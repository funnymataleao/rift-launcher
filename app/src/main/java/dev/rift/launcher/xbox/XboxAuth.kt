package dev.rift.launcher.xbox

import android.content.Context
import androidx.core.content.edit
import app.gamenative.utils.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * The Microsoft account behind Xbox Remote Play, signed in with a code typed on another device (the
 * Microsoft device-code sign-in, so no password is ever entered in Rift and the gamepad is enough).
 *
 * From the account's refresh token it makes, on demand, the tokens the rest needs:
 *  - an Xbox Live token for the console list, installed games and commands ([webToken]);
 *  - the home streaming token of Microsoft's streaming service ([homeStreaming]);
 *  - the transfer token the console asks for when a stream connects ([transferToken]).
 *
 * The flow and endpoints are the ones open Xbox clients use (XStreaming, Greenlight); Microsoft does not
 * document them. Only the refresh token and the gamertag are saved, in "rift_xbox".
 */
object XboxAuth {
    private const val CLIENT_ID = "1f907974-e22b-4810-a9de-d9647380c97e"
    private const val SCOPE = "xboxlive.signin openid profile offline_access"
    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val LIVE_TOKEN_URL = "https://login.live.com/oauth20_token.srf"
    private const val TRANSFER_SCOPE = "service::http://Passport.NET/purpose::PURPOSE_XBOX_CLOUD_CONSOLE_TRANSFER_TOKEN"
    private const val USER_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val HOME_LOGIN_URL = "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user"

    private const val RP_XBOX_LIVE = "http://xboxlive.com"
    private const val RP_GSSV = "http://gssv.xboxlive.com/"

    private const val FILE = "rift_xbox"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_GAMERTAG = "gamertag"

    /** Tokens are renewed this long before they run out. */
    private const val MARGIN_MS = 60_000L

    private val json = "application/json".toMediaType()
    private val lock = Mutex()
    private val signedInFlow = MutableStateFlow<Boolean?>(null)

    private var accessToken: Expiring<String>? = null
    private var userToken: Expiring<String>? = null
    private var web: Expiring<Xsts>? = null
    private var gssv: Expiring<Xsts>? = null
    private var home: Expiring<HomeStreaming>? = null

    /** A Microsoft device sign-in in progress: the person types [userCode] at [verificationUri]. */
    class DeviceCode(
        val userCode: String,
        val verificationUri: String,
        internal val deviceCode: String,
        internal val intervalMs: Long,
        internal val expiresAt: Long,
    )

    /** An Xbox Live authorization: the header value Xbox Live services take, and who is signed in. */
    class Xsts(val token: String, val userHash: String, val xuid: String, val gamertag: String) {
        val header: String get() = "XBL3.0 x=$userHash;$token"
    }

    /** The home streaming service: its token and the region address sessions start at. */
    class HomeStreaming(val token: String, val baseUri: String)

    private class Expiring<T>(val value: T, val expiresAt: Long) {
        val fresh: Boolean get() = System.currentTimeMillis() < expiresAt - MARGIN_MS
    }

    class SignInException(message: String) : Exception(message)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Signed in or not; Rift's Xbox settings and the consoles in the home streaming tab follow it. */
    fun signedIn(context: Context): StateFlow<Boolean?> {
        if (signedInFlow.value == null) signedInFlow.value = prefs(context).getString(KEY_REFRESH, null) != null
        return signedInFlow.asStateFlow()
    }

    fun isSignedIn(context: Context): Boolean = signedIn(context).value == true

    fun gamertag(context: Context): String? = prefs(context).getString(KEY_GAMERTAG, null)

    /** Starts a sign-in: Microsoft gives a short code to type on a phone or a computer. */
    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("client_id", CLIENT_ID).add("scope", SCOPE).build()
        val response = postForm(DEVICE_CODE_URL, body)
        DeviceCode(
            userCode = response.getString("user_code"),
            verificationUri = response.optString("verification_uri").ifBlank { "https://www.microsoft.com/link" },
            deviceCode = response.getString("device_code"),
            intervalMs = response.optLong("interval", 5).coerceAtLeast(1) * 1000,
            expiresAt = System.currentTimeMillis() + response.optLong("expires_in", 900) * 1000,
        )
    }

    /**
     * Waits until the person has typed [code] and agreed, then keeps the account. Throws when the code
     * runs out or the person declines; cancelling the coroutine stops waiting.
     */
    suspend fun awaitSignIn(context: Context, code: DeviceCode) = withContext(Dispatchers.IO) {
        var interval = code.intervalMs
        while (System.currentTimeMillis() < code.expiresAt) {
            delay(interval)
            val body = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", CLIENT_ID)
                .add("device_code", code.deviceCode)
                .build()
            val (status, response) = post(Request.Builder().url(TOKEN_URL).post(body).build())
            if (status in 200..299) {
                lock.withLock {
                    clearSession()
                    keepUserTokens(context, response)
                    // The gamertag shows in settings; a failure here still leaves the account signed in.
                    runCatching { gamertagNow(context) }
                }
                signedInFlow.value = true
                return@withContext
            }
            when (response.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5_000
                "authorization_declined" -> throw SignInException("declined")
                "expired_token", "bad_verification_code" -> throw SignInException("expired")
                else -> throw SignInException(response.optString("error_description").ifBlank { "HTTP $status" })
            }
        }
        throw SignInException("expired")
    }

    /** Forgets the account and every token made from it. */
    fun signOut(context: Context) {
        prefs(context).edit { clear() }
        clearSession()
        signedInFlow.value = false
    }

    /** Xbox Live authorization for the console list, installed games, art and console commands. */
    suspend fun webToken(context: Context): Xsts = lock.withLock {
        web?.takeIf { it.fresh }?.value ?: xsts(context, RP_XBOX_LIVE).also { web = it }.value
    }

    /** The home streaming token and its region, for starting a stream from a console. */
    suspend fun homeStreaming(context: Context): HomeStreaming = lock.withLock {
        home?.takeIf { it.fresh }?.value?.let { return@withLock it }
        val authorization = (gssv?.takeIf { it.fresh } ?: xsts(context, RP_GSSV).also { gssv = it }).value
        val payload = JSONObject().put("token", authorization.token).put("offeringId", "xhome")
        val response = withContext(Dispatchers.IO) {
            postJson(
                HOME_LOGIN_URL,
                payload,
                mapOf("x-gssv-client" to "XboxComBrowser", "Cache-Control" to "no-store, must-revalidate, no-cache"),
            )
        }
        val regions = response.optJSONObject("offeringSettings")?.optJSONArray("regions") ?: JSONArray()
        val region = (0 until regions.length()).mapNotNull { regions.optJSONObject(it) }
            .let { list -> list.firstOrNull { it.optBoolean("isDefault") } ?: list.firstOrNull() }
            ?: throw SignInException("no streaming region")
        val streaming = HomeStreaming(response.getString("gsToken"), region.getString("baseUri").trimEnd('/'))
        home = Expiring(streaming, System.currentTimeMillis() + response.optLong("durationInSeconds", 3600) * 1000)
        streaming
    }

    /** The token the console asks for while a stream connects ("ReadyToConnect"). */
    suspend fun transferToken(context: Context): String = lock.withLock {
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", TRANSFER_SCOPE)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken(context))
                .build()
            postForm(LIVE_TOKEN_URL, body).getString("access_token")
        }
    }

    private fun clearSession() {
        accessToken = null
        userToken = null
        web = null
        gssv = null
        home = null
    }

    private fun refreshToken(context: Context): String =
        prefs(context).getString(KEY_REFRESH, null) ?: throw SignInException("not signed in")

    private fun keepUserTokens(context: Context, response: JSONObject) {
        val access = response.getString("access_token")
        accessToken = Expiring(access, System.currentTimeMillis() + response.optLong("expires_in", 3600) * 1000)
        response.optString("refresh_token").takeIf(String::isNotBlank)?.let { refresh ->
            prefs(context).edit { putString(KEY_REFRESH, refresh) }
        }
    }

    /** The Microsoft account's access token, renewed with the saved refresh token. Called under [lock]. */
    private suspend fun access(context: Context): String {
        accessToken?.takeIf { it.fresh }?.let { return it.value }
        return withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken(context))
                .add("scope", SCOPE)
                .build()
            val (status, response) = post(Request.Builder().url(TOKEN_URL).post(body).build())
            if (status == 400 || status == 401) {
                // The refresh token is no longer accepted (password changed, access removed): sign out.
                signOut(context)
                throw SignInException("signed out")
            }
            if (status !in 200..299) throw SignInException("HTTP $status")
            keepUserTokens(context, response)
            response.getString("access_token")
        }
    }

    /** The Xbox Live user token, made from the Microsoft access token. Called under [lock]. */
    private suspend fun user(context: Context): String {
        userToken?.takeIf { it.fresh }?.let { return it.value }
        val access = access(context)
        val payload = JSONObject()
            .put(
                "Properties",
                JSONObject().put("AuthMethod", "RPS").put("RpsTicket", "d=$access").put("SiteName", "user.auth.xboxlive.com"),
            )
            .put("RelyingParty", "http://auth.xboxlive.com")
            .put("TokenType", "JWT")
        val response = withContext(Dispatchers.IO) { postJson(USER_AUTH_URL, payload, xboxHeaders()) }
        val token = response.getString("Token")
        userToken = Expiring(token, expiry(response))
        return token
    }

    /** An Xbox Live authorization for one service ([relyingParty]). Called under [lock]. */
    private suspend fun xsts(context: Context, relyingParty: String): Expiring<Xsts> {
        val user = user(context)
        val payload = JSONObject()
            .put("Properties", JSONObject().put("SandboxId", "RETAIL").put("UserTokens", JSONArray().put(user)))
            .put("RelyingParty", relyingParty)
            .put("TokenType", "JWT")
        val response = withContext(Dispatchers.IO) { postJson(XSTS_URL, payload, xboxHeaders()) }
        val claims = response.optJSONObject("DisplayClaims")?.optJSONArray("xui")?.optJSONObject(0) ?: JSONObject()
        val xsts = Xsts(
            token = response.getString("Token"),
            userHash = claims.optString("uhs"),
            xuid = claims.optString("xid"),
            gamertag = claims.optString("gtg"),
        )
        if (xsts.gamertag.isNotBlank()) prefs(context).edit { putString(KEY_GAMERTAG, xsts.gamertag) }
        return Expiring(xsts, expiry(response))
    }

    private suspend fun gamertagNow(context: Context) {
        web = xsts(context, RP_XBOX_LIVE)
    }

    private fun expiry(response: JSONObject): Long =
        runCatching { Instant.parse(response.getString("NotAfter")).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis() + 3_600_000L)

    private fun xboxHeaders() = mapOf(
        "x-xbl-contract-version" to "1",
        "Cache-Control" to "no-cache",
        "Origin" to "https://www.xbox.com",
        "Referer" to "https://www.xbox.com/",
        "ms-cv" to "0",
    )

    private fun postForm(url: String, body: FormBody): JSONObject {
        val (status, response) = post(Request.Builder().url(url).post(body).build())
        if (status !in 200..299) throw SignInException(response.optString("error_description").ifBlank { "HTTP $status" })
        return response
    }

    private fun postJson(url: String, payload: JSONObject, headers: Map<String, String>): JSONObject {
        val request = Request.Builder().url(url).post(payload.toString().toRequestBody(json))
        headers.forEach { (name, value) -> request.header(name, value) }
        request.header("Accept", "application/json")
        val (status, response) = post(request.build())
        if (status !in 200..299) throw SignInException("HTTP $status from $url")
        return response
    }

    private fun post(request: Request): Pair<Int, JSONObject> =
        Net.http.newCall(request).execute().use { response ->
            val text = response.body.string()
            response.code to (runCatching { JSONObject(text) }.getOrDefault(JSONObject()))
        }
}

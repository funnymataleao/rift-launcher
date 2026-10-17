package dev.rift.launcher.xbox

import android.content.Context
import app.gamenative.utils.Net
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** An Xbox the signed-in account has, as the Xbox app lists it. */
data class XboxConsole(
    val id: String,
    val name: String,
    /** "XboxSeriesX", "XboxSeriesS", "XboxOne"... */
    val type: String,
    /** "On", "ConnectedStandby" (asleep, can be woken), "Off"... */
    val powerState: String,
) {
    val isOn: Boolean get() = powerState.equals("On", ignoreCase = true)
}

/** A game installed on a console, with what it takes to start it there. */
data class XboxInstalledGame(
    val consoleId: String,
    val productId: String,
    val titleId: Long,
    val name: String,
    /** When it was last played on the console (ms), 0 when unknown. */
    val lastActive: Long,
)

/**
 * The account's consoles and what is on them, through the same Xbox Live service the Xbox app uses to
 * manage a console remotely ("xccs"): the console list, the installed games, waking a console and starting
 * a game on it. A console only answers when its "Remote features" are on (Settings > Devices & connections).
 */
object XboxConsoles {
    private const val BASE = "https://xccs.xboxlive.com"
    private val json = "application/json".toMediaType()

    suspend fun list(context: Context): List<XboxConsole> {
        val response = get(context, "/lists/devices?queryCurrentDevice=false&includeStorageDevices=true", contract = "2")
        val result = response.optJSONArray("result") ?: JSONArray()
        return (0 until result.length()).mapNotNull { i ->
            val item = result.optJSONObject(i) ?: return@mapNotNull null
            XboxConsole(
                id = item.optString("id").ifBlank { return@mapNotNull null },
                name = item.optString("name"),
                type = item.optString("consoleType"),
                powerState = item.optString("powerState"),
            )
        }
    }

    /** The games installed on [consoleId] (apps left out), the last played first. */
    suspend fun installedGames(context: Context, consoleId: String): List<XboxInstalledGame> {
        val response = get(context, "/lists/installedApps?deviceId=$consoleId", contract = "2")
        val result = response.optJSONArray("result") ?: JSONArray()
        return (0 until result.length()).mapNotNull { i ->
            val item = result.optJSONObject(i) ?: return@mapNotNull null
            if (!item.optBoolean("isGame")) return@mapNotNull null
            XboxInstalledGame(
                consoleId = consoleId,
                productId = item.optString("oneStoreProductId").ifBlank { return@mapNotNull null },
                titleId = item.optLong("titleId"),
                name = item.optString("name"),
                lastActive = item.optString("lastActiveTime").takeIf(String::isNotBlank)
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            )
        }.distinctBy { it.productId }.sortedByDescending { it.lastActive }
    }

    /** Wakes a console that sleeps in "Sleep" power mode. */
    suspend fun wake(context: Context, consoleId: String) {
        command(context, consoleId, "Power", "WakeUp", JSONArray())
    }

    /** Starts a game on the console; it comes to the front, so the stream opens on it. */
    suspend fun launch(context: Context, consoleId: String, productId: String) {
        command(context, consoleId, "Shell", "ActivateApplicationWithOneStoreProductId", JSONArray().put(JSONObject().put("oneStoreProductId", productId)))
    }

    private suspend fun command(context: Context, consoleId: String, type: String, command: String, parameters: JSONArray) {
        val body = JSONObject()
            .put("destination", "Xbox")
            .put("type", type)
            .put("command", command)
            .put("sessionId", UUID.randomUUID().toString())
            .put("sourceId", "com.microsoft.smartglass")
            .put("parameters", parameters)
            .put("linkedXboxId", consoleId)
        request(context, "/commands", body, contract = "4")
    }

    private suspend fun get(context: Context, path: String, contract: String): JSONObject = request(context, path, null, contract)

    private suspend fun request(context: Context, path: String, body: JSONObject?, contract: String): JSONObject {
        val auth = XboxAuth.webToken(context)
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(BASE + path)
                .header("Authorization", auth.header)
                .header("Accept-Language", "en-US")
                .header("skillplatform", "RemoteManagement")
                .header("x-xbl-contract-version", contract)
                .header("x-xbl-client-name", "XboxApp")
                .header("x-xbl-client-type", "UWA")
                .header("x-xbl-client-version", "39.39.22001.0")
                .apply { if (body != null) post(body.toString().toRequestBody(json)) }
                .build()
            Net.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} for $path")
                runCatching { JSONObject(response.body.string()) }.getOrDefault(JSONObject())
            }
        }
    }
}

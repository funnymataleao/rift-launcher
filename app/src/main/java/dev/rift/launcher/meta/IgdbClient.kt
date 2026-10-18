package dev.rift.launcher.meta

import dev.rift.launcher.art.SteamGridDbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Why a request to IGDB gave nothing. */
sealed class MetaError : Exception() {
    /** No Client ID / Client Secret saved in the settings. */
    object NoKey : MetaError()

    /** Twitch or IGDB refused the Client ID / Secret. */
    object BadKey : MetaError()
    object NotFound : MetaError()
    object Network : MetaError()
}

/**
 * A small client for IGDB (https://api-docs.igdb.com), which is free for non-commercial use with a Twitch
 * application's Client ID and Secret that the user saves in Rift's settings. A game is found by its name
 * (cleaned of dump tags like "(USA)") and, when the system is known, its platform.
 */
object IgdbClient {
    private const val TAG = "RiftMeta"
    private const val GAMES = "https://api.igdb.com/v4/games"
    private const val TOKEN = "https://id.twitch.tv/oauth2/token"
    private const val FIELDS = "id,name,summary,storyline,total_rating,total_rating_count,first_release_date," +
        "genres.name,themes.name,game_modes.name,player_perspectives.name,platforms.name," +
        "involved_companies.developer,involved_companies.company.name"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class Token(val clientId: String, val value: String, val expiresAt: Long)

    @Volatile
    private var token: Token? = null

    /** The best match for [name] on [platforms] (IGDB platform ids; empty = any platform). */
    suspend fun find(clientId: String, secret: String, name: String, platforms: List<Int>): GameMeta {
        if (clientId.isBlank() || secret.isBlank()) throw MetaError.NoKey
        val term = SteamGridDbClient.cleanTitle(name)
        if (term.isEmpty()) throw MetaError.NotFound
        val (meta, gameId) = pick(query(clientId, secret, term, platforms), term)
        val (main, extras, complete) = beatTimes(clientId, secret, gameId)
        return meta.copy(beatMain = main, beatExtras = extras, beatComplete = complete)
    }

    /** IGDB's time to beat for a game id; zeros when IGDB has none or the request fails (the rest is still shown). */
    private suspend fun beatTimes(clientId: String, secret: String, gameId: Long): Triple<Long, Long, Long> {
        return try {
            val arr = JSONArray(post(clientId, secret, "fields hastily,normally,completely,count; where game_id = $gameId; limit 1;", refreshed = false))
            val o = arr.optJSONObject(0) ?: return Triple(0, 0, 0)
            Triple(o.optLong("hastily"), o.optLong("normally"), o.optLong("completely"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "IGDB time to beat unavailable for %d", gameId)
            Triple(0, 0, 0)
        }
    }

    private suspend fun query(clientId: String, secret: String, term: String, platforms: List<Int>): JSONArray {
        if (platforms.isNotEmpty()) {
            val onPlatform = request(clientId, secret, term, platforms)
            if (onPlatform.length() > 0) return onPlatform
        }
        return request(clientId, secret, term, emptyList())
    }

    private suspend fun request(clientId: String, secret: String, term: String, platforms: List<Int>): JSONArray {
        val where = if (platforms.isEmpty()) "" else "where platforms = (${platforms.joinToString(",")}); "
        val body = "search \"${term.replace("\\", " ").replace("\"", " ")}\"; fields $FIELDS; ${where}limit 10;"
        val text = post(clientId, secret, body, refreshed = false)
        return try {
            JSONArray(text)
        } catch (e: JSONException) {
            Timber.tag(TAG).w(e, "IGDB answered with something unreadable")
            throw MetaError.Network
        }
    }

    private suspend fun post(clientId: String, secret: String, body: String, refreshed: Boolean): String = withContext(Dispatchers.IO) {
        val bearer = bearer(clientId, secret)
        val request = Request.Builder()
            .url(GAMES)
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $bearer")
            .post(body.toRequestBody("text/plain".toMediaType()))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                Timber.tag(TAG).i("IGDB HTTP %d (%d bytes) for %s", response.code, text.length, body.take(80))
                when {
                    response.code == 401 && !refreshed -> {
                        token = null
                        return@withContext post(clientId, secret, body, refreshed = true)
                    }
                    response.code == 401 || response.code == 403 -> throw MetaError.BadKey
                    !response.isSuccessful -> throw MetaError.Network
                }
                text
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "IGDB request failed")
            throw MetaError.Network
        }
    }

    private fun bearer(clientId: String, secret: String): String {
        token?.let { if (it.clientId == clientId && System.currentTimeMillis() < it.expiresAt) return it.value }
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", secret)
            .add("grant_type", "client_credentials")
            .build()
        try {
            http.newCall(Request.Builder().url(TOKEN).post(form).build()).execute().use { response ->
                val text = response.body.string()
                Timber.tag(TAG).i("Twitch token HTTP %d", response.code)
                if (response.code == 400 || response.code == 401 || response.code == 403) throw MetaError.BadKey
                if (!response.isSuccessful) throw MetaError.Network
                val o = JSONObject(text)
                val value = o.getString("access_token")
                // Renewed a minute early.
                val fresh = Token(clientId, value, System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 60) * 1000)
                token = fresh
                return value
            }
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Twitch token request failed")
            throw MetaError.Network
        } catch (e: JSONException) {
            throw MetaError.Network
        }
    }

    /** Exact name wins, otherwise IGDB's own first (best) hit. */
    private fun pick(results: JSONArray, term: String): Pair<GameMeta, Long> {
        if (results.length() == 0) throw MetaError.NotFound
        val wanted = normalize(term)
        val list = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        val game = list.firstOrNull { normalize(it.optString("name")) == wanted } ?: list.first()
        return Pair(parse(game), game.optLong("id"))
    }

    private fun parse(game: JSONObject): GameMeta {
        val companies = game.optJSONArray("involved_companies")
        val developer = companies?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .filter { it.optBoolean("developer") }
                .mapNotNull { it.optJSONObject("company")?.optString("name")?.ifEmpty { null } }
                .distinct()
                .joinToString(", ")
        }.orEmpty()
        val summary = game.optString("summary").trim()
        val story = game.optString("storyline").trim()
        return GameMeta(
            description = if (summary.isEmpty()) story else summary,
            rating = if (game.has("total_rating")) game.optDouble("total_rating").toInt() else null,
            ratingCount = if (game.has("total_rating_count")) game.optInt("total_rating_count") else null,
            genres = names(game.optJSONArray("genres")),
            tags = (names(game.optJSONArray("themes")) + names(game.optJSONArray("game_modes")) + names(game.optJSONArray("player_perspectives"))).distinct(),
            developer = developer,
            releaseDate = game.optLong("first_release_date"),
        )
    }

    /** IGDB's hits for [term] to choose from: on [platforms] first, then on any platform. */
    suspend fun candidates(clientId: String, secret: String, term: String, platforms: List<Int>): List<MetaCandidate> {
        if (clientId.isBlank() || secret.isBlank()) throw MetaError.NoKey
        val clean = SteamGridDbClient.cleanTitle(term)
        if (clean.isEmpty()) throw MetaError.NotFound
        val games = buildList {
            if (platforms.isNotEmpty()) addAll(request(clientId, secret, clean, platforms).objects())
            addAll(request(clientId, secret, clean, emptyList()).objects())
        }
        return games.distinctBy { it.optLong("id") }.take(8).map { game ->
            val onPlatform = names(game.optJSONArray("platforms"))
            val year = game.optLong("first_release_date").takeIf { it > 0 }
                ?.let { java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = it * 1000 }.get(java.util.Calendar.YEAR) }
            MetaCandidate(
                source = "igdb",
                ref = game.optLong("id").toString(),
                title = game.optString("name"),
                platform = onPlatform.take(3).joinToString(", "),
                year = year,
                meta = parse(game),
            )
        }
    }

    /** A chosen hit with IGDB's time to beat. */
    suspend fun complete(clientId: String, secret: String, candidate: MetaCandidate): GameMeta {
        val (main, extras, complete) = beatTimes(clientId, secret, candidate.ref.toLongOrNull() ?: return candidate.meta)
        return candidate.meta.copy(beatMain = main, beatExtras = extras, beatComplete = complete)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private fun names(array: JSONArray?): List<String> = if (array == null) emptyList() else
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name")?.ifEmpty { null } }

    private fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** IGDB platform ids for Rift's emulator platform ids; unknown systems are searched on every platform. */
    fun platformsFor(platformId: String?): List<Int> = when (platformId) {
        null -> emptyList()
        "switch" -> listOf(130)
        "ps1" -> listOf(7)
        "ps2" -> listOf(8)
        "ps3" -> listOf(9)
        "ps4" -> listOf(48)
        "psp" -> listOf(38)
        "vita" -> listOf(46)
        "3ds" -> listOf(37)
        "nds" -> listOf(20)
        "gc_wii" -> listOf(21, 5)
        "dreamcast" -> listOf(23)
        "gba" -> listOf(24)
        "snes" -> listOf(19)
        "nes" -> listOf(18)
        "genesis" -> listOf(29)
        "mastersystem" -> listOf(64)
        "gamegear" -> listOf(35)
        "saturn" -> listOf(32)
        "n64" -> listOf(4)
        "gb" -> listOf(33)
        "gbc" -> listOf(22)
        "wiiu" -> listOf(41)
        "xbox360" -> listOf(12)
        "virtualboy" -> listOf(87)
        "segacd" -> listOf(78)
        "sega32x" -> listOf(30)
        "atari2600" -> listOf(59)
        "atari7800" -> listOf(60)
        "lynx" -> listOf(61)
        "jaguar" -> listOf(62)
        "pcengine" -> listOf(86, 150)
        "neogeo" -> listOf(80)
        "ngp" -> listOf(119, 120)
        "wonderswan" -> listOf(57, 123)
        "arcade" -> listOf(52)
        "3do" -> listOf(50)
        "msx" -> listOf(27)
        "c64" -> listOf(15)
        "amiga" -> listOf(16)
        "dos" -> listOf(13)
        "colecovision" -> listOf(68)
        "intellivision" -> listOf(67)
        else -> emptyList()
    }

    const val ANDROID_PLATFORM = 34
}

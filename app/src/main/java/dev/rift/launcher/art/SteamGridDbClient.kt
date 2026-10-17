package dev.rift.launcher.art

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One picture offered by SteamGridDB: [thumb] for the grid, [url] is the full image that gets saved. */
data class ArtOption(val id: Long, val thumb: String, val url: String, val width: Int, val height: Int)

/** Why a request to SteamGridDB gave nothing. */
sealed class ArtError : Exception() {
    /** No key saved in the settings. */
    object NoKey : ArtError()

    /** SteamGridDB refused the key (401/403). */
    object BadKey : ArtError()

    /** The game was not found on SteamGridDB. */
    object NotFound : ArtError()
    object Network : ArtError()
}

/**
 * A small client for SteamGridDB's v2 API (https://www.steamgriddb.com/api/v2), used with the key
 * the user saved in Rift's settings. Steam games are asked for by their Steam id, every other game is found by
 * its name first. Nothing is cached here; the pictures themselves are cached by Coil.
 */
object SteamGridDbClient {
    private const val BASE = "https://www.steamgriddb.com/api/v2"
    const val PAGE_SIZE = 50

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** What identifies a game on SteamGridDB: a Steam app id, or a name to search for. */
    data class Game(val steamAppId: Int?, val name: String)

    /** Resolves [game] to the path part SteamGridDB uses: `steam/<id>` or `game/<id>`. */
    suspend fun resolve(key: String, game: Game, byName: Boolean = false): String {
        if (!byName && game.steamAppId != null && game.steamAppId > 0) return "steam/${game.steamAppId}"
        val term = game.name.trim()
        if (term.isEmpty()) throw ArtError.NotFound
        val url = "$BASE/search/autocomplete".toHttpUrl().newBuilder().addPathSegment(term).build()
        val data = getJson(key, url.toString()).optJSONArray("data")
        val first = data?.optJSONObject(0) ?: throw ArtError.NotFound
        return "game/${first.getLong("id")}"
    }

    /** One page (0-based) of [kind] pictures for the game that [resolve] returned [path] for. */
    suspend fun options(key: String, path: String, kind: ArtKind, page: Int): List<ArtOption> {
        val url = "$BASE/${kind.endpoint}/$path".toHttpUrl().newBuilder()
            .addQueryParameter("mimes", kind.mimes)
            .addQueryParameter("nsfw", "false")
            .addQueryParameter("humor", "any")
            .addQueryParameter("types", "static")
            .addQueryParameter("page", page.toString())
            .apply { kind.dimensions?.let { addQueryParameter("dimensions", it) } }
            .build()
        val data = getJson(key, url.toString()).optJSONArray("data") ?: return emptyList()
        return List(data.length()) { i ->
            val o = data.getJSONObject(i)
            val full = o.getString("url")
            ArtOption(
                id = o.getLong("id"),
                thumb = o.optString("thumb").ifEmpty { full },
                url = full,
                width = o.optInt("width"),
                height = o.optInt("height"),
            )
        }
    }

    private suspend fun getJson(key: String, url: String): JSONObject = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw ArtError.NoKey
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${key.trim()}").build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw ArtError.BadKey
                    response.code == 404 -> throw ArtError.NotFound
                    !response.isSuccessful -> throw ArtError.Network
                }
                JSONObject(response.body.string())
            }
        } catch (e: IOException) {
            throw ArtError.Network
        }
    }
}

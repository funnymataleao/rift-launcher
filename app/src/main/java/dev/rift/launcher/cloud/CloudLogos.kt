package dev.rift.launcher.cloud

import android.content.Context
import app.gamenative.data.parseSteamSearchAppId
import app.gamenative.utils.Net
import dev.rift.launcher.art.ArtError
import dev.rift.launcher.art.ArtKind
import dev.rift.launcher.art.SteamGridDbClient
import dev.rift.launcher.settings.RiftPrefs
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Transparent title logos for cloud games, shown over the home's art like the PC games' logos. The
 * Microsoft Store has no such logo (its "Logo" is a square tile), so the game is looked up on Steam by its
 * English name and Steam's library logo is used; a game Steam does not have is looked up on SteamGridDB
 * when a key is saved in the settings. Found logos and misses are kept in a file; a miss is tried again
 * after a week, a network failure on the next request.
 */
object CloudLogos {
    private const val STEAM_ASSETS = "https://shared.steamstatic.com/store_item_assets/steam/apps"
    private const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    private const val PARALLEL = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(PARALLEL)
    private val logosFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    private val checked = mutableMapOf<String, Long>()
    private val inFlight = mutableSetOf<String>()
    private var loaded = false

    /** "Edition" words and platform tags the store adds to a name that Steam does not use. */
    private val storeSuffixes = Regex(
        """\s*[(\[]?\b(game preview|early access|for windows( 10| 11)?|windows( 10| 11)?( edition)?|xbox series x\s*\|\s*s|xbox series x|xbox one|xbox edition|standard edition|cloud version)\b[)\]]?""",
        RegexOption.IGNORE_CASE,
    )

    private fun file(context: Context) = File(context.applicationContext.filesDir, "cloud/logos.json")

    /** Product id to logo URL, for the games looked up so far that have one. */
    fun logos(context: Context): StateFlow<Map<String, String>> {
        load(context)
        return logosFlow.asStateFlow()
    }

    /** Looks up the logos of [games] that are not known yet, a couple at a time. */
    fun prefetch(context: Context, games: List<CloudGame>) {
        load(context)
        val now = System.currentTimeMillis()
        val wanted = synchronized(this) {
            games.filter { game ->
                val id = game.productId
                id !in logosFlow.value && id !in inFlight && now - (checked[id] ?: 0L) > MISS_RETRY_MS
            }.onEach { inFlight += it.productId }
        }
        val appContext = context.applicationContext
        wanted.forEach { game ->
            scope.launch {
                val url = gate.withPermit { runCatching { find(appContext, game) }.getOrNull() }
                synchronized(this@CloudLogos) {
                    inFlight -= game.productId
                    // null: the network failed, so nothing is remembered and the next request tries again.
                    if (url != null) {
                        checked[game.productId] = System.currentTimeMillis()
                        if (url.isNotEmpty()) logosFlow.update { it + (game.productId to url) }
                        save(appContext)
                    }
                }
            }
        }
    }

    /** The logo URL, "" when neither Steam nor SteamGridDB has one, null when a request failed. */
    private suspend fun find(context: Context, game: CloudGame): String? {
        val names = listOf(game.englishTitle, game.title, storeSuffixes.replace(game.englishTitle, "").trim(' ', '-', '–', ':'))
            .filter(String::isNotBlank).distinct()
        var failed = false
        var steamAppId: Int? = null
        for (name in names) {
            val json = get("https://store.steampowered.com/api/storesearch/?term=${URLEncoder.encode(name, "UTF-8")}&cc=us&l=english")
            if (json == null) {
                failed = true
                continue
            }
            steamAppId = parseSteamSearchAppId(name, json)
            if (steamAppId != null) break
        }
        steamAppId?.let { id ->
            val url = "$STEAM_ASSETS/$id/logo.png"
            when (exists(url)) {
                true -> return url
                null -> failed = true
                false -> Unit
            }
        }
        val key = RiftPrefs.of(context).steamGridDbKey
        if (key.isNotBlank()) {
            try {
                val path = if (steamAppId != null) {
                    "steam/$steamAppId"
                } else {
                    SteamGridDbClient.resolve(key, SteamGridDbClient.Game(null, names.first()))
                }
                SteamGridDbClient.options(key, path, ArtKind.Logo, 0).firstOrNull()?.url?.let { return it }
            } catch (e: ArtError) {
                if (e === ArtError.Network) failed = true
            }
        }
        return if (failed) null else ""
    }

    private fun get(url: String): String? = try {
        Net.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }
    } catch (e: IOException) {
        Timber.w(e, "Cloud logo lookup failed: %s", url)
        null
    }

    /** Whether [url] is an image Steam has; null when the request failed. */
    private fun exists(url: String): Boolean? = try {
        Net.http.newCall(Request.Builder().url(url).head().build()).execute().use { it.isSuccessful }
    } catch (e: IOException) {
        null
    }

    private fun load(context: Context) = synchronized(this) {
        if (loaded) return@synchronized
        loaded = true
        runCatching {
            val f = file(context).takeIf { it.exists() } ?: return@runCatching
            val json = JSONObject(f.readText())
            val found = mutableMapOf<String, String>()
            json.keys().forEach { id ->
                val entry = json.getJSONObject(id)
                checked[id] = entry.optLong("at")
                entry.optString("url").takeIf(String::isNotEmpty)?.let { found[id] = it }
            }
            logosFlow.value = found
        }.onFailure { Timber.w(it, "Cloud logos file unreadable") }
    }

    private fun save(context: Context) {
        val json = JSONObject()
        checked.forEach { (id, at) -> json.put(id, JSONObject().put("at", at).put("url", logosFlow.value[id].orEmpty())) }
        runCatching {
            val f = file(context)
            f.parentFile?.mkdirs()
            f.writeText(json.toString())
        }.onFailure { Timber.w(it, "Cloud logos file not saved") }
    }
}

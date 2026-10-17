package dev.rift.launcher.cloud

import android.content.Context
import android.graphics.BitmapFactory
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
 * when a key is saved in the settings. Every picture is checked before it is used: a logo has see-through
 * parts, so an opaque picture (a grey square, a store tile) is passed over for the
 * next one. Found logos and misses are kept in a file; a miss is tried again after a week, a network
 * failure on the next request.
 */
object CloudLogos {
    private const val STEAM_ASSETS = "https://shared.steamstatic.com/store_item_assets/steam/apps"
    private const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000
    private const val PARALLEL = 2

    /** SteamGridDB pictures tried per game before giving up on it. */
    private const val SGDB_TRIES = 3

    /** The picture is checked at about this size; a logo is a few hundred pixels wide. */
    private const val CHECK_PX = 128

    /** Share of see-through pixels a picture needs to count as a logo rather than a tile or a placeholder. */
    private const val MIN_TRANSPARENT = 0.05f
    private const val MAX_BYTES = 8L * 1024 * 1024

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

    private fun file(context: Context) = File(context.applicationContext.filesDir, "cloud/logos-v2.json")

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
            when (isLogo("$STEAM_ASSETS/$id/logo.png")) {
                true -> return "$STEAM_ASSETS/$id/logo.png"
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
                for (option in SteamGridDbClient.options(key, path, ArtKind.Logo, 0).take(SGDB_TRIES)) {
                    when (isLogo(option.url)) {
                        true -> return option.url
                        null -> failed = true
                        false -> Unit
                    }
                }
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

    /**
     * Whether [url] is a usable logo: an image that loads and has see-through parts. False for a missing
     * file, an opaque picture or something that is not an image; null when the request failed.
     */
    private fun isLogo(url: String): Boolean? {
        val bytes = try {
            Net.http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body
                if (body.contentLength() > MAX_BYTES) return false
                body.bytes()
            }
        } catch (e: IOException) {
            Timber.w(e, "Cloud logo download failed: %s", url)
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CHECK_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        try {
            if (!bitmap.hasAlpha()) return false
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val clear = pixels.count { (it ushr 24) < 0x20 }
            return clear >= pixels.size * MIN_TRANSPARENT
        } finally {
            bitmap.recycle()
        }
    }

    private fun load(context: Context) = synchronized(this) {
        if (loaded) return@synchronized
        loaded = true
        // The first version's file kept pictures it never checked; they are looked up again.
        File(context.applicationContext.filesDir, "cloud/logos.json").delete()
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

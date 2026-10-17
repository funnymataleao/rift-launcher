package dev.rift.launcher.art

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The pictures the user picked for their games, one per game and [ArtKind]. A picked picture is saved
 * on the device (files/game_art) so it stays even when the network or SteamGridDB does not; the store holds
 * its file URL. [version] changes with every pick, so screens that show art read it and redraw.
 */
object GameArtStore {
    private const val PREFS = "rift_game_art"
    private const val DIR = "game_art"

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(appId: String, kind: ArtKind) = "$appId|${kind.key}"

    /** Whether [url] is a picture the user picked: saved on the device, or (if the download failed) SteamGridDB's own address. */
    fun isPicked(url: String): Boolean = url.startsWith("file:") || url.contains("steamgriddb.com")

    /** The picked picture's URL, or empty when the game still uses its own. */
    fun get(context: Context, appId: String, kind: ArtKind): String =
        prefs(context).getString(key(appId, kind), "").orEmpty()

    /** [item] with the pictures the user picked in place of the store's. */
    fun apply(context: Context, item: LibraryItem): LibraryItem {
        val p = prefs(context)
        fun picked(kind: ArtKind) = p.getString(key(item.appId, kind), "").orEmpty()
        val capsule = picked(ArtKind.Capsule)
        val wide = picked(ArtKind.Wide)
        val hero = picked(ArtKind.Hero)
        val logo = picked(ArtKind.Logo)
        val icon = picked(ArtKind.Icon)
        if (capsule.isEmpty() && wide.isEmpty() && hero.isEmpty() && logo.isEmpty() && icon.isEmpty()) return item
        return item.copy(
            capsuleImageUrl = capsule.ifEmpty { item.capsuleImageUrl },
            headerImageUrl = wide.ifEmpty { item.headerImageUrl },
            heroImageUrl = hero.ifEmpty { item.heroImageUrl },
            customLogoUrl = logo,
            customIconUrl = icon,
        )
    }

    /** How many pictures are picked in all; -1 until first asked. Lets [apply] skip the work for the common case of none. */
    @Volatile
    private var pickCount = -1

    private fun pickedCount(context: Context): Int {
        if (pickCount < 0) pickCount = prefs(context).all.size
        return pickCount
    }

    /** Applies [apply] to a whole list, doing nothing at all while no game has a picked picture. */
    fun apply(context: Context, items: List<LibraryItem>): List<LibraryItem> {
        if (pickedCount(context) == 0) return items
        return items.map { apply(context, it) }
    }

    /**
     * Saves [remoteUrl] on the device and makes it the game's picture of [kind]. The image is downloaded
     * first and only then put in place, so the screens switch once, straight to the finished picture.
     * If the download fails the web address is used instead.
     */
    suspend fun pick(context: Context, appId: String, kind: ArtKind, remoteUrl: String) {
        val saved = withContext(Dispatchers.IO) { runCatching { download(context, appId, kind, remoteUrl) }.getOrNull() }
        prefs(context).edit { putString(key(appId, kind), saved ?: remoteUrl) }
        pickCount = -1
        _version.value++
    }

    // --- Pictures found without being asked (Android games have no store art of their own) ---
    private const val AUTO_PREFS = "rift_game_art_auto"
    private const val AUTO_NONE = "none:"
    private const val AUTO_RETRY_MS = 24 * 60 * 60 * 1000L
    private val autoSlots = kotlinx.coroutines.sync.Semaphore(2)

    private fun autoPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE)

    /** The cover found earlier for [appId] by [auto], or empty. Instant; for the first frame. */
    fun autoCached(context: Context, appId: String): String =
        autoPrefs(context).getString(appId, "").orEmpty().takeUnless { it.startsWith(AUTO_NONE) }.orEmpty()

    /**
     * A vertical cover for a game that has none: SteamGridDB's first result for [name]. Saved on the device
     * (a game SteamGridDB does not know is remembered for a day). Empty when there is no key, no result or no network.
     */
    suspend fun auto(context: Context, appId: String, name: String, key: String): String {
        val p = autoPrefs(context)
        val known = p.getString(appId, "").orEmpty()
        if (known.startsWith(AUTO_NONE)) {
            if (System.currentTimeMillis() - known.removePrefix(AUTO_NONE).toLongOrNull().let { it ?: 0L } < AUTO_RETRY_MS) return ""
        } else if (known.isNotEmpty()) {
            return known
        }
        if (key.isBlank()) return ""
        return autoSlots.withPermit {
            try {
                val path = SteamGridDbClient.resolve(key, SteamGridDbClient.Game(null, name))
                val option = SteamGridDbClient.options(key, path, ArtKind.Capsule, 0).firstOrNull()
                    ?: throw ArtError.NotFound
                val saved = withContext(Dispatchers.IO) { download(context, "auto|$appId", ArtKind.Capsule, option.url) }
                p.edit { putString(appId, saved) }
                saved
            } catch (e: ArtError) {
                if (e === ArtError.NotFound) p.edit { putString(appId, AUTO_NONE + System.currentTimeMillis()) }
                ""
            } catch (e: java.io.IOException) {
                ""
            } catch (e: IllegalStateException) {
                ""
            }
        }
    }

    /** Back to the store's own picture of [kind]. */
    fun reset(context: Context, appId: String, kind: ArtKind) {
        prefs(context).edit { remove(key(appId, kind)) }
        pickCount = -1
        File(context.filesDir, DIR).listFiles { f -> f.name.startsWith(fileStem(appId, kind)) }?.forEach { it.delete() }
        _version.value++
    }

    private fun fileStem(appId: String, kind: ArtKind): String {
        val hash = MessageDigest.getInstance("SHA-1").digest("$appId|${kind.key}".toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(16) + "_"
    }

    private fun download(context: Context, appId: String, kind: ArtKind, url: String): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stem = fileStem(appId, kind)
        val extension = url.substringBefore('?').substringAfterLast('.', "png").lowercase().takeIf { it in setOf("png", "jpg", "jpeg", "webp") } ?: "png"
        // A new name for every pick, so Coil never serves the previous picture from its cache.
        val target = File(dir, "$stem${System.currentTimeMillis()}.$extension")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            target.outputStream().use { out -> response.body.byteStream().use { it.copyTo(out) } }
        }
        dir.listFiles { f -> f.name.startsWith(stem) && f != target }?.forEach { it.delete() }
        return "file://${target.absolutePath}"
    }
}

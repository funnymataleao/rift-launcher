package dev.rift.launcher.art

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
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
    private const val TAG = "RiftArt"
    private const val IDS_PREFS = "rift_game_art_ids"

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

    /** [item] with the pictures the user picked in place of the store's; for games with none, the ones [autoFill] found. */
    fun apply(context: Context, item: LibraryItem): LibraryItem {
        val p = prefs(context)
        val a = autoPrefs(context)
        fun picked(kind: ArtKind) = p.getString(key(item.appId, kind), "").orEmpty().ifEmpty { autoValue(a, item.appId, kind) }
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
            customLogoUrl = logo.ifEmpty { item.customLogoUrl },
            customIconUrl = icon.ifEmpty { item.customIconUrl },
        )
    }

    /** How many pictures are picked or found in all; -1 until first asked. Lets [apply] skip the work for the common case of none. */
    @Volatile
    private var pickCount = -1

    private fun pickedCount(context: Context): Int {
        if (pickCount < 0) pickCount = prefs(context).all.size + autoPrefs(context).all.size
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
    suspend fun pick(context: Context, appId: String, kind: ArtKind, remoteUrl: String, optionId: Long = 0L) {
        val saved = withContext(Dispatchers.IO) {
            runCatching { download(context, appId, kind, remoteUrl) }
                .onFailure { Timber.tag(TAG).w(it, "Picked %s for %s did not download: %s", kind.key, appId, remoteUrl) }
                .getOrNull()
        }
        Timber.tag(TAG).i("Picked %s for %s: %s", kind.key, appId, saved ?: remoteUrl)
        prefs(context).edit { putString(key(appId, kind), saved ?: remoteUrl) }
        idPrefs(context).edit { if (optionId != 0L) putLong(key(appId, kind), optionId) else remove(key(appId, kind)) }
        pickCount = -1
        _version.value++
    }

    /** Lives as long as the app, so a pick finishes even when the picker is closed or its tab changed meanwhile. */
    private val picks = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [pick] that is not cancelled with the screen that asked for it. */
    fun pickAsync(context: Context, appId: String, kind: ArtKind, remoteUrl: String, optionId: Long): Job {
        val app = context.applicationContext
        return picks.launch { pick(app, appId, kind, remoteUrl, optionId) }
    }

    private fun idPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(IDS_PREFS, Context.MODE_PRIVATE)

    /** SteamGridDB's id of the picture now in use for [appId] and [kind] (picked, else found by itself), or 0 when unknown. */
    fun currentOptionId(context: Context, appId: String, kind: ArtKind): Long {
        val ids = idPrefs(context)
        if (prefs(context).getString(key(appId, kind), "").orEmpty().isNotEmpty()) return ids.getLong(key(appId, kind), 0L)
        return ids.getLong("auto|" + autoKey(appId, kind), 0L)
    }

    // --- Pictures found without being asked (emulator and Android games have no store art of their own) ---
    private const val AUTO_PREFS = "rift_game_art_auto"
    private const val AUTO_NONE = "none:"
    private const val AUTO_RETRY_MS = 24 * 60 * 60 * 1000L
    private const val AUTO_PATH = "path"
    private const val AUTO_PUBLISH_MS = 1_500L

    private fun autoPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(AUTO_PREFS, Context.MODE_PRIVATE)

    private fun autoKey(appId: String, kind: ArtKind) = "$appId|${kind.key}"

    /** What was found earlier for [appId] and [kind], or empty. */
    private fun autoValue(p: SharedPreferences, appId: String, kind: ArtKind): String =
        p.getString(autoKey(appId, kind), "").orEmpty().takeUnless { it.startsWith(AUTO_NONE) }.orEmpty()

    /** A "none yet" note that is younger than a day: SteamGridDB did not have it, no need to ask again at once. */
    private fun recentlyMissing(known: String) =
        known.startsWith(AUTO_NONE) &&
            System.currentTimeMillis() - (known.removePrefix(AUTO_NONE).toLongOrNull() ?: 0L) < AUTO_RETRY_MS

    /**
     * Finds [kinds] of pictures on SteamGridDB, by name, for every game of [items] that has none yet, one game
     * after another in the order given (the ones on screen first). Pictures are saved on the device; [version]
     * moves while this runs (at most about once a second) so the covers appear one by one. A game SteamGridDB does
     * not know is skipped for a day; a network failure is not remembered, the next run tries again.
     * Returns early when the key is missing or refused.
     */
    suspend fun autoFill(context: Context, items: List<Pair<LibraryItem, List<ArtKind>>>, key: String) {
        if (key.isBlank()) {
            Timber.tag(TAG).i("Auto art: no SteamGridDB key, %d games not asked", items.size)
            return
        }
        Timber.tag(TAG).i("Auto art: %d games", items.size)
        val p = autoPrefs(context)
        var lastPublish = 0L
        var dirty = false
        fun publish(force: Boolean) {
            val now = System.currentTimeMillis()
            if (dirty && (force || now - lastPublish >= AUTO_PUBLISH_MS)) {
                pickCount = -1
                _version.value++
                lastPublish = now
                dirty = false
            }
        }
        try {
            for ((item, kinds) in items) {
                val missing = kinds.filter { kind ->
                    val known = p.getString(autoKey(item.appId, kind), "").orEmpty()
                    known.isEmpty() || (known.startsWith(AUTO_NONE) && !recentlyMissing(known))
                }
                if (missing.isEmpty()) continue
                val path = resolveAuto(p, item, key) ?: continue
                for (kind in missing) {
                    if (fetchAuto(context, p, item.appId, path, kind, key)) dirty = true
                    publish(force = false)
                }
            }
        } catch (e: ArtError) {
            Timber.tag(TAG).w("Auto art stopped: %s", e.javaClass.simpleName)
            // A refused or missing key: nothing more to ask this time.
            if (e !== ArtError.BadKey && e !== ArtError.NoKey) throw e
        } finally {
            publish(force = true)
        }
    }

    /** SteamGridDB's path for the game, from the saved one or by searching its name; null when it has none. */
    private suspend fun resolveAuto(p: SharedPreferences, item: LibraryItem, key: String): String? {
        val pathKey = "${item.appId}|$AUTO_PATH"
        val known = p.getString(pathKey, "").orEmpty()
        if (known.isNotEmpty() && !known.startsWith(AUTO_NONE)) return known
        if (recentlyMissing(known)) return null
        return try {
            SteamGridDbClient.resolve(key, SteamGridDbClient.Game(null, item.name)).also { p.edit { putString(pathKey, it) } }
        } catch (e: ArtError) {
            when (e) {
                ArtError.NotFound -> p.edit { putString(pathKey, AUTO_NONE + System.currentTimeMillis()) }
                ArtError.BadKey, ArtError.NoKey -> throw e
                else -> Unit
            }
            null
        }
    }

    /** Downloads the first picture of [kind] for [path]; true when one was saved. */
    private suspend fun fetchAuto(context: Context, p: SharedPreferences, appId: String, path: String, kind: ArtKind, key: String): Boolean {
        val slot = autoKey(appId, kind)
        return try {
            val option = SteamGridDbClient.options(key, path, kind, 0).firstOrNull()
            if (option == null) {
                Timber.tag(TAG).i("Auto art: no %s for %s", kind.key, appId)
                p.edit { putString(slot, AUTO_NONE + System.currentTimeMillis()) }
                false
            } else {
                val saved = withContext(Dispatchers.IO) { download(context, "auto|$appId", kind, option.url) }
                p.edit { putString(slot, saved) }
                idPrefs(context).edit { putLong("auto|$slot", option.id) }
                true
            }
        } catch (e: ArtError) {
            if (e === ArtError.BadKey || e === ArtError.NoKey) throw e
            if (e === ArtError.NotFound) p.edit { putString(slot, AUTO_NONE + System.currentTimeMillis()) }
            false
        } catch (e: java.io.IOException) {
            Timber.tag(TAG).w(e, "Auto art: %s for %s did not download", kind.key, appId)
            false
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).w(e, "Auto art: %s for %s did not download", kind.key, appId)
            false
        }
    }

    /** Drops the pictures found without being asked for [appId] (renamed): the next auto fill searches again. */
    fun forgetAuto(context: Context, appId: String) {
        autoPrefs(context).edit {
            ArtKind.entries.forEach { remove(autoKey(appId, it)) }
            remove("$appId|$AUTO_PATH")
        }
        ArtKind.entries.forEach { kind ->
            File(context.filesDir, DIR).listFiles { f -> f.name.startsWith(fileStem("auto|$appId", kind)) }?.forEach { it.delete() }
        }
        pickCount = -1
        _version.value++
    }

    /** Back to the store's own picture of [kind]. */
    fun reset(context: Context, appId: String, kind: ArtKind) {
        prefs(context).edit { remove(key(appId, kind)) }
        idPrefs(context).edit { remove(key(appId, kind)) }
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
        Timber.tag(TAG).i("Saved %s for %s: %s (%d bytes)", kind.key, appId, target.absolutePath, target.length())
        return "file://${target.absolutePath}"
    }
}

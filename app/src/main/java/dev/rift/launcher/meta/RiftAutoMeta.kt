package dev.rift.launcher.meta

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import dev.rift.launcher.settings.RiftPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The description, rating, genres, tags, developer and release date of an Android or emulator game, from IGDB
 * with the user's own key. Fetched once when the game's page opens and kept on the device; a game IGDB does not
 * know is asked again after a day, a failed request (no network, no key) the next time the page opens.
 */
object RiftAutoMeta {
    private const val PREFS = "rift_game_meta"
    private const val RETRY_MS = 24 * 60 * 60 * 1000L
    private const val TRIED = "tried_"
    private val lock = Mutex()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What is saved for [appId] already; empty when nothing is. */
    fun cached(context: Context, appId: String): GameMeta? =
        prefs(context).getString(appId, null)?.let(GameMeta::fromJson)

    /** Drops what was found for [appId] (the game was renamed), so the next lookup asks IGDB again. */
    fun forget(context: Context, appId: String) {
        prefs(context).edit { remove(appId); remove(TRIED + appId) }
    }

    /** The saved metadata, or a fresh lookup when there is none (and the retry delay is over). */
    suspend fun ensure(context: Context, item: LibraryItem, platformId: String?): GameMeta? {
        cached(context, item.appId)?.let { return it }
        val rift = RiftPrefs.of(context)
        if (rift.igdbClientId.isBlank() || rift.igdbClientSecret.isBlank() || item.name.isBlank()) return null
        return lock.withLock {
            cached(context, item.appId)?.let { return@withLock it }
            val p = prefs(context)
            if (System.currentTimeMillis() - p.getLong(TRIED + item.appId, 0L) < RETRY_MS) return@withLock null
            val platforms = IgdbClient.platformsFor(platformId).ifEmpty {
                if (item.appId.startsWith("ANDROID_")) listOf(IgdbClient.ANDROID_PLATFORM) else emptyList()
            }
            try {
                val meta = IgdbClient.find(rift.igdbClientId, rift.igdbClientSecret, item.name, platforms)
                p.edit { putString(item.appId, meta.toJson()) }
                meta
            } catch (e: MetaError) {
                if (e === MetaError.NotFound) p.edit { putLong(TRIED + item.appId, System.currentTimeMillis()) }
                null
            }
        }
    }

    /**
     * Looks up every game that has no metadata yet, one after the other (IGDB allows four requests a second),
     * so a library that was just added is filled in before its pages are opened. [games] pairs a game with
     * its emulator system (null for Android games).
     */
    suspend fun prefetch(context: Context, games: List<Pair<LibraryItem, String?>>) {
        val rift = RiftPrefs.of(context)
        if (rift.igdbClientId.isBlank() || rift.igdbClientSecret.isBlank()) return
        for ((item, platformId) in games) {
            if (cached(context, item.appId) != null) continue
            ensure(context, item, platformId)
            delay(PREFETCH_GAP_MS)
        }
    }

    private const val PREFETCH_GAP_MS = 400L
}

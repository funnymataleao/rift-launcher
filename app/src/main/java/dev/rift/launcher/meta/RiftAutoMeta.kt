package dev.rift.launcher.meta

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import app.gamenative.data.StoreDetailsRepository
import dev.rift.launcher.art.SteamGridDbClient
import timber.log.Timber
import java.io.File
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
    private const val TRIED_IGDB = "tried_igdb_"
    private const val TAG = "RiftMeta"
    private val lock = Mutex()

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** What is saved for [appId] already; empty when nothing is. */
    fun cached(context: Context, appId: String): GameMeta? =
        prefs(context).getString(appId, null)?.let(GameMeta::fromJson)

    /** Bytes taken by the saved descriptions and ratings. */
    fun bytes(context: Context): Long =
        File(context.applicationInfo.dataDir, "shared_prefs/$PREFS.xml").length()

    /** Forgets everything found for every game; each game's page and the next library scan ask again. Returns the bytes freed. */
    fun clearAll(context: Context): Long {
        val freed = bytes(context)
        prefs(context).edit { clear() }
        return freed
    }

    /** Drops what was found for [appId] (the game was renamed), so the next lookup asks IGDB again. */
    fun forget(context: Context, appId: String) {
        prefs(context).edit { remove(appId); remove(TRIED + appId) }
    }

    /**
     * The saved metadata, or a fresh lookup when there is none (and the retry delay is over). IGDB (the
     * user's keys) is asked first; without keys, or when IGDB does not know the game, the Steam store's
     * game of exactly the same name is used. Steam-sourced data is upgraded once IGDB keys are saved.
     */
    suspend fun ensure(context: Context, item: LibraryItem, platformId: String?): GameMeta? {
        val saved = cached(context, item.appId)
        val rift = RiftPrefs.of(context)
        val hasKeys = rift.igdbClientId.isNotBlank() && rift.igdbClientSecret.isNotBlank()
        if (saved != null && (saved.source == "igdb" || !hasKeys)) return saved
        if (item.name.isBlank()) return saved
        return lock.withLock {
            val p = prefs(context)
            val current = cached(context, item.appId)
            if (current != null && (current.source == "igdb" || !hasKeys)) return@withLock current
            // A Steam result is only upgraded once a day; a game nobody knows is asked again after a day too.
            val triedKey = if (current == null) TRIED + item.appId else TRIED_IGDB + item.appId
            if (System.currentTimeMillis() - p.getLong(triedKey, 0L) < RETRY_MS) return@withLock current
            val platforms = IgdbClient.platformsFor(platformId).ifEmpty {
                if (item.appId.startsWith("ANDROID_")) listOf(IgdbClient.ANDROID_PLATFORM) else emptyList()
            }
            var igdbMissing = !hasKeys
            if (hasKeys) {
                try {
                    val meta = IgdbClient.find(rift.igdbClientId, rift.igdbClientSecret, item.name, platforms)
                    Timber.tag(TAG).i("%s: source igdb", item.name)
                    p.edit { putString(item.appId, meta.toJson()) }
                    return@withLock meta
                } catch (e: MetaError) {
                    Timber.tag(TAG).i("%s: igdb gave nothing (%s)", item.name, e.javaClass.simpleName)
                    // Not found or a bad key: Steam is tried; no network: nothing to try.
                    if (e === MetaError.Network) return@withLock current
                    igdbMissing = true
                }
            }
            if (current != null) {
                // Already has Steam data; IGDB did not know the game, so this is the end of the upgrade attempt.
                p.edit { putLong(triedKey, System.currentTimeMillis()) }
                return@withLock current
            }
            val steam = fromSteam(item.name)
            if (steam == null) {
                Timber.tag(TAG).i("%s: steam not reachable", item.name)
                return@withLock null
            }
            if (steam.isEmpty) {
                Timber.tag(TAG).i("%s: source none", item.name)
                p.edit { putLong(triedKey, System.currentTimeMillis()) }
                return@withLock null
            }
            Timber.tag(TAG).i("%s: source steam", item.name)
            p.edit { putString(item.appId, steam.toJson()) }
            if (igdbMissing && hasKeys) p.edit { putLong(TRIED_IGDB + item.appId, System.currentTimeMillis()) }
            steam
        }
    }

    /** The Steam store's game of exactly this name, or null when Steam cannot be reached. */
    private suspend fun fromSteam(name: String): GameMeta? {
        val found = StoreDetailsRepository.getMetaByTitle(SteamGridDbClient.cleanTitle(name)) ?: return null
        val d = found.details
        return GameMeta(
            description = d.description,
            rating = d.reviewPercentage,
            ratingCount = d.reviewCount,
            genres = d.tags.take(4),
            tags = d.tags.drop(4),
            developer = found.developer,
            publisher = found.publisher,
            releaseDate = found.releaseDate,
            source = "steam",
            ratingText = d.reviewSummary.orEmpty(),
        ).takeUnless { it.isEmpty } ?: GameMeta(source = "steam")
    }

    /**
     * Looks up every game that has no metadata yet, one after the other (IGDB allows four requests a second),
     * so a library that was just added is filled in before its pages are opened. [games] pairs a game with
     * its emulator system (null for Android games).
     */
    suspend fun prefetch(context: Context, games: List<Pair<LibraryItem, String?>>) {
        for ((item, platformId) in games) {
            if (cached(context, item.appId) != null) continue
            ensure(context, item, platformId)
            delay(PREFETCH_GAP_MS)
        }
    }

    private const val PREFETCH_GAP_MS = 400L
}

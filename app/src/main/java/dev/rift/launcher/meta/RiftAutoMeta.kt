package dev.rift.launcher.meta

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import app.gamenative.data.LibraryItem
import app.gamenative.data.StoreDetailsRepository
import timber.log.Timber
import java.util.Locale
import dev.rift.launcher.settings.RiftPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The description, rating, genres, tags, developer and release date of an Android or emulator game, from IGDB
 * (the user's own key) or, without one, from the free sources below. Fetched once when the game is added or its
 * page opens and kept on the device.
 */
object RiftAutoMeta {
    private const val PREFS = "rift_game_meta"
    private const val RETRY_MS = 24 * 60 * 60 * 1000L
    // "tried3_": new sources and title cleaning, so games nobody knew before are asked again.
    private const val TRIED = "tried3_"
    private const val TRIED_IGDB = "tried3_igdb_"
    private const val TAG = "RiftMeta"
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

    /**
     * The saved metadata, or a fresh lookup when there is none (and the retry delay is over). Order: IGDB (the
     * user's keys), then sources that need none: for Android games the Google Play page of the package, then the
     * Steam store's game of the same name, then Wikipedia. Data from a free source is upgraded to IGDB's once keys
     * are saved. A game nobody knows is asked again after a day.
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
            val triedKey = if (current == null) TRIED + item.appId else TRIED_IGDB + item.appId
            if (System.currentTimeMillis() - p.getLong(triedKey, 0L) < RETRY_MS) return@withLock current

            val title = TitleCleaner.clean(item.name)
            val android = item.appId.startsWith("ANDROID_")
            val language = Locale.getDefault().language.ifEmpty { "en" }
            Timber.tag(TAG).i("%s: searching as \"%s\"", item.name, title)
            val why = mutableListOf<String>()
            var unreachable = false

            fun found(meta: GameMeta): GameMeta {
                Timber.tag(TAG).i("%s: source %s", item.name, meta.source)
                p.edit { putString(item.appId, meta.toJson()) }
                return meta
            }

            if (hasKeys) {
                val platforms = IgdbClient.platformsFor(platformId).ifEmpty { if (android) listOf(IgdbClient.ANDROID_PLATFORM) else emptyList() }
                when (val r = igdb(rift.igdbClientId, rift.igdbClientSecret, title, platforms)) {
                    is MetaResult.Found -> return@withLock found(r.meta)
                    is MetaResult.Miss -> why += "igdb: ${r.reason}"
                    is MetaResult.Unreachable -> {
                        Timber.tag(TAG).i("%s: igdb unreachable (%s)", item.name, r.reason)
                        return@withLock current
                    }
                }
            } else {
                why += "igdb: no key"
            }
            if (current != null) {
                // Already has free-source data; IGDB did not know the game, so the upgrade is tried again tomorrow.
                Timber.tag(TAG).i("%s: igdb gave nothing (%s), keeping %s", item.name, why.joinToString(), current.source)
                p.edit { putLong(triedKey, System.currentTimeMillis()) }
                return@withLock current
            }

            val steps = buildList<Pair<String, suspend () -> MetaResult>> {
                if (android) add(Pair<String, suspend () -> MetaResult>("play", { PlayStoreClient.find(item.appId.removePrefix("ANDROID_"), language) }))
                add(Pair<String, suspend () -> MetaResult>("steam", { steam(title) }))
                add(Pair<String, suspend () -> MetaResult>("wikipedia", { WikipediaClient.find(title, platformId, android, language) }))
            }
            for ((name, step) in steps) {
                when (val r = step()) {
                    is MetaResult.Found -> return@withLock found(r.meta)
                    is MetaResult.Miss -> why += "$name: ${r.reason}"
                    is MetaResult.Unreachable -> {
                        unreachable = true
                        why += "$name: unreachable (${r.reason})"
                    }
                }
            }
            Timber.tag(TAG).i("%s: source none (%s)", item.name, why.joinToString("; "))
            // A source that could not be reached means "ask again next time", not "nobody knows it".
            if (!unreachable) p.edit { putLong(triedKey, System.currentTimeMillis()) }
            null
        }
    }

    private suspend fun igdb(id: String, secret: String, title: String, platforms: List<Int>): MetaResult = try {
        MetaResult.Found(IgdbClient.find(id, secret, title, platforms))
    } catch (e: MetaError) {
        when (e) {
            MetaError.Network -> MetaResult.Unreachable("network")
            MetaError.BadKey -> MetaResult.Miss("key refused")
            MetaError.NotFound -> MetaResult.Miss("not found")
            MetaError.NoKey -> MetaResult.Miss("no key")
        }
    }

    /** The Steam store's game of exactly this name. */
    private suspend fun steam(title: String): MetaResult {
        val found = StoreDetailsRepository.getMetaByTitle(title) ?: return MetaResult.Unreachable("Steam search failed")
        val d = found.details
        val meta = GameMeta(
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
        )
        return if (found.isEmpty || meta.isEmpty) MetaResult.Miss("no game with this name") else MetaResult.Found(meta)
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

package dev.rift.launcher.meta

import android.content.Context
import app.gamenative.data.LibraryItem
import app.gamenative.data.StoreDetailsRepository
import dev.rift.launcher.settings.RiftPrefs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.util.Locale

/** One game a source offers for a name, shown in the list where a game's information is chosen by hand. */
class MetaCandidate(
    /** "igdb", "steam", "wikipedia" or "play". */
    val source: String,
    /** What the source needs to finish the candidate (an id). */
    val ref: String,
    val title: String,
    /** The systems it is on, when the source says; empty otherwise. */
    val platform: String,
    val year: Int?,
    /** What the source already sent: enough to show and, for most sources, to save. */
    val meta: GameMeta,
)

/** What a search for candidates found, and whether some source could not be reached. */
class MetaSearch(val candidates: List<MetaCandidate>, val unreachable: Boolean)

/** The hand-picked side of [RiftAutoMeta]: every source searched by one editable name, the user choosing the game. */
object MetaCandidates {
    private const val TAG = "RiftMeta"

    suspend fun search(context: Context, item: LibraryItem, platformId: String?, query: String): MetaSearch = coroutineScope {
        val rift = RiftPrefs.of(context)
        val android = item.appId.startsWith("ANDROID_")
        val language = Locale.getDefault().language.ifEmpty { "en" }
        val term = query.trim()
        var unreachable = false

        val igdb = async<List<MetaCandidate>> {
            if (rift.igdbClientId.isBlank() || rift.igdbClientSecret.isBlank()) return@async emptyList<MetaCandidate>()
            val platforms = IgdbClient.platformsFor(platformId).ifEmpty { if (android) listOf(IgdbClient.ANDROID_PLATFORM) else emptyList() }
            try {
                IgdbClient.candidates(rift.igdbClientId, rift.igdbClientSecret, term, platforms)
            } catch (e: MetaError) {
                if (e === MetaError.Network) unreachable = true
                Timber.tag(TAG).i("%s: igdb candidates: %s", term, e.javaClass.simpleName)
                emptyList()
            }
        }
        val wikipedia = async<List<MetaCandidate>> { WikipediaClient.candidates(term, platformId, android, language) }
        val steam = async<List<MetaCandidate>> {
            val hits = StoreDetailsRepository.searchMeta(term)
            if (hits == null) unreachable = true
            hits.orEmpty().filterNot { it.meta.isEmpty }.map { hit ->
                val meta = RiftAutoMeta.steamMeta(hit.meta)
                MetaCandidate("steam", hit.appId.toString(), hit.name, "Steam", meta.releaseDate.takeIf { it > 0 }?.let { yearOf(it) }, meta)
            }
        }
        val play = async<List<MetaCandidate>> {
            if (!android) return@async emptyList<MetaCandidate>()
            when (val r = PlayStoreClient.find(item.appId.removePrefix("ANDROID_"), language)) {
                is MetaResult.Found -> listOf(MetaCandidate("play", item.appId.removePrefix("ANDROID_"), item.name, "Google Play", r.meta.releaseDate.takeIf { it > 0 }?.let { yearOf(it) }, r.meta))
                is MetaResult.Unreachable -> emptyList<MetaCandidate>().also { unreachable = true }
                is MetaResult.Miss -> emptyList()
            }
        }
        val all = igdb.await() + play.await() + wikipedia.await() + steam.await()
        Timber.tag(TAG).i("%s: manual search \"%s\": %d candidates%s", item.name, term, all.size, if (unreachable) " (a source was unreachable)" else "")
        MetaSearch(all, unreachable)
    }

    /** The candidate as it will be saved: IGDB adds its time to beat, Wikipedia its Wikidata facts. */
    suspend fun complete(context: Context, candidate: MetaCandidate): GameMeta = when (candidate.source) {
        "wikipedia" -> WikipediaClient.complete(candidate)
        "igdb" -> {
            val rift = RiftPrefs.of(context)
            IgdbClient.complete(rift.igdbClientId, rift.igdbClientSecret, candidate)
        }
        else -> candidate.meta
    }

    private fun yearOf(unixSeconds: Long): Int =
        java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = unixSeconds * 1000 }.get(java.util.Calendar.YEAR)
}

package dev.rift.launcher.art

import android.content.Context
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * "Default covers" (Y in the art picker), like SteamGridDB's "apply default art": the open game gets Steam's own
 * cover, wide picture and banner, from the addresses Steam names for it now, in place of whatever was picked.
 * Only that game: the others keep their pictures.
 */
object StandardArt {
    /** The art picker's id of Steam's own picture of a kind: [STEAM_OPTION_ID] minus the kind's ordinal. */
    const val STEAM_OPTION_ID = -100L

    private val KINDS = listOf(ArtKind.Capsule, ArtKind.Wide, ArtKind.Hero)

    /** Returns false when [item] is not a Steam game or Steam did not answer. */
    suspend fun apply(context: Context, item: LibraryItem): Boolean = withContext(Dispatchers.IO) {
        if (item.gameSource != GameSource.STEAM || item.gameId <= 0) return@withContext false
        val assets = SteamStoreArt.assets(item.gameId) ?: return@withContext false
        var any = false
        for (kind in KINDS) {
            val urls = assets.of(kind)
            val url = urls.firstOrNull { it.substringBefore('?').contains("_2x.") } ?: urls.firstOrNull() ?: continue
            GameArtStore.pick(context.applicationContext, item.appId, kind, url, STEAM_OPTION_ID - kind.ordinal)
            any = true
        }
        Timber.tag("RiftArt").i("Default art for %s: %s", item.appId, if (any) "set" else "Steam has none")
        any
    }
}

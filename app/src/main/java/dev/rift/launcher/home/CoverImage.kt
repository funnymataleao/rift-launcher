package dev.rift.launcher.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.art.GameArtStore
import dev.rift.launcher.art.SteamStoreArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val RETRY_DELAY_MS = 2_000L
private const val MAX_ROUNDS = 3

/**
 * A cover that does not stay empty: the first non-empty URL of [urls] is loaded, a failed load tries
 * the next one, and once all have failed the whole list is tried again a little later (the network is
 * often not up yet right after start). A change of URLs (the library learns a game's art after start)
 * starts a fresh load instead of keeping the old, empty one.
 */
@Composable
fun CoverImage(
    urls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier.fillMaxSize(),
    lateUrls: (suspend () -> List<String>)? = null,
) {
    val base = remember(urls) { urls.filter { it.isNotBlank() }.distinct() }
    // Asked for once, when every address of the list has failed.
    var late by remember(base) { mutableStateOf(emptyList<String>()) }
    val candidates = remember(base, late) { (base + late).distinct() }
    if (candidates.isEmpty() && lateUrls == null) return
    var attempt by remember(base) { mutableIntStateOf(0) }
    val firstRoundFailed = attempt >= base.size
    if (lateUrls != null && firstRoundFailed) {
        LaunchedEffect(base) {
            late = withContext(Dispatchers.IO) { runCatching { lateUrls() }.getOrDefault(emptyList()) }
                .filter { it.isNotBlank() }
            if (late.isEmpty() || late.all { it in base }) Timber.tag("RiftArt").w("No cover loads for %s", base.firstOrNull())
        }
    }
    if (candidates.isEmpty() || attempt >= candidates.size * MAX_ROUNDS) return
    val url = candidates[attempt % candidates.size]
    key(url, attempt) {
        CoilImage(
            modifier = modifier,
            imageModel = { url },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = contentDescription),
            loading = {},
            failure = {
                LaunchedEffect(Unit) {
                    // The next URL right away; a new round only after a pause.
                    if ((attempt + 1) % candidates.size == 0) delay(RETRY_DELAY_MS)
                    attempt++
                }
            },
        )
    }
}

/**
 * The art for a 2:3 cover, best first. A picture the user picked comes before everything. Then Steam games
 * use Steam's own 600x900 library cover, built from the app id, so the cover is right from the first frame
 * and does not change when the library learns the game's art later; the wide header is the last resort
 * (never for a cloud game, whose wide art would sit on a tall card until the poster came).
 */
fun portraitCoverUrls(item: app.gamenative.data.LibraryItem): List<String> =
    // A cloud game always has the store's tall poster: it is retried rather than replaced by wide art.
    if (dev.rift.launcher.cloud.CloudLibrary.isCloud(item.appId) && item.capsuleImageUrl.isNotBlank()) {
        listOf(item.capsuleImageUrl)
    } else {
        listOfNotNull(
            item.capsuleImageUrl.takeIf { GameArtStore.isPicked(it) },
            steamAsset(item, "library_600x900.jpg"),
            item.capsuleImageUrl,
            steamAsset(item, "library_600x900_2x.jpg"),
            steamAsset(item, "library_600x900.jpg", AKAMAI),
            item.headerImageUrl,
            steamAsset(item, "header.jpg", AKAMAI),
            item.heroImageUrl,
        )
    }

/** The art for a wide tile, best first: a picked picture, then the header (built from the app id for Steam), then the hero. */
fun wideCoverUrls(item: app.gamenative.data.LibraryItem): List<String> = listOfNotNull(
    item.headerImageUrl.takeIf { GameArtStore.isPicked(it) },
    steamAsset(item, "header.jpg"),
    item.headerImageUrl,
    steamAsset(item, "header.jpg", AKAMAI),
    item.heroImageUrl,
    item.capsuleImageUrl,
)

/** What a Steam game's card still tries when none of its pictures loads: the ones the store page names. Null for other games. */
fun storeCoverLookup(item: app.gamenative.data.LibraryItem, portrait: Boolean): (suspend () -> List<String>)? {
    if (item.gameSource != app.gamenative.data.GameSource.STEAM) return null
    val id = item.appId.substringAfter('_', "").toIntOrNull() ?: return null
    return { SteamStoreArt.urls(id, portrait) }
}

private const val AKAMAI = "https://cdn.akamai.steamstatic.com/steam/apps/"
private const val SHARED = "https://shared.steamstatic.com/store_item_assets/steam/apps/"

private fun steamAsset(item: app.gamenative.data.LibraryItem, file: String, host: String = SHARED): String? {
    if (item.gameSource != app.gamenative.data.GameSource.STEAM) return null
    val id = item.appId.substringAfter('_', "").toIntOrNull() ?: return null
    return "$host$id/$file"
}

package dev.rift.launcher.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import kotlinx.coroutines.delay

private const val RETRY_DELAY_MS = 2_000L
private const val MAX_ROUNDS = 3

/**
 * A cover that does not stay empty: the first non-empty URL of [urls] is loaded, a failed load tries
 * the next one, and once all have failed the whole list is tried again a little later (the network is
 * often not up yet right after start). A change of URLs (the library learns a game's art after start)
 * starts a fresh load instead of keeping the old, empty one.
 */
@Composable
fun CoverImage(urls: List<String>, contentDescription: String?, modifier: Modifier = Modifier.fillMaxSize()) {
    val candidates = remember(urls) { urls.filter { it.isNotBlank() }.distinct() }
    if (candidates.isEmpty()) return
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    if (attempt >= candidates.size * MAX_ROUNDS) return
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

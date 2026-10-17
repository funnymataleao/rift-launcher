package dev.rift.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftTheme
import kotlinx.coroutines.delay

/**
 * Start-up in one piece (Denis 2026-10-16): the black system splash, then a black curtain with one
 * expressive loading indicator over everything, so no half-drawn screen shows; it fades out once the
 * Rift home has something to show, and the home's entrance plays.
 */
object RiftStartup {
    var homeReady by mutableStateOf(false)

    const val CURTAIN_FADE_MS = 320
    private const val CURTAIN_MAX_MS = 9000L
    private const val INDICATOR_AFTER_MS = 600L
    private const val INDICATOR_FADE_MS = 240

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun Curtain(skip: Boolean) {
        var timedOut by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(CURTAIN_MAX_MS)
            timedOut = true
        }
        AnimatedVisibility(
            visible = !skip && !homeReady && !timedOut,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(tween(CURTAIN_FADE_MS)),
        ) {
            // Plain black first; the indicator only on a slow start, so a fast one never flashes it
            // (and the first frame does not pay for its shape morphing).
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(INDICATOR_AFTER_MS)
                slow = true
            }
            Box(modifier = Modifier.fillMaxSize().background(RiftColors.Background), contentAlignment = Alignment.Center) {
                AnimatedVisibility(visible = slow, enter = fadeIn(tween(INDICATOR_FADE_MS)), exit = ExitTransition.None) {
                    RiftTheme {
                        LoadingIndicator(color = RiftColors.Primary, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }
    }
}

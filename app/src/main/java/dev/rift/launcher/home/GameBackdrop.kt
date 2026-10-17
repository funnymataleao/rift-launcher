package dev.rift.launcher.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A game's art behind whatever waits for it, like Steam (Denis): the hero under a dark veil, drifting
 * with a slow zoom, and the game's logo in the middle. Used by the stream splash, the screen shown while a
 * game downloads and the screen shown while a game starts, so they all look the same.
 *
 * The logo is the one given (picked or found), else Steam's library logo for [steamAppId], else [name].
 */
@Composable
fun GameBackdrop(
    heroUrl: String,
    logoUrl: String,
    steamAppId: Int,
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val drift = rememberInfiniteTransition(label = "backdropDrift")
        val scale by drift.animateFloat(
            initialValue = 1f,
            targetValue = DRIFT_SCALE,
            animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Reverse),
            label = "backdropZoom",
        )
        if (heroUrl.isNotBlank()) {
            CoverImage(
                urls = listOf(heroUrl),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = VEIL_ALPHA)))
        // Steam's logo is looked up off the main thread; null while looking.
        val logo by produceState(logoUrl.ifBlank { null }, logoUrl, steamAppId) {
            value = logoUrl.ifBlank { if (steamAppId != 0) withContext(Dispatchers.IO) { GameLogos.steamLogoUrl(steamAppId) } else "" }
        }
        var logoFailed by remember(logo) { mutableStateOf(logo.isNullOrEmpty()) }
        val url = logo
        if (url != null && !logoFailed) {
            RiftLogo(
                url = url,
                contentDescription = name,
                modifier = Modifier.fillMaxWidth(LOGO_WIDTH).height(LOGO_HEIGHT),
                alignment = Alignment.Center,
                onFailed = { logoFailed = true },
            )
        } else if (url != null && name.isNotBlank()) {
            Text(
                text = name,
                color = Color.White,
                fontSize = 54.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
}

private const val VEIL_ALPHA = 0.3f
private const val DRIFT_SCALE = 1.06f
private const val DRIFT_MS = 20_000
private const val LOGO_WIDTH = 0.5f
private val LOGO_HEIGHT = 220.dp

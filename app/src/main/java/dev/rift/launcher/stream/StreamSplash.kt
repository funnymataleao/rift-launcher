package dev.rift.launcher.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.home.CoverImage
import dev.rift.launcher.home.GameLogos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * What the stream shows while the game starts on the PC, like Steam (Denis): the game's art under a
 * dark veil, drifting with a slow zoom, and its logo in the middle, instead of the PC's desktop or Big
 * Picture. The stream runs underneath; the splash fades out once the game is up.
 */
@Composable
fun StreamSplash(visible: Boolean, appName: String, heroUrl: String, logoUrl: String, steamAppId: Int) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(0)), exit = fadeOut(tween(SPLASH_FADE_MS))) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val drift = rememberInfiniteTransition(label = "splashDrift")
            val scale by drift.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Reverse),
                label = "splashZoom",
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
            // A picked logo first, then Steam's library logo (looked up off the main thread), then the name.
            val logo by produceState(logoUrl.ifBlank { null }, logoUrl, steamAppId) {
                if (value == null) value = if (steamAppId != 0) withContext(Dispatchers.IO) { GameLogos.steamLogoUrl(steamAppId) } else ""
            }
            var logoFailed by remember(logo) { mutableStateOf(logo.isNullOrEmpty()) }
            val url = logo
            if (url != null && !logoFailed) {
                CoilImage(
                    modifier = Modifier.fillMaxWidth(LOGO_WIDTH).height(LOGO_HEIGHT),
                    imageModel = { url },
                    imageOptions = ImageOptions(contentScale = ContentScale.Fit, alignment = Alignment.Center, contentDescription = appName),
                    loading = {},
                    failure = { LaunchedEffect(Unit) { logoFailed = true } },
                )
            } else if (url != null) {
                Text(
                    text = appName,
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
}

/**
 * Plays the stream's sound and tells when the PC has been making sound for a while: the sign a game
 * is up. A desktop or Big Picture is silent, apart from short system and launch sounds; a game's
 * intro, menu music or first scene is not.
 */
class SoundWatch(private val inner: AudioRenderer, private val onSustainedSound: () -> Unit) : AudioRenderer by inner {
    private var frameMs = 0
    private var loudMs = 0
    private var quietMs = 0

    @Volatile
    var armed = true

    override fun setup(audioConfiguration: MoonBridge.AudioConfiguration, sampleRate: Int, samplesPerFrame: Int): Int {
        frameMs = if (sampleRate > 0) (samplesPerFrame * 1000 / sampleRate).coerceAtLeast(1) else 5
        return inner.setup(audioConfiguration, sampleRate, samplesPerFrame)
    }

    override fun playDecodedAudio(audioData: ShortArray) {
        inner.playDecodedAudio(audioData)
        if (!armed || audioData.isEmpty()) return
        var sum = 0.0
        for (sample in audioData) sum += sample.toDouble() * sample
        val loud = sqrt(sum / audioData.size) >= SOUND_RMS
        if (loud) {
            loudMs += frameMs
            quietMs = 0
        } else {
            // A short pause (between notes, words) does not break the run; a real silence does.
            quietMs += frameMs
            if (quietMs >= SOUND_GAP_MS) loudMs = 0
        }
        if (loudMs >= SOUND_SUSTAIN_MS) {
            armed = false
            onSustainedSound()
        }
    }
}

/** About -40 dBFS: anything clearly audible. */
private const val SOUND_RMS = 330.0
private const val SOUND_GAP_MS = 400
private const val SOUND_SUSTAIN_MS = 2000
private const val VEIL_ALPHA = 0.3f
private const val DRIFT_MS = 20_000
private const val SPLASH_FADE_MS = 600
private const val LOGO_WIDTH = 0.5f
private val LOGO_HEIGHT = 220.dp

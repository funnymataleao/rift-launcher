package dev.rift.launcher.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.jni.MoonBridge
import dev.rift.launcher.home.GameBackdrop
import kotlin.math.sqrt

/**
 * What the stream shows while the game starts on the PC, like Steam (Denis): the game's [GameBackdrop]
 * instead of the PC's desktop or Big Picture. The stream runs underneath; the splash fades out once the
 * game is up.
 */
@Composable
fun StreamSplash(visible: Boolean, appName: String, heroUrl: String, logoUrl: String, steamAppId: Int) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(0)), exit = fadeOut(tween(SPLASH_FADE_MS))) {
        GameBackdrop(heroUrl = heroUrl, logoUrl = logoUrl, steamAppId = steamAppId, name = appName)
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
private const val SPLASH_FADE_MS = 600

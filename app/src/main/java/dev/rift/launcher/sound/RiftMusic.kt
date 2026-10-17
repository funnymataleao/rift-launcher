package dev.rift.launcher.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.rift.launcher.sound.pack.InstalledPacks
import timber.log.Timber
import java.io.File

/** Looping background music from the music pack picked in the settings. Quiet and off until a pack is chosen. */
object RiftMusic {
    private var player: MediaPlayer? = null
    private var playing: File? = null

    /** Starts the chosen track, or stops when music is off or no pack is picked. Safe to call again at any time. */
    @Synchronized
    fun apply(context: Context) {
        val app = context.applicationContext
        val track = if (RiftSoundSettings.isMusicEnabled(app)) {
            InstalledPacks.get(app, RiftSoundSettings.musicPackId(app))?.let { InstalledPacks.musicTrack(it.dir) }
        } else {
            null
        }
        if (track == null) {
            stop()
            return
        }
        val volume = RiftSoundSettings.musicVolume(app) / 100f
        if (playing == track && player != null) {
            player?.setVolume(volume, volume)
            return
        }
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(track.path)
                isLooping = true
                setVolume(volume, volume)
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
            playing = track
        }.onFailure {
            Timber.tag("RiftMusic").w(it, "Cannot play %s", track.name)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        runCatching { player?.release() }
        player = null
        playing = null
    }
}

/** Plays the music while the screen it sits in is the one in front, and stops when it is left or covered. */
@Composable
fun RiftMusicEffect() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> RiftMusic.apply(context)
                Lifecycle.Event.ON_PAUSE -> RiftMusic.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            RiftMusic.stop()
        }
    }
}

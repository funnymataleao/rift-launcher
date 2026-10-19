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
    // Whether [player] finished preparing: start() before that puts a MediaPlayer in its error state.
    private var prepared = false

    // Screens that play their own sound (the game page trailer) hold the music: it fades out and pauses,
    // and fades back in once nobody holds it.
    private val holds = mutableSetOf<Any>()
    // Screens the music plays on (the home, the assistant, Settings). Going from one to the next, the one left
    // pauses before the next resumes: for that moment none is in front. Stopping then released the player and
    // the next screen started the track over (Denis 2026-10-19). So the music only stops when no screen has
    // come back after [LEAVE_GRACE_MS] (the app left, a game or a stream started); between screens it plays on.
    private val screens = mutableSetOf<Any>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopIfLeft = Runnable { synchronized(this) { if (screens.isEmpty()) stop() } }
    private const val LEAVE_GRACE_MS = 1500L

    /** [screen] is in front: the music plays on (or starts). Call from the main thread. */
    @Synchronized
    fun show(screen: Any, context: Context) {
        handler.removeCallbacks(stopIfLeft)
        screens.add(screen)
        apply(context)
    }

    /** [screen] is left or covered: the music stops unless another screen it plays on comes to the front. */
    @Synchronized
    fun hide(screen: Any) {
        if (screens.remove(screen) && screens.isEmpty()) {
            handler.removeCallbacks(stopIfLeft)
            handler.postDelayed(stopIfLeft, LEAVE_GRACE_MS)
        }
    }
    private var fade: android.animation.ValueAnimator? = null
    private var level = 1f
    private var target = 1f

    private fun setLevel(value: Float) {
        level = value
        player?.setVolume(level * target, level * target)
    }

    private fun fadeTo(to: Float, then: () -> Unit = {}) {
        fade?.cancel()
        fade = android.animation.ValueAnimator.ofFloat(level, to).apply {
            duration = 1200L
            addUpdateListener { setLevel(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) then() }
            })
            start()
        }
    }

    /** Fades the music out and pauses it while [owner] plays its own sound. Call from the main thread. */
    @Synchronized
    fun hold(owner: Any) {
        if (!holds.add(owner) || holds.size > 1) return
        fadeTo(0f) { runCatching { player?.takeIf { it.isPlaying }?.pause() } }
    }

    /** Lets the music fade back in once no one holds it. Call from the main thread. */
    @Synchronized
    fun release(owner: Any) {
        if (!holds.remove(owner) || holds.isNotEmpty()) return
        if (prepared) runCatching { player?.takeIf { !it.isPlaying }?.start() }
        fadeTo(1f)
    }

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
        target = volume
        level = if (holds.isEmpty()) 1f else 0f
        if (playing == track && player != null) {
            // The same track is already on: keep it going where it is, never from the start.
            setLevel(level)
            if (holds.isEmpty() && prepared) runCatching { player?.takeIf { !it.isPlaying }?.start() }
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
                setVolume(volume * level, volume * level)
                setOnPreparedListener {
                    prepared = true
                    if (holds.isEmpty()) it.start()
                }
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
        prepared = false
    }
}

/** Plays the music while the screen it sits in is the one in front, and stops when it is left or covered. */
@Composable
fun RiftMusicEffect() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    val screen = androidx.compose.runtime.remember { Any() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> RiftMusic.show(screen, context)
                Lifecycle.Event.ON_PAUSE -> RiftMusic.hide(screen)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            RiftMusic.hide(screen)
        }
    }
}

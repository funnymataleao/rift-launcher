package dev.rift.launcher.sound.pack

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rift.launcher.sound.RiftMusic
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.RiftSoundSettings
import timber.log.Timber
import java.io.File

/**
 * Listening to a pack before choosing it: a stretch of a music pack's track, or a handful of a sound pack's
 * interface sounds one after another. One preview at a time; the home music stays quiet meanwhile and returns after.
 * Call from the main thread.
 */
object PackPreview {
    private const val MUSIC_MILLIS = 20_000L
    private const val GAP_MILLIS = 300L
    private val SOUNDS = listOf(RiftSound.Move, RiftSound.Select, RiftSound.SidebarOpen, RiftSound.Back, RiftSound.Launch)

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var generation = 0

    /** Key of the pack being heard now, or null. Read from composables. */
    var playing by mutableStateOf<String?>(null)
        private set

    fun playMusic(context: Context, key: String, dir: File) {
        val track = InstalledPacks.musicTrack(dir) ?: return
        val volume = RiftSoundSettings.musicVolume(context) / 100f
        val mine = begin(context, key)
        start(track, volume, onDone = { finish(context, mine) })
        handler.postDelayed({ finish(context, mine) }, MUSIC_MILLIS)
    }

    fun playSounds(context: Context, key: String, dir: File) {
        val files = SOUNDS.mapNotNull { sound -> sound.deckNames.firstNotNullOfOrNull { InstalledPacks.file(dir, it) } }
        if (files.isEmpty()) return
        val volume = (RiftSoundSettings.volume(context) / 100f).coerceAtLeast(0.3f)
        val mine = begin(context, key)
        fun next(i: Int) {
            if (mine != generation) return
            if (i >= files.size) {
                finish(context, mine)
                return
            }
            start(files[i], volume, onDone = { handler.postDelayed({ next(i + 1) }, GAP_MILLIS) })
        }
        next(0)
    }

    /** Stops the preview and lets the home music come back. */
    fun stop(context: Context) {
        if (playing == null) return
        finish(context, generation)
    }

    private fun begin(context: Context, key: String): Int {
        handler.removeCallbacksAndMessages(null)
        release()
        generation += 1
        playing = key
        RiftMusic.stop()
        return generation
    }

    private fun finish(context: Context, mine: Int) {
        if (mine != generation) return
        handler.removeCallbacksAndMessages(null)
        release()
        generation += 1
        playing = null
        RiftMusic.apply(context)
    }

    private fun start(file: File, volume: Float, onDone: () -> Unit) {
        val mine = generation
        release()
        runCatching {
            val p = MediaPlayer()
            player = p
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            p.setDataSource(file.path)
            p.setVolume(volume, volume)
            p.setOnPreparedListener { if (mine == generation) it.start() }
            p.setOnCompletionListener { if (mine == generation) onDone() }
            p.setOnErrorListener { _, _, _ ->
                if (mine == generation) onDone()
                true
            }
            p.prepareAsync()
        }.onFailure {
            Timber.tag("RiftPreview").w(it, "Cannot play %s", file.name)
            onDone()
        }
    }

    private fun release() {
        runCatching { player?.release() }
        player = null
    }
}

package dev.rift.launcher.stream

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * PC streaming settings, shown in Rift's settings under "PC streaming".
 *
 * The settings screen reads and writes these properties; the stream screen reads them when a stream
 * starts. Values are kept as plain strings and ints so the screen can list them as options.
 */
class StreamSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** "native" (the screen's own size), "720p", "1080p", "1440p" or "2160p". */
    var resolution: String
        get() = prefs.getString(KEY_RESOLUTION, RESOLUTION_NATIVE) ?: RESOLUTION_NATIVE
        set(value) = prefs.edit { putString(KEY_RESOLUTION, value) }

    /** Frames per second; 0 follows the screen's refresh rate. */
    var fps: Int
        get() = prefs.getInt(KEY_FPS, 0)
        set(value) = prefs.edit { putInt(KEY_FPS, value) }

    /** Video bitrate in Kbps; 0 picks one from resolution and frame rate. */
    var bitrateKbps: Int
        get() = prefs.getInt(KEY_BITRATE, 0)
        set(value) = prefs.edit { putInt(KEY_BITRATE, value) }

    /** "auto", "h264", "hevc" or "av1". */
    var codec: String
        get() = prefs.getString(KEY_CODEC, CODEC_AUTO) ?: CODEC_AUTO
        set(value) = prefs.edit { putString(KEY_CODEC, value) }

    var hdr: Boolean
        get() = prefs.getBoolean(KEY_HDR, false)
        set(value) = prefs.edit { putBoolean(KEY_HDR, value) }

    /** "stereo", "surround51" or "surround71". */
    var audio: String
        get() = prefs.getString(KEY_AUDIO, AUDIO_STEREO) ?: AUDIO_STEREO
        set(value) = prefs.edit { putString(KEY_AUDIO, value) }

    /** Keep the sound playing on the PC as well. */
    var playAudioOnPc: Boolean
        get() = prefs.getBoolean(KEY_PLAY_AUDIO_ON_PC, false)
        set(value) = prefs.edit { putBoolean(KEY_PLAY_AUDIO_ON_PC, value) }

    var gamepadVibration: Boolean
        get() = prefs.getBoolean(KEY_GAMEPAD_VIBRATION, true)
        set(value) = prefs.edit { putBoolean(KEY_GAMEPAD_VIBRATION, value) }

    /** Frame rate, latency and decoder stats over the stream. */
    var showStats: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STATS, false)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_STATS, value) }

    companion object {
        const val PREFS_NAME = "rift_stream"

        const val KEY_RESOLUTION = "resolution"
        const val KEY_FPS = "fps"
        const val KEY_BITRATE = "bitrate_kbps"
        const val KEY_CODEC = "codec"
        const val KEY_HDR = "hdr"
        const val KEY_AUDIO = "audio"
        const val KEY_PLAY_AUDIO_ON_PC = "play_audio_on_pc"
        const val KEY_GAMEPAD_VIBRATION = "gamepad_vibration"
        const val KEY_SHOW_STATS = "show_stats"

        const val RESOLUTION_NATIVE = "native"
        val RESOLUTIONS = listOf(RESOLUTION_NATIVE, "720p", "1080p", "1440p", "2160p")
        val FPS_OPTIONS = listOf(0, 30, 60, 90, 120)

        const val CODEC_AUTO = "auto"
        val CODECS = listOf(CODEC_AUTO, "h264", "hevc", "av1")

        const val AUDIO_STEREO = "stereo"
        val AUDIO_OPTIONS = listOf(AUDIO_STEREO, "surround51", "surround71")

        /** Height in pixels for a fixed resolution, or null for "native". */
        fun heightOf(resolution: String): Int? = resolution.removeSuffix("p").toIntOrNull()

        /** Moonlight's default bitrate table, scaled by frame rate. */
        fun autoBitrateKbps(width: Int, height: Int, fps: Int): Int {
            val pixels = width.toLong() * height
            val base = when {
                pixels <= 1280L * 720 -> 5000
                pixels <= 1920L * 1080 -> 10000
                pixels <= 2560L * 1600 -> 20000
                else -> 40000
            }
            return (base * (fps.coerceAtLeast(30) / 30f)).toInt().coerceAtMost(150000)
        }
    }
}

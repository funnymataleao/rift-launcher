package dev.rift.launcher.stream

import android.content.Context
import android.content.SharedPreferences

/**
 * PC streaming settings as the stream screen reads them when a stream starts.
 *
 * Rift's settings screen ("PC streaming" section, RiftPrefs) owns these keys in "rift_settings";
 * this is a read-only view with the same keys and defaults.
 */
class StreamSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** "1280x720", "1920x1080", "2560x1440", "3840x2160" or "device" (the screen's own size). */
    val resolution: String get() = prefs.getString("stream_resolution", "device") ?: "device"
    /** 0 for the screen's own (highest) refresh rate. */
    val fps: Int get() = prefs.getInt("stream_fps", 0)

    /** Mbps; 0 picks a bitrate from resolution and frame rate. */
    val bitrateMbps: Int get() = prefs.getInt("stream_bitrate_mbps", 0)

    /** "auto", "h264", "hevc" or "av1". */
    val codec: String get() = prefs.getString("stream_codec", "auto") ?: "auto"
    val hdr: Boolean get() = prefs.getBoolean("stream_hdr", false)
    val fullRange: Boolean get() = prefs.getBoolean("stream_full_range", false)
    val ultraLowLatency: Boolean get() = prefs.getBoolean("stream_ultra_low_latency", false)

    /** DecoderConfiguration.FRAME_PACING_*: 0 lowest latency, 1 balanced, 2 cap FPS, 3 smoothest. */
    val framePacing: Int get() = prefs.getInt("stream_frame_pacing", 1)

    /** "stereo", "5.1" or "7.1". */
    val audio: String get() = prefs.getString("stream_audio", "stereo") ?: "stereo"

    /** Keep the sound playing on the PC as well. */
    val playLocalAudio: Boolean get() = prefs.getBoolean("stream_play_local_audio", false)

    /** Let the host change game settings to fit the stream (SOPS). */
    val optimizeGameSettings: Boolean get() = prefs.getBoolean("stream_optimize_game_settings", true)
    val virtualDisplay: Boolean get() = prefs.getBoolean("stream_virtual_display", true)
    val adaptiveResolution: Boolean get() = prefs.getBoolean("stream_adaptive_resolution", false)

    /** "auto", "local" or "remote". */
    val connection: String get() = prefs.getString("stream_connection", "auto") ?: "auto"

    /** 0 attaches as many gamepads as are connected, otherwise 1..4. */
    val gamepadCount: Int get() = prefs.getInt("stream_gamepad_count", 0)
    val keepGamepads: Boolean get() = prefs.getBoolean("stream_keep_gamepads", false)
    val gamepadVibration: Boolean get() = prefs.getBoolean("stream_gamepad_vibration", true)

    /** Frame rate, latency and decoder stats over the stream. */
    val perfOverlay: Boolean get() = prefs.getBoolean("stream_perf_overlay", false)

    companion object {
        const val PREFS_NAME = "rift_settings"

        /** Width and height for a fixed resolution, or null for "device". */
        fun sizeOf(resolution: String): Pair<Int, Int>? {
            val parts = resolution.split('x')
            val width = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val height = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return width to height
        }

        /** Moonlight's default bitrate table, scaled by frame rate (more gently above 60, as Moonlight does). */
        fun autoBitrateKbps(width: Int, height: Int, fps: Int): Int {
            val pixels = width.toLong() * height
            val base = when {
                pixels <= 1280L * 720 -> 5000
                pixels <= 1920L * 1080 -> 10000
                pixels <= 2560L * 1600 -> 20000
                else -> 40000
            }
            val rate = fps.coerceAtLeast(30).toFloat()
            val factor = (if (rate <= 60f) rate else kotlin.math.sqrt(rate / 60f) * 60f) / 30f
            return (base * factor).toInt().coerceAtMost(150000)
        }
    }
}

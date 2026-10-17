package dev.rift.launcher.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Rift's own settings, for what GameNative's [app.gamenative.PrefManager] does not have: PC streaming
 * (Artemis) and the emulator folders. Plain SharedPreferences so the streaming and emulator code can read
 * the values without touching the UI.
 *
 * Stream values are the ones Artemis' stream configuration takes (see StreamConfiguration and
 * DecoderConfiguration in the artemis-core module): resolution, frame rate, bitrate, video format, HDR,
 * colour range, frame pacing, audio layout and the gamepad options.
 */
/** One configured emulator platform: [platformId] is an [EmulatorCatalog] id, [emulatorPackage] the app that plays it. */
data class EmulatorPlatform(val platformId: String, val emulatorPackage: String, val folderUri: String)

class RiftPrefs private constructor(
    private val prefs: SharedPreferences,
    private val homePrefs: SharedPreferences,
) {

    // --- PC streaming (Artemis) ---

    /** "device" for the tablet's own screen size, otherwise "WIDTHxHEIGHT" such as "1920x1080". */
    var streamResolution: String
        get() = prefs.getString(STREAM_RESOLUTION, "device") ?: "device"
        set(value) = prefs.edit().putString(STREAM_RESOLUTION, value).apply()

    var streamFps: Int
        get() = prefs.getInt(STREAM_FPS, 60)
        set(value) = prefs.edit().putInt(STREAM_FPS, value).apply()

    /** Megabits per second; 0 lets the app choose from resolution and frame rate. */
    var streamBitrateMbps: Int
        get() = prefs.getInt(STREAM_BITRATE, 0)
        set(value) = prefs.edit().putInt(STREAM_BITRATE, value).apply()

    /** "auto", "h264", "hevc" or "av1" (Artemis' FormatOption AUTO, FORCE_H264, FORCE_HEVC, FORCE_AV1). */
    var streamCodec: String
        get() = prefs.getString(STREAM_CODEC, "auto") ?: "auto"
        set(value) = prefs.edit().putString(STREAM_CODEC, value).apply()

    var streamHdr: Boolean
        get() = prefs.getBoolean(STREAM_HDR, false)
        set(value) = prefs.edit().putBoolean(STREAM_HDR, value).apply()

    var streamFullRange: Boolean
        get() = prefs.getBoolean(STREAM_FULL_RANGE, false)
        set(value) = prefs.edit().putBoolean(STREAM_FULL_RANGE, value).apply()

    var streamUltraLowLatency: Boolean
        get() = prefs.getBoolean(STREAM_ULTRA_LOW_LATENCY, false)
        set(value) = prefs.edit().putBoolean(STREAM_ULTRA_LOW_LATENCY, value).apply()

    /** Artemis frame pacing: 0 minimum latency, 1 balanced, 2 cap FPS, 3 maximum smoothness. */
    var streamFramePacing: Int
        get() = prefs.getInt(STREAM_FRAME_PACING, 1)
        set(value) = prefs.edit().putInt(STREAM_FRAME_PACING, value).apply()

    /** "stereo", "5.1" or "7.1". */
    var streamAudio: String
        get() = prefs.getString(STREAM_AUDIO, "stereo") ?: "stereo"
        set(value) = prefs.edit().putString(STREAM_AUDIO, value).apply()

    /** Keep the game's sound playing on the PC as well. */
    var streamPlayLocalAudio: Boolean
        get() = prefs.getBoolean(STREAM_LOCAL_AUDIO, false)
        set(value) = prefs.edit().putBoolean(STREAM_LOCAL_AUDIO, value).apply()

    /** Let the host adjust the game's own graphics settings to the stream (SOPS). */
    var streamOptimizeGameSettings: Boolean
        get() = prefs.getBoolean(STREAM_SOPS, true)
        set(value) = prefs.edit().putBoolean(STREAM_SOPS, value).apply()

    /** Apollo/Vibepollo virtual display: the host makes a screen of exactly the stream's size. */
    var streamVirtualDisplay: Boolean
        get() = prefs.getBoolean(STREAM_VIRTUAL_DISPLAY, false)
        set(value) = prefs.edit().putBoolean(STREAM_VIRTUAL_DISPLAY, value).apply()

    var streamAdaptiveResolution: Boolean
        get() = prefs.getBoolean(STREAM_ADAPTIVE_RESOLUTION, false)
        set(value) = prefs.edit().putBoolean(STREAM_ADAPTIVE_RESOLUTION, value).apply()

    /** "auto", "local" or "remote" (StreamConfiguration STREAM_CFG_AUTO, LOCAL, REMOTE). */
    var streamConnection: String
        get() = prefs.getString(STREAM_CONNECTION, "auto") ?: "auto"
        set(value) = prefs.edit().putString(STREAM_CONNECTION, value).apply()

    /** 0 = as many as are connected, otherwise 1 to 4 gamepads shown to the PC. */
    var streamGamepadCount: Int
        get() = prefs.getInt(STREAM_GAMEPADS, 0)
        set(value) = prefs.edit().putInt(STREAM_GAMEPADS, value).apply()

    var streamKeepGamepads: Boolean
        get() = prefs.getBoolean(STREAM_KEEP_GAMEPADS, false)
        set(value) = prefs.edit().putBoolean(STREAM_KEEP_GAMEPADS, value).apply()

    /** Rumble from the game is passed to the gamepad. */
    var streamGamepadVibration: Boolean
        get() = prefs.getBoolean(STREAM_GAMEPAD_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(STREAM_GAMEPAD_VIBRATION, value).apply()

    var streamPerfOverlay: Boolean
        get() = prefs.getBoolean(STREAM_PERF_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(STREAM_PERF_OVERLAY, value).apply()

    // --- Android games ---

    /**
     * Package names of the installed Android games the user added. The home screen shows an
     * "Android games" tab only while this is not empty, like every other source. Stored where the home
     * screen reads it (file "rift_prefs", key "android_games_packages").
     */
    var androidGamePackages: Set<String>
        get() = homePrefs.getStringSet(ANDROID_GAMES, emptySet())?.toSet() ?: emptySet()
        set(value) = homePrefs.edit().putStringSet(ANDROID_GAMES, value.toSet()).apply()

    // --- Emulator platforms (platform -> emulator -> ROM folder) ---

    /**
     * Platforms the user added, each with the emulator app that plays it and the folder with its games.
     * The home screen shows one tab per entry, like Beacon's platforms.
     */
    var emulatorPlatforms: List<EmulatorPlatform>
        get() = runCatching {
            val array = org.json.JSONArray(prefs.getString(EMULATOR_PLATFORMS, "[]"))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                EmulatorPlatform(o.getString("platform"), o.optString("emulator"), o.optString("folder"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = org.json.JSONArray()
            value.forEach {
                array.put(
                    org.json.JSONObject()
                        .put("platform", it.platformId)
                        .put("emulator", it.emulatorPackage)
                        .put("folder", it.folderUri),
                )
            }
            prefs.edit().putString(EMULATOR_PLATFORMS, array.toString()).apply()
        }

    // --- Top bar and buttons ---

    /** 24-hour clock in the top bar; false shows 12-hour time. */
    var clock24h: Boolean
        get() = prefs.getBoolean(CLOCK_24H, true)
        set(value) = prefs.edit().putBoolean(CLOCK_24H, value).apply()

    var showBattery: Boolean
        get() = prefs.getBoolean(SHOW_BATTERY, true)
        set(value) = prefs.edit().putBoolean(SHOW_BATTERY, value).apply()

    var showNetwork: Boolean
        get() = prefs.getBoolean(SHOW_NETWORK, true)
        set(value) = prefs.edit().putBoolean(SHOW_NETWORK, value).apply()

    /**
     * KeyEvent key code of the gamepad button that opens the right-hand panel on the home screen
     * (file "rift_prefs", key "quick_access_keycode"). None by default: holding Select always works,
     * and Android keeps Guide for itself on some devices.
     */
    var quickAccessKeyCode: Int
        get() = homePrefs.getInt(QUICK_ACCESS, dev.rift.launcher.home.RiftControls.DEFAULT_QUICK_ACCESS)
        set(value) = homePrefs.edit().putInt(QUICK_ACCESS, value).apply()

    // --- Emulator folders ---

    /** Content URI of the folder the user picked, empty when none. */
    var romsFolderUri: String
        get() = prefs.getString(ROMS_URI, "") ?: ""
        set(value) = prefs.edit().putString(ROMS_URI, value).apply()

    var biosFolderUri: String
        get() = prefs.getString(BIOS_URI, "") ?: ""
        set(value) = prefs.edit().putString(BIOS_URI, value).apply()

    /** The ES-DE folder whose systems and emulator choices Rift imports. */
    var esDeFolderUri: String
        get() = prefs.getString(ESDE_URI, "") ?: ""
        set(value) = prefs.edit().putString(ESDE_URI, value).apply()

    companion object {
        private const val FILE = "rift_settings"

        private const val STREAM_RESOLUTION = "stream_resolution"
        private const val STREAM_FPS = "stream_fps"
        private const val STREAM_BITRATE = "stream_bitrate_mbps"
        private const val STREAM_CODEC = "stream_codec"
        private const val STREAM_HDR = "stream_hdr"
        private const val STREAM_FULL_RANGE = "stream_full_range"
        private const val STREAM_ULTRA_LOW_LATENCY = "stream_ultra_low_latency"
        private const val STREAM_FRAME_PACING = "stream_frame_pacing"
        private const val STREAM_AUDIO = "stream_audio"
        private const val STREAM_LOCAL_AUDIO = "stream_play_local_audio"
        private const val STREAM_SOPS = "stream_optimize_game_settings"
        private const val STREAM_VIRTUAL_DISPLAY = "stream_virtual_display"
        private const val STREAM_ADAPTIVE_RESOLUTION = "stream_adaptive_resolution"
        private const val STREAM_CONNECTION = "stream_connection"
        private const val STREAM_GAMEPADS = "stream_gamepad_count"
        private const val STREAM_KEEP_GAMEPADS = "stream_keep_gamepads"
        private const val STREAM_GAMEPAD_VIBRATION = "stream_gamepad_vibration"
        private const val STREAM_PERF_OVERLAY = "stream_perf_overlay"

        private const val HOME_FILE = "rift_prefs"
        private const val ANDROID_GAMES = "android_games_packages"
        private const val EMULATOR_PLATFORMS = "emulator_platforms"
        private const val CLOCK_24H = "clock_24h"
        private const val SHOW_BATTERY = "show_battery"
        private const val SHOW_NETWORK = "show_network"
        private const val QUICK_ACCESS = "quick_access_keycode"
        private const val ROMS_URI = "emulators_roms_uri"
        private const val BIOS_URI = "emulators_bios_uri"
        private const val ESDE_URI = "emulators_esde_uri"

        fun of(context: Context): RiftPrefs =
            context.applicationContext.let {
                RiftPrefs(
                    it.getSharedPreferences(FILE, Context.MODE_PRIVATE),
                    it.getSharedPreferences(HOME_FILE, Context.MODE_PRIVATE),
                )
            }
    }
}

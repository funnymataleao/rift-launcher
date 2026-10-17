package dev.rift.launcher.cloud

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import kotlin.math.abs

/**
 * Where xbox.com's page keeps a setting: the page-wide ones, read when the page loads, or the stream's,
 * which also change while a game plays. The names are the ones the bundled script stores them under.
 */
enum class CloudStore(val storageKey: String) {
    Page("BetterXcloud"),
    Stream("BetterXcloud.Stream"),
}

/** One answer of a choice: a string resource, or [text] for names that are not translated (720p, 16:9). */
class CloudOpt(val value: Any, @StringRes val res: Int = 0, val text: String = "") {
    /** With both, [res] is a pattern that takes [text] (a number with its unit). */
    @Composable
    fun label(): String = when {
        res != 0 && text.isNotEmpty() -> stringResource(res, text)
        res != 0 -> stringResource(res)
        else -> text
    }
}

/** A setting of the cloud stream, shown in Settings → Cloud streaming and kept by Rift. */
sealed class CloudSetting(
    val key: String,
    val store: CloudStore,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val icon: ImageVector,
) {
    class Toggle(key: String, store: CloudStore, title: Int, icon: ImageVector, val default: Boolean, summary: Int = 0) :
        CloudSetting(key, store, title, summary, icon)

    /** One of [options]; a short list shows as chips, a long one opens a list. */
    class Choice(
        key: String,
        store: CloudStore,
        title: Int,
        icon: ImageVector,
        val options: List<CloudOpt>,
        val default: Any,
        summary: Int = 0,
        val chips: Boolean = options.size <= 4,
    ) : CloudSetting(key, store, title, summary, icon)

    /** Any number of [options]. */
    class Multi(key: String, store: CloudStore, title: Int, icon: ImageVector, val options: List<CloudOpt>, val default: List<String>, summary: Int = 0) :
        CloudSetting(key, store, title, summary, icon)
}

class CloudGroup(@StringRes val title: Int, val settings: List<CloudSetting>)

/**
 * Rift's own copy of the cloud stream's settings. Xbox.com's page reads them from its local storage, so
 * before the page's scripts run Rift writes its values there ([pageScript]); a change made in the
 * in-game menu is applied to the running stream at once ([liveScript]) and kept here for the next game.
 * Values the page already had from before Rift kept them are taken over once ([importFromPage]).
 */
object CloudPlaySettings {
    private const val PREFS = "rift_cloud_play"
    private const val KEY_VALUES = "values"
    private const val KEY_IMPORTED = "imported"

    /** Languages xCloud can run a game in, by their own names. */
    private val GAME_LANGUAGES = listOf(
        "ar-SA" to "العربية", "bg-BG" to "Български", "cs-CZ" to "čeština", "da-DK" to "dansk", "de-DE" to "Deutsch",
        "el-GR" to "Ελληνικά", "en-GB" to "English (UK)", "en-US" to "English (US)", "es-ES" to "español (España)",
        "es-MX" to "español (Latinoamérica)", "fi-FI" to "suomi", "fr-FR" to "français", "he-IL" to "עברית",
        "hu-HU" to "magyar", "it-IT" to "italiano", "ja-JP" to "日本語", "ko-KR" to "한국어", "nb-NO" to "norsk bokmål",
        "nl-NL" to "Nederlands", "pl-PL" to "polski", "pt-BR" to "português (Brasil)", "pt-PT" to "português (Portugal)",
        "ro-RO" to "Română", "ru-RU" to "русский", "sk-SK" to "slovenčina", "sv-SE" to "svenska", "th-TH" to "ไทย",
        "tr-TR" to "Türkçe", "zh-CN" to "中文(简体)", "zh-TW" to "中文 (繁體)",
    )

    private val P = CloudStore.Page
    private val S = CloudStore.Stream

    private fun percent(from: Int, to: Int, step: Int) = (from..to step step).map { CloudOpt(it, text = "$it%") }
    private val default = CloudOpt("default", R.string.rift_cloud_opt_default)
    private val off = CloudOpt("off", R.string.rift_settings_off)

    /** Megabits as the page counts them (1 024 000 bits); 0 is no limit. */
    private const val MEGABIT = 1_024_000

    val stats = CloudSetting.Toggle("stats.showWhenPlaying", S, R.string.rift_cloud_set_stats_show, Icons.Rounded.Insights, false)
    val maxFps = CloudSetting.Choice(
        "video.maxFps", S, R.string.rift_cloud_set_max_fps, Icons.Rounded.Speed,
        (10..50 step 10).map { CloudOpt(it, text = "$it") } + CloudOpt(60, R.string.rift_cloud_opt_unlimited),
        60, chips = true,
    )
    val sharpness = CloudSetting.Choice(
        "video.processing.sharpness", S, R.string.rift_cloud_set_sharpness, Icons.Rounded.CenterFocusStrong,
        listOf(CloudOpt(0, R.string.rift_settings_off)) + (1..10).map { CloudOpt(it, text = "$it") }, 0,
    )
    val brightness = CloudSetting.Choice("video.brightness", S, R.string.rift_cloud_set_brightness, Icons.Rounded.BrightnessMedium, percent(50, 150, 10), 100)
    val contrast = CloudSetting.Choice("video.contrast", S, R.string.rift_cloud_set_contrast, Icons.Rounded.Contrast, percent(50, 150, 10), 100)
    val saturation = CloudSetting.Choice("video.saturation", S, R.string.rift_cloud_set_saturation, Icons.Rounded.InvertColors, percent(50, 150, 10), 100)
    val ratio = CloudSetting.Choice(
        "video.ratio", S, R.string.rift_cloud_set_ratio, Icons.Rounded.AspectRatio,
        listOf("16:9", "16:10", "18:9", "20:9", "21:9", "3:2", "4:3", "5:4").map { CloudOpt(it, text = it) } +
            CloudOpt("fill", R.string.rift_cloud_opt_stretch),
        "16:9", summary = R.string.rift_cloud_set_ratio_summary,
    )
    val volumeControl = CloudSetting.Toggle(
        "audio.volume.booster.enabled", P, R.string.rift_cloud_set_volume_control, Icons.Rounded.Tune, false,
        summary = R.string.rift_cloud_set_volume_control_summary,
    )
    val volume = CloudSetting.Choice(
        "audio.volume", S, R.string.rift_cloud_set_volume, Icons.Rounded.VolumeUp,
        listOf(0, 25, 50, 75, 100, 125, 150, 200, 300, 400, 500, 600).map { CloudOpt(it, text = "$it%") }, 100,
    )

    val renderer = CloudSetting.Choice(
        "video.player.type", S, R.string.rift_cloud_set_renderer, Icons.Rounded.Memory,
        listOf(default, CloudOpt("webgl2", text = "WebGL 2"), CloudOpt("webgpu", text = "WebGPU")), "default",
    )
    val power = CloudSetting.Choice(
        "video.player.powerPreference", S, R.string.rift_cloud_set_power, Icons.Rounded.Memory,
        listOf(default, CloudOpt("low-power", R.string.rift_cloud_opt_battery), CloudOpt("high-performance", R.string.rift_cloud_opt_performance)),
        "default", summary = R.string.rift_cloud_set_power_summary,
    )
    val clarity = CloudSetting.Choice(
        "video.processing", S, R.string.rift_cloud_set_clarity, Icons.Rounded.CenterFocusStrong,
        listOf(CloudOpt("usm", R.string.rift_cloud_opt_usm), CloudOpt("cas", text = "AMD FidelityFX CAS")), "usm",
    )
    val clarityMode = CloudSetting.Choice(
        "video.processing.mode", S, R.string.rift_cloud_set_clarity_mode, Icons.Rounded.Tune,
        listOf(CloudOpt("performance", R.string.rift_cloud_opt_performance), CloudOpt("quality", R.string.rift_cloud_opt_quality)), "performance",
    )
    val position = CloudSetting.Choice(
        "video.position", S, R.string.rift_cloud_set_position, Icons.Rounded.AspectRatio,
        listOf(
            CloudOpt("top", R.string.rift_cloud_opt_top),
            CloudOpt("top-half", R.string.rift_cloud_opt_top_half),
            CloudOpt("center", R.string.rift_cloud_opt_center),
            CloudOpt("bottom-half", R.string.rift_cloud_opt_bottom_half),
            CloudOpt("bottom", R.string.rift_cloud_opt_bottom),
        ),
        "center",
    )
    val deviceVibration = CloudSetting.Choice(
        "deviceVibration.mode", S, R.string.rift_cloud_set_device_vibration, Icons.Rounded.Vibration,
        listOf(off, CloudOpt("on", R.string.rift_settings_on), CloudOpt("auto", R.string.rift_cloud_opt_no_gamepad)), "off",
        summary = R.string.rift_cloud_set_device_vibration_summary,
    )
    val vibrationStrength = CloudSetting.Choice("deviceVibration.intensity", S, R.string.rift_cloud_set_vibration_strength, Icons.Rounded.Vibration, percent(10, 100, 10), 50)
    val pollingRate = CloudSetting.Choice(
        "controller.pollingRate", S, R.string.rift_cloud_set_polling, Icons.Rounded.Gamepad,
        (4..60 step 4).map { CloudOpt(it, R.string.rift_cloud_opt_hz, "%.0f".format(1000f / it)) }, 4,
        summary = R.string.rift_cloud_set_polling_summary,
    )
    val localCoop = CloudSetting.Toggle("localCoOp.enabled", S, R.string.rift_cloud_set_local_coop, Icons.Rounded.Groups, false)
    val statsPosition = CloudSetting.Choice(
        "stats.position", S, R.string.rift_cloud_set_stats_position, Icons.Rounded.AspectRatio,
        listOf(CloudOpt("top-left", R.string.rift_cloud_opt_left), CloudOpt("top-center", R.string.rift_cloud_opt_center), CloudOpt("top-right", R.string.rift_cloud_opt_right)),
        "top-right",
    )

    /** The game's tab of the settings: the stream, the picture, the sound, the gamepad, the stats. */
    val streamGroups = listOf(
        CloudGroup(
            R.string.rift_cloud_group_quality,
            listOf(
                CloudSetting.Choice(
                    "stream.video.resolution", P, R.string.rift_cloud_set_resolution, Icons.Rounded.Videocam,
                    listOf(CloudOpt("auto", R.string.rift_settings_auto), CloudOpt("720p", text = "720p"), CloudOpt("1080p", text = "1080p"), CloudOpt("1080p-hq", text = "1080p HQ")),
                    "auto",
                ),
                CloudSetting.Choice(
                    "stream.video.codecProfile", P, R.string.rift_cloud_set_codec, Icons.Rounded.HighQuality,
                    listOf(default, CloudOpt("low", R.string.rift_cloud_opt_low), CloudOpt("normal", R.string.rift_cloud_opt_normal), CloudOpt("high", R.string.rift_cloud_opt_high)),
                    "default", summary = R.string.rift_cloud_set_codec_summary,
                ),
                CloudSetting.Choice(
                    "stream.video.maxBitrate", P, R.string.rift_cloud_set_bitrate, Icons.Rounded.Speed,
                    // The page's own scale ends at 15: that is its "no limit", the most Xbox's server sends.
                    listOf(3, 5, 8, 10, 12, 14).map { CloudOpt(it * MEGABIT, text = "$it") } + CloudOpt(0, R.string.rift_cloud_opt_server_max),
                    0, summary = R.string.rift_cloud_set_bitrate_summary, chips = true,
                ),
                CloudSetting.Toggle("stream.video.preventResolutionDrops", P, R.string.rift_cloud_set_prevent_drops, Icons.Rounded.Layers, false),
                CloudSetting.Toggle(
                    "stream.video.combineAudio", P, R.string.rift_cloud_set_combine_audio, Icons.Rounded.GraphicEq, false,
                    summary = R.string.rift_cloud_set_combine_audio_summary,
                ),
                CloudSetting.Choice(
                    "server.bypassRestriction", P, R.string.rift_cloud_set_bypass, Icons.Rounded.Public,
                    listOf(
                        off,
                        CloudOpt("br", R.string.rift_cloud_opt_brazil),
                        CloudOpt("jp", R.string.rift_cloud_opt_japan),
                        CloudOpt("kr", R.string.rift_cloud_opt_korea),
                        CloudOpt("pl", R.string.rift_cloud_opt_poland),
                        CloudOpt("us", R.string.rift_cloud_opt_usa),
                    ),
                    "off", summary = R.string.rift_cloud_set_bypass_summary,
                ),
                CloudSetting.Toggle("server.ipv6.prefer", P, R.string.rift_cloud_set_ipv6, Icons.Rounded.Router, false),
                CloudSetting.Choice(
                    "stream.locale", P, R.string.rift_cloud_set_game_language, Icons.Rounded.Translate,
                    listOf(default) + GAME_LANGUAGES.map { (code, name) -> CloudOpt(code, text = name) }, "default",
                ),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_picture,
            listOf(
                renderer,
                power,
                clarity,
                clarityMode,
                sharpness,
                maxFps,
                ratio,
                position,
                brightness,
                contrast,
                saturation,
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_sound,
            listOf(
                volumeControl,
                volume,
                CloudSetting.Toggle("audio.mic.onPlaying", P, R.string.rift_cloud_set_mic, Icons.Rounded.Mic, false),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_gamepad,
            listOf(
                deviceVibration,
                vibrationStrength,
                pollingRate,
                localCoop,
                CloudSetting.Toggle("ui.controllerStatus.show", P, R.string.rift_cloud_set_controller_status, Icons.Rounded.SportsEsports, true),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_stats,
            listOf(
                stats,
                CloudSetting.Toggle(
                    "stats.quickGlance.enabled", S, R.string.rift_cloud_set_quick_glance, Icons.Rounded.Insights, true,
                    summary = R.string.rift_cloud_set_quick_glance_summary,
                ),
                CloudSetting.Multi(
                    "stats.items", S, R.string.rift_cloud_set_stats_items, Icons.Rounded.Insights,
                    listOf(
                        CloudOpt("time", R.string.rift_cloud_stat_time),
                        CloudOpt("play", R.string.rift_cloud_stat_play),
                        CloudOpt("batt", R.string.rift_cloud_stat_battery),
                        CloudOpt("ping", R.string.rift_cloud_stat_ping),
                        CloudOpt("jit", R.string.rift_cloud_stat_jitter),
                        CloudOpt("res", R.string.rift_cloud_stat_resolution),
                        CloudOpt("fps", text = "FPS"),
                        CloudOpt("btr", R.string.rift_cloud_stat_bitrate),
                        CloudOpt("dt", R.string.rift_cloud_stat_decode),
                        CloudOpt("pl", R.string.rift_cloud_stat_packets_lost),
                        CloudOpt("fl", R.string.rift_cloud_stat_frames_lost),
                        CloudOpt("dl", R.string.rift_cloud_stat_downloaded),
                        CloudOpt("ul", R.string.rift_cloud_stat_uploaded),
                    ),
                    listOf("ping", "fps", "btr", "dt", "pl", "fl"),
                ),
                statsPosition,
                CloudSetting.Choice(
                    "stats.textSize", S, R.string.rift_cloud_set_stats_text, Icons.Rounded.FormatSize,
                    listOf(CloudOpt("0.9rem", R.string.rift_cloud_opt_small), CloudOpt("1.0rem", R.string.rift_cloud_opt_normal_size), CloudOpt("1.1rem", R.string.rift_cloud_opt_large)),
                    "0.9rem",
                ),
                CloudSetting.Choice("stats.opacity.all", S, R.string.rift_cloud_set_stats_opacity, Icons.Rounded.Opacity, percent(50, 100, 10), 80, chips = true),
                CloudSetting.Choice("stats.opacity.background", S, R.string.rift_cloud_set_stats_background, Icons.Rounded.Opacity, percent(0, 100, 10), 100),
                CloudSetting.Toggle(
                    "stats.colors", S, R.string.rift_cloud_set_stats_colors, Icons.Rounded.InvertColors, false,
                    summary = R.string.rift_cloud_set_stats_colors_summary,
                ),
            ),
        ),
    )

    /** The interface tab: the game's loading, xbox.com's own screens, touch controls and the rest. */
    val pageGroups = listOf(
        CloudGroup(
            R.string.rift_cloud_group_loading,
            listOf(
                CloudSetting.Toggle("ui.splashVideo.skip", P, R.string.rift_cloud_set_skip_splash, Icons.Rounded.Movie, false),
                CloudSetting.Toggle("loadingScreen.gameArt.show", P, R.string.rift_cloud_set_loading_art, Icons.Rounded.Image, true),
                CloudSetting.Toggle("loadingScreen.waitTime.show", P, R.string.rift_cloud_set_wait_time, Icons.Rounded.HourglassEmpty, true),
                CloudSetting.Choice(
                    "loadingScreen.rocket", P, R.string.rift_cloud_set_rocket, Icons.Rounded.RocketLaunch,
                    listOf(CloudOpt("show", R.string.rift_cloud_opt_always), CloudOpt("hide-queue", R.string.rift_cloud_opt_hide_in_queue), CloudOpt("hide", R.string.rift_cloud_opt_never)),
                    "show",
                ),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_interface,
            listOf(
                CloudSetting.Choice(
                    "ui.layout", P, R.string.rift_cloud_set_layout, Icons.Rounded.ViewQuilt,
                    listOf(default, CloudOpt("normal", R.string.rift_cloud_opt_normal_layout), CloudOpt("tv", R.string.rift_cloud_opt_tv)), "default",
                ),
                CloudSetting.Choice(
                    "ui.theme", P, R.string.rift_cloud_set_theme, Icons.Rounded.DarkMode,
                    listOf(default, CloudOpt("dark-oled", text = "OLED")), "default",
                ),
                CloudSetting.Toggle("ui.controllerFriendly", P, R.string.rift_cloud_set_controller_friendly, Icons.Rounded.SportsEsports, true),
                CloudSetting.Toggle("ui.reduceAnimations", P, R.string.rift_cloud_set_reduce_animations, Icons.Rounded.Animation, false),
                CloudSetting.Toggle("ui.hideScrollbar", P, R.string.rift_cloud_set_hide_scrollbar, Icons.Rounded.VisibilityOff, false),
                CloudSetting.Toggle("ui.streamMenu.simplify", P, R.string.rift_cloud_set_simplify_menu, Icons.Rounded.Tune, false),
                CloudSetting.Toggle(
                    "ui.systemMenu.hideHandle", P, R.string.rift_cloud_set_hide_handle, Icons.Rounded.VisibilityOff, false,
                    summary = R.string.rift_cloud_set_hide_handle_summary,
                ),
                CloudSetting.Toggle("ui.feedbackDialog.disabled", P, R.string.rift_cloud_set_no_feedback, Icons.Rounded.Feedback, false),
                CloudSetting.Toggle("ui.gameCard.waitTime.show", P, R.string.rift_cloud_set_card_wait_time, Icons.Rounded.Timer, true),
                CloudSetting.Choice(
                    "ui.imageQuality", P, R.string.rift_cloud_set_image_quality, Icons.Rounded.Image,
                    (10..80 step 10).map { CloudOpt(it, text = "$it%") } + CloudOpt(90, R.string.rift_cloud_opt_default), 90,
                ),
                CloudSetting.Multi(
                    "ui.hideSections", P, R.string.rift_cloud_set_hide_sections, Icons.Rounded.VisibilityOff,
                    listOf(
                        CloudOpt("news", R.string.rift_cloud_section_news),
                        CloudOpt("friends", R.string.rift_cloud_section_friends),
                        CloudOpt("native-mkb", R.string.rift_cloud_section_mkb),
                        CloudOpt("touch", R.string.rift_cloud_section_touch),
                        CloudOpt("most-popular", R.string.rift_cloud_section_popular),
                        CloudOpt("byog", R.string.rift_cloud_section_byog),
                        CloudOpt("recently-added", R.string.rift_cloud_section_recent),
                        CloudOpt("leaving-soon", R.string.rift_cloud_section_leaving),
                        CloudOpt("genres", R.string.rift_cloud_section_genres),
                        CloudOpt("all-games", R.string.rift_cloud_section_all),
                    ),
                    emptyList(),
                ),
                CloudSetting.Choice(
                    "gameBar.position", P, R.string.rift_cloud_set_game_bar, Icons.Rounded.ViewQuilt,
                    listOf(off, CloudOpt("bottom-left", R.string.rift_cloud_opt_bottom_left), CloudOpt("bottom-right", R.string.rift_cloud_opt_bottom_right)),
                    "bottom-left", summary = R.string.rift_cloud_set_game_bar_summary,
                ),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_touch,
            listOf(
                CloudSetting.Choice(
                    "touchController.mode", P, R.string.rift_cloud_set_touch, Icons.Rounded.TouchApp,
                    listOf(default, off, CloudOpt("all", R.string.rift_cloud_opt_all_games)), "all",
                ),
                CloudSetting.Toggle("touchController.autoOff", P, R.string.rift_cloud_set_touch_auto_off, Icons.Rounded.TouchApp, false),
                CloudSetting.Choice("touchController.opacity.default", P, R.string.rift_cloud_set_touch_opacity, Icons.Rounded.Opacity, percent(10, 100, 10), 100),
                CloudSetting.Choice(
                    "touchController.style.standard", P, R.string.rift_cloud_set_touch_style, Icons.Rounded.InvertColors,
                    listOf(default, CloudOpt("white", R.string.rift_cloud_opt_white), CloudOpt("muted", R.string.rift_cloud_opt_muted)), "default",
                ),
                CloudSetting.Choice(
                    "touchController.style.custom", P, R.string.rift_cloud_set_touch_style_custom, Icons.Rounded.InvertColors,
                    listOf(default, CloudOpt("muted", R.string.rift_cloud_opt_muted)), "default",
                ),
            ),
        ),
        CloudGroup(
            R.string.rift_cloud_group_other,
            listOf(
                CloudSetting.Toggle("block.tracking", P, R.string.rift_cloud_set_block_tracking, Icons.Rounded.PrivacyTip, false),
                CloudSetting.Multi(
                    "block.features", P, R.string.rift_cloud_set_block_features, Icons.Rounded.Block,
                    listOf(
                        CloudOpt("chat", R.string.rift_cloud_feature_chat),
                        CloudOpt("friends", R.string.rift_cloud_feature_friends),
                        CloudOpt("notifications-invites", R.string.rift_cloud_feature_invites),
                        CloudOpt("notifications-achievements", R.string.rift_cloud_feature_achievements),
                        CloudOpt("remote-play", R.string.rift_cloud_feature_remote_play),
                    ),
                    emptyList(),
                ),
                CloudSetting.Toggle("screenshot.applyFilters", P, R.string.rift_cloud_set_screenshot_filters, Icons.Rounded.PhotoCamera, false),
                CloudSetting.Toggle(
                    "game.fortnite.forceConsole", P, R.string.rift_cloud_set_fortnite, Icons.Rounded.SportsEsports, false,
                    summary = R.string.rift_cloud_set_fortnite_summary,
                ),
            ),
        ),
    )

    private val all: List<CloudSetting> by lazy { (streamGroups + pageGroups).flatMap { it.settings } }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun stored(context: Context): JSONObject =
        runCatching { JSONObject(prefs(context).getString(KEY_VALUES, null) ?: "{}") }.getOrDefault(JSONObject())

    /** Every setting's value: Rift's, or the default when it was never changed. */
    fun values(context: Context): Map<String, Any> {
        val stored = stored(context)
        return all.associate { setting -> setting.key to (stored.opt(setting.key)?.let { decode(setting, it) } ?: defaultOf(setting)) }
    }

    fun value(context: Context, setting: CloudSetting): Any =
        stored(context).opt(setting.key)?.let { decode(setting, it) } ?: defaultOf(setting)

    fun set(context: Context, setting: CloudSetting, value: Any) {
        val stored = stored(context).put(setting.key, encode(value))
        prefs(context).edit().putString(KEY_VALUES, stored.toString()).apply()
    }

    private fun defaultOf(setting: CloudSetting): Any = when (setting) {
        is CloudSetting.Toggle -> setting.default
        is CloudSetting.Choice -> setting.default
        is CloudSetting.Multi -> setting.default
    }

    /** A stored or page value as the setting's own type, null when it is not one of its answers. */
    private fun decode(setting: CloudSetting, raw: Any): Any? = when (setting) {
        is CloudSetting.Toggle -> raw as? Boolean
        is CloudSetting.Choice -> {
            val values = setting.options.map { it.value }
            if (raw is Number && values.all { it is Int }) {
                // A value set with the page's own slider can fall between the steps: the nearest one.
                values.minByOrNull { abs((it as Int) - raw.toDouble()) }
            } else {
                values.firstOrNull { it == raw }
            }
        }
        is CloudSetting.Multi -> (raw as? JSONArray)?.let { array ->
            val known = setting.options.map { it.value }
            (0 until array.length()).map { array.optString(it) }.filter { it in known }
        }
    }

    private fun encode(value: Any): Any = if (value is List<*>) JSONArray(value) else value

    /** JavaScript run before the page's own: Rift's values go into the page's storage and its menus are hidden. */
    fun pageScript(context: Context): String {
        val stored = stored(context)
        val out = JSONObject()
        CloudStore.entries.forEach { out.put(it.storageKey, JSONObject()) }
        all.forEach { setting ->
            stored.opt(setting.key)?.let { decode(setting, it) }?.let { out.getJSONObject(setting.store.storageKey).put(setting.key, encode(it)) }
        }
        return """
            (function () {
              try {
                var values = $out;
                for (var name in values) {
                  var current = {};
                  try { current = JSON.parse(localStorage.getItem(name) || '{}') || {}; } catch (e) {}
                  for (var key in values[name]) current[key] = values[name][key];
                  localStorage.setItem(name, JSON.stringify(current));
                }
                // Settings for one game would win over Rift's; there is no way to make them any more.
                for (var i = localStorage.length - 1; i >= 0; i--) {
                  var item = localStorage.key(i);
                  if (/^BetterXcloud\.Stream\.\d+$/.test(item)) localStorage.removeItem(item);
                }
              } catch (e) { console.warn('[Rift] cloud settings not applied: ' + e); }
              var style = document.createElement('style');
              style.textContent = '.bx-header-settings-button { display: none !important; }';
              (document.head || document.documentElement).appendChild(style);
            })();
        """.trimIndent() + "\n"
    }

    /** Whether the page's earlier values still need to be taken over. */
    fun needsImport(context: Context) = !prefs(context).getBoolean(KEY_IMPORTED, false)

    /** Reads the page's stored settings; its result goes to [importFromPage]. */
    const val READ_PAGE_SCRIPT =
        "(function(){try{return JSON.stringify({p:localStorage.getItem('BetterXcloud'),s:localStorage.getItem('BetterXcloud.Stream')})}catch(e){return null}})()"

    /**
     * Takes over what was set in the page before Rift kept the settings, for those Rift has no value of
     * its own yet. [result] is what [READ_PAGE_SCRIPT] returned.
     */
    fun importFromPage(context: Context, result: String?) {
        val text = result?.let { runCatching { JSONArray("[$it]").opt(0) as? String }.getOrNull() } ?: return
        runCatching {
            val page = JSONObject(text)
            val sources = mapOf(
                CloudStore.Page to page.optStringOrNull("p"),
                CloudStore.Stream to page.optStringOrNull("s"),
            ).mapValues { (_, json) -> json?.let { runCatching { JSONObject(it) }.getOrNull() } }
            val stored = stored(context)
            var taken = 0
            all.forEach { setting ->
                if (stored.has(setting.key)) return@forEach
                val raw = sources[setting.store]?.opt(setting.key) ?: return@forEach
                decode(setting, raw)?.let {
                    stored.put(setting.key, encode(it))
                    taken++
                }
            }
            prefs(context).edit().putString(KEY_VALUES, stored.toString()).putBoolean(KEY_IMPORTED, true).apply()
            Timber.i("Cloud settings taken over from the page: %d", taken)
        }.onFailure { Timber.w(it, "Cloud settings not taken over") }
    }

    private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)

    /**
     * JavaScript that changes a stream setting while the game plays: the page saves it and redraws the
     * picture (or sets the volume, or the frame rate) right away.
     */
    fun liveScript(setting: CloudSetting, value: Any): String {
        val json = JSONArray().put(encode(value)).toString()
        val apply = when (setting.key) {
            stats.key -> "var s = StreamStats.getInstance(); if (v) { if (s.isHidden()) s.start(); } else if (!s.isHidden()) { s.stop(); }"
            maxFps.key -> "limitVideoPlayerFps(v);"
            volume.key -> "SoundShortcut.setGainNodeVolume(v);"
            statsPosition.key -> "StreamStats.refreshStyles();"
            deviceVibration.key, vibrationStrength.key, pollingRate.key -> "StreamSettings.refreshControllerSettings();"
            localCoop.key -> "if (BxExposed.toggleLocalCoOp) BxExposed.toggleLocalCoOp(v);"
            else -> if (setting.key.startsWith("video.")) "updateVideoPlayer();" else ""
        }
        return "(function(){try{var v=$json[0];setGamePref(-1,'${setting.key}',v,'direct');$apply return 'ok'}catch(e){return String(e)}})()"
    }
}

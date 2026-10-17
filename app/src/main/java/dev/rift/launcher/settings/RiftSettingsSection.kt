package dev.rift.launcher.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector
import app.gamenative.R

/** Headings the section list is split under, like the groups in macOS System Settings. */
enum class RiftSettingsGroup(@get:StringRes val titleRes: Int) {
    Sources(R.string.rift_settings_group_sources),
    Rift(R.string.rift_settings_group_rift),
    Windows(R.string.rift_settings_group_windows),
    System(R.string.rift_settings_group_system),
}

/**
 * Sections of Rift's settings screen, in the order the left rail shows them.
 *
 * The logic follows Beacon Game Launcher: first the places games come from (Android games, PC games,
 * PC streaming, emulator platforms, cloud streaming), where adding a source is what makes its tab appear on the home
 * screen; then how Rift looks, sounds and is controlled; then GameNative's own settings for Windows games
 * (driver, display, launch), each on a page of its own. Rows read and write the same
 * [app.gamenative.PrefManager] values GameNative uses (and Rift's own [RiftPrefs] for what it does not have).
 */
enum class RiftSettingsSection(
    val group: RiftSettingsGroup,
    @get:StringRes val titleRes: Int,
    @get:StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    AndroidGames(RiftSettingsGroup.Sources, R.string.rift_settings_android_games, R.string.rift_settings_android_games_summary, Icons.Rounded.Android),
    Accounts(RiftSettingsGroup.Sources, R.string.rift_settings_accounts, R.string.rift_settings_accounts_summary, Icons.Rounded.ManageAccounts),
    Streaming(RiftSettingsGroup.Sources, R.string.rift_settings_streaming, R.string.rift_settings_streaming_summary, Icons.Rounded.Cast),
    Emulators(RiftSettingsGroup.Sources, R.string.rift_settings_emulators, R.string.rift_settings_emulators_summary, Icons.Rounded.SportsEsports),
    Cloud(RiftSettingsGroup.Sources, R.string.rift_settings_cloud, R.string.rift_settings_cloud_summary, Icons.Rounded.Cloud),

    Interface(RiftSettingsGroup.Rift, R.string.rift_settings_interface, R.string.rift_settings_interface_summary, Icons.Rounded.Palette),
    Tabs(RiftSettingsGroup.Rift, R.string.rift_settings_tabs, R.string.rift_settings_tabs_summary, Icons.Rounded.Tab),
    Controller(RiftSettingsGroup.Rift, R.string.rift_settings_controller, R.string.rift_settings_controller_summary, Icons.Rounded.Gamepad),
    Sound(RiftSettingsGroup.Rift, R.string.rift_settings_sound, R.string.rift_settings_sound_summary, Icons.Rounded.MusicNote),
    Home(RiftSettingsGroup.Rift, R.string.rift_settings_home, R.string.rift_settings_home_summary, Icons.Rounded.Home),
    Artwork(RiftSettingsGroup.Rift, R.string.rift_settings_artwork, R.string.rift_settings_artwork_summary, Icons.Rounded.Image),

    Graphics(RiftSettingsGroup.Windows, R.string.rift_settings_graphics, R.string.rift_settings_graphics_summary, Icons.Rounded.Videocam),
    Display(RiftSettingsGroup.Windows, R.string.rift_settings_display, R.string.rift_settings_display_summary, Icons.Rounded.DesktopWindows),
    Launch(RiftSettingsGroup.Windows, R.string.rift_settings_launch, R.string.rift_settings_launch_summary, Icons.Rounded.RocketLaunch),
    Downloads(RiftSettingsGroup.Windows, R.string.rift_settings_downloads, R.string.rift_settings_downloads_summary, Icons.Rounded.CloudDownload),

    Assistant(RiftSettingsGroup.System, R.string.rift_settings_assistant, R.string.rift_settings_assistant_summary, Icons.Rounded.AutoAwesome),
    Device(RiftSettingsGroup.System, R.string.rift_settings_device, R.string.rift_settings_device_summary, Icons.Rounded.Memory),
    About(RiftSettingsGroup.System, R.string.rift_settings_about, R.string.rift_settings_about_summary, Icons.Rounded.Info),
    Debug(RiftSettingsGroup.System, R.string.rift_settings_debug, R.string.rift_settings_debug_summary, Icons.Rounded.BugReport),
    ;

    companion object {
        /** A section another screen wants the settings to open on (the art picker sends people to the key). Read once. */
        @Volatile
        private var requested: RiftSettingsSection? = null

        fun request(section: RiftSettingsSection) {
            requested = section
        }

        internal fun takeRequested(): RiftSettingsSection? = requested.also { requested = null }
    }
}

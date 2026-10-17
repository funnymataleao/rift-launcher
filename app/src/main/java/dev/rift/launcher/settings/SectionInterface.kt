package dev.rift.launcher.settings

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.sync.FrontendSyncManager
import app.gamenative.ui.component.ACHIEVEMENT_NOTIFICATION_POSITION
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.screen.settings.FrontendSyncDialog
import app.gamenative.utils.IconSwitcher
import app.gamenative.utils.LocaleHelper
import com.posthog.PostHog
import dev.rift.launcher.home.RiftUninstalledMode
import dev.rift.launcher.home.UninstalledMode
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes

/** Language, app icon, status bar, the library and achievement notifications. */
@Composable
internal fun InterfaceSettings() {
    AccentSettings()
    TopBarGroup()
    LookGroup()
    LibraryGroup()
    GamePageGroup()
    AchievementsGroup()
}

/** Rift: what fills the game page's first screen and how the trailer behaves there. */
@Composable
private fun GamePageGroup() {
    val context = LocalContext.current
    val prefs = remember { dev.rift.launcher.settings.RiftPrefs.of(context) }
    val modes = listOf("trailer", "art", "cover")
    var mode by remember { mutableStateOf(prefs.gamePageBackground) }
    var sound by remember { mutableStateOf(prefs.gamePageTrailerSound) }
    var wifiOnly by remember { mutableStateOf(prefs.gamePageTrailerWifiOnly) }
    var loop by remember { mutableStateOf(prefs.gamePageTrailerLoop) }
    SettingsGroup(title = stringResource(R.string.rift_game_page_settings)) {
        ChoiceChipsRow(
            title = stringResource(R.string.rift_game_page_background),
            subtitle = stringResource(R.string.rift_game_page_background_summary),
            icon = Icons.Rounded.Theaters,
            options = listOf(
                stringResource(R.string.rift_game_page_bg_trailer),
                stringResource(R.string.rift_game_page_bg_art),
                stringResource(R.string.rift_game_page_bg_cover),
            ),
            selectedIndex = modes.indexOf(mode).coerceAtLeast(0),
            onSelect = {
                mode = modes[it]
                prefs.gamePageBackground = mode
            },
        )
        // Rift: two choices as pills (Denis 17.10); the same saved value as the page's Mute button.
        ChoiceChipsRow(
            title = stringResource(R.string.rift_game_page_trailer_sound),
            subtitle = stringResource(R.string.rift_game_page_trailer_sound_summary),
            icon = Icons.Rounded.VolumeUp,
            options = listOf(
                stringResource(R.string.rift_game_page_sound_on),
                stringResource(R.string.rift_game_page_sound_off),
            ),
            selectedIndex = if (sound) 0 else 1,
            onSelect = {
                sound = it == 0
                prefs.gamePageTrailerSound = sound
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_game_page_wifi_only),
            subtitle = stringResource(R.string.rift_game_page_wifi_only_summary),
            icon = Icons.Rounded.Wifi,
            checked = wifiOnly,
            enabled = mode == "trailer",
            onCheckedChange = {
                wifiOnly = it
                prefs.gamePageTrailerWifiOnly = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_game_page_loop),
            subtitle = stringResource(R.string.rift_game_page_loop_summary),
            icon = Icons.Rounded.Repeat,
            checked = loop,
            enabled = mode == "trailer",
            onCheckedChange = {
                loop = it
                prefs.gamePageTrailerLoop = it
            },
        )
    }
}

@Composable
private fun LookGroup() {
    val context = LocalContext.current

    // Language
    val languageCodes = remember { LocaleHelper.getSupportedLanguageCodes() }
    val languageNames = remember { LocaleHelper.getSupportedLanguageNames() }
    var languageDialog by rememberSaveable { mutableStateOf(false) }
    var pendingLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var restartingForLanguage by rememberSaveable { mutableStateOf(false) }

    // Status bar
    var hideStatusBar by rememberSaveable { mutableStateOf(PrefManager.hideStatusBarWhenNotInGame) }
    var pendingStatusBar by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var restartingForStatusBar by rememberSaveable { mutableStateOf(false) }

    var openWebLinks by rememberSaveable { mutableStateOf(PrefManager.openWebLinksExternally) }
    var warnBeforeExit by rememberSaveable { mutableStateOf(PrefManager.warnBeforeExit) }
    var iconVariant by rememberSaveable {
        mutableStateOf(if (PrefManager.useAltLauncherIcon || PrefManager.useAltNotificationIcon) 1 else 0)
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_look)) {
        LinkRow(
            title = stringResource(R.string.settings_language),
            icon = Icons.Rounded.Language,
            value = LocaleHelper.getLanguageDisplayName(PrefManager.appLanguage),
            onClick = { languageDialog = true },
        )
        IconStyleRow(
            selectedVariant = iconVariant,
            onSelect = { variant ->
                iconVariant = variant
                val alt = variant == 1
                PrefManager.useAltLauncherIcon = alt
                PrefManager.useAltNotificationIcon = alt
                IconSwitcher.applyLauncherIcon(context, alt)
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_hide_statusbar_title),
            subtitle = stringResource(R.string.settings_interface_hide_statusbar_subtitle),
            icon = Icons.Rounded.Fullscreen,
            checked = hideStatusBar,
            onCheckedChange = {
                hideStatusBar = it
                pendingStatusBar = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_external_links_title),
            subtitle = stringResource(R.string.settings_interface_external_links_subtitle),
            icon = Icons.Rounded.OpenInNew,
            checked = openWebLinks,
            onCheckedChange = {
                openWebLinks = it
                PrefManager.openWebLinksExternally = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_warn_before_exit_title),
            subtitle = stringResource(R.string.settings_interface_warn_before_exit_subtitle),
            icon = Icons.Rounded.ExitToApp,
            checked = warnBeforeExit,
            onCheckedChange = {
                warnBeforeExit = it
                PrefManager.warnBeforeExit = it
            },
        )
    }

    if (languageDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.settings_select_language),
            options = languageNames,
            selectedIndex = languageCodes.indexOf(PrefManager.appLanguage).coerceAtLeast(0),
            onSelect = { index ->
                val code = languageCodes[index]
                if (code != PrefManager.appLanguage) pendingLanguage = code
            },
            onDismiss = { languageDialog = false },
        )
    }
    pendingLanguage?.let { code ->
        RestartConfirmDialog(
            onConfirm = {
                PrefManager.appLanguage = code
                pendingLanguage = null
                restartingForLanguage = true
            },
            onDismiss = { pendingLanguage = null },
        )
    }
    if (restartingForLanguage) RestartingDialog(stringResource(R.string.settings_language_changing))

    pendingStatusBar?.let { value ->
        RestartConfirmDialog(
            onConfirm = {
                PrefManager.hideStatusBarWhenNotInGame = value
                pendingStatusBar = null
                restartingForStatusBar = true
            },
            onDismiss = {
                hideStatusBar = PrefManager.hideStatusBarWhenNotInGame
                pendingStatusBar = null
            },
        )
    }
    if (restartingForStatusBar) RestartingDialog(stringResource(R.string.settings_saving_restarting))
}

@Composable
private fun LibraryGroup() {
    var showHidden by rememberSaveable { mutableStateOf(PrefManager.showHiddenGamesByDefault) }
    var hideAi by rememberSaveable { mutableStateOf(PrefManager.hideAiFeatures) }
    var importAsSteam by rememberSaveable { mutableStateOf(PrefManager.importCustomGameAsSteamGame) }
    var libraryTabs by remember { mutableStateOf(PrefManager.libraryTabs) }
    var tabsDialog by rememberSaveable { mutableStateOf(false) }
    var syncDialog by rememberSaveable { mutableStateOf(false) }

    val configurableTabs = LibraryTab.configurableEntries
    val tabLabels = configurableTabs.map { stringResource(it.labelResId) }
    val selectedTabs = configurableTabs.indices.filter { configurableTabs[it] in libraryTabs }.toSet()

    val context = LocalContext.current
    var uninstalledMode by remember { mutableStateOf(RiftUninstalledMode.get(context)) }
    SettingsGroup(title = stringResource(R.string.rift_settings_group_library)) {
        ChoiceChipsRow(
            title = stringResource(R.string.rift_uninstalled_title),
            subtitle = stringResource(R.string.rift_uninstalled_summary),
            icon = Icons.Rounded.VisibilityOff,
            options = listOf(
                stringResource(R.string.rift_uninstalled_dim),
                stringResource(R.string.rift_uninstalled_hide),
                stringResource(R.string.rift_uninstalled_show),
            ),
            selectedIndex = UninstalledMode.entries.indexOf(uninstalledMode),
            onSelect = {
                uninstalledMode = UninstalledMode.entries[it]
                RiftUninstalledMode.set(context, uninstalledMode)
            },
        )
        LinkRow(
            title = stringResource(R.string.settings_interface_library_tabs_title),
            subtitle = stringResource(R.string.settings_interface_library_tabs_subtitle),
            icon = Icons.Rounded.Tab,
            value = if (selectedTabs.isEmpty()) {
                stringResource(R.string.settings_interface_library_tabs_none)
            } else {
                selectedTabs.joinToString(", ") { tabLabels[it] }
            },
            onClick = { tabsDialog = true },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_show_hidden_games_title),
            subtitle = stringResource(R.string.settings_interface_show_hidden_games_subtitle),
            icon = Icons.Rounded.VisibilityOff,
            checked = showHidden,
            onCheckedChange = {
                showHidden = it
                PrefManager.showHiddenGamesByDefault = it
                PluviaApp.events.emit(AndroidEvent.HiddenGamesSettingChanged(showHiddenGamesByDefault = it))
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_hide_ai_title),
            subtitle = stringResource(R.string.settings_hide_ai_subtitle),
            icon = Icons.Rounded.AutoAwesome,
            checked = hideAi,
            onCheckedChange = {
                hideAi = it
                PrefManager.hideAiFeatures = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_custom_game_import_as_steam),
            icon = Icons.Rounded.Upload,
            checked = importAsSteam,
            onCheckedChange = {
                importAsSteam = it
                PrefManager.importCustomGameAsSteamGame = it
            },
        )
        if (!BuildConfig.MODERN_ANDROID) {
            val anyConfigured by FrontendSyncManager.anyConfigured.collectAsState()
            LinkRow(
                title = stringResource(R.string.settings_interface_frontend_sync_title),
                subtitle = stringResource(R.string.settings_interface_frontend_sync_subtitle),
                icon = Icons.Rounded.Sync,
                value = if (anyConfigured) stringResource(R.string.rift_settings_on) else null,
                onClick = { syncDialog = true },
            )
        }
    }

    if (tabsDialog) {
        RiftMultiChoiceDialog(
            title = stringResource(R.string.settings_interface_library_tabs_title),
            options = tabLabels,
            selected = selectedTabs,
            onToggle = { index ->
                val tab = configurableTabs[index]
                val chosen = libraryTabs.toMutableSet()
                if (!chosen.add(tab)) chosen.remove(tab)
                libraryTabs = LibraryTab.entries.filter { it !in LibraryTab.configurableEntries || it in chosen }
                PrefManager.libraryTabs = libraryTabs
                PluviaApp.events.emit(AndroidEvent.LibraryTabsChanged(libraryTabs))
            },
            doneText = stringResource(R.string.rift_settings_done),
            onDismiss = { tabsDialog = false },
        )
    }
    if (syncDialog) FrontendSyncDialog(onDismiss = { syncDialog = false })
}

@Composable
private fun AchievementsGroup() {
    var showNotification by rememberSaveable { mutableStateOf(PrefManager.achievementShowNotification) }
    var playSound by rememberSaveable { mutableStateOf(PrefManager.achievementPlaySound) }
    val positionKeys = remember { ACHIEVEMENT_NOTIFICATION_POSITION.keys.toList() }
    val positionLabels = ACHIEVEMENT_NOTIFICATION_POSITION.values.map { stringResource(it) }
    var positionIndex by rememberSaveable {
        mutableStateOf(
            positionKeys.indexOf(PrefManager.achievementNotificationPosition).takeIf { it >= 0 }
                ?: positionKeys.indexOf("bottom_right"),
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_achievements)) {
        SwitchRow(
            title = stringResource(R.string.settings_achievement_show_notification),
            icon = Icons.Rounded.EmojiEvents,
            checked = showNotification,
            onCheckedChange = {
                showNotification = it
                PrefManager.achievementShowNotification = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_achievement_play_sound),
            icon = Icons.Rounded.VolumeUp,
            checked = playSound,
            onCheckedChange = {
                playSound = it
                PrefManager.achievementPlaySound = it
            },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.settings_achievement_notification_position),
            icon = Icons.Rounded.NotificationsActive,
            options = positionLabels,
            selectedIndex = positionIndex,
            onSelect = {
                positionIndex = it
                PrefManager.achievementNotificationPosition = positionKeys[it]
            },
        )
    }
}

/** Two big cards to choose between GameNative's icon and the alternative one (also the notification icon). */
@Composable
private fun IconStyleRow(selectedVariant: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .plateFill(focused = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_interface_icon_style),
            color = RiftColors.OnSurface,
            fontSize = 18.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconVariantCard(
                label = stringResource(R.string.settings_theme_default),
                launcherIconRes = R.mipmap.ic_launcher,
                notificationIconRes = R.drawable.ic_notification,
                selected = selectedVariant == 0,
                onClick = { onSelect(0) },
            )
            IconVariantCard(
                label = stringResource(R.string.settings_theme_alternate),
                launcherIconRes = R.mipmap.ic_launcher_alt,
                notificationIconRes = R.drawable.ic_notification_alt,
                selected = selectedVariant == 1,
                onClick = { onSelect(1) },
            )
        }
    }
}

@Composable
private fun IconVariantCard(
    label: String,
    launcherIconRes: Int,
    notificationIconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .plateFill(focused, rest = if (selected) RiftColors.SurfaceContainerHigh else RiftColors.Surface)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.BottomEnd) {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageResource(launcherIconRes)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
            )
            Image(
                painter = painterResource(id = notificationIconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (focused) plateText(true) else RiftAccent.current.main),
                )
            }
            Text(
                text = label,
                color = if (focused) plateText(true) else if (selected) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** What the bar at the top of the home screen shows, like Beacon's clock, battery and network. */
@Composable
private fun TopBarGroup() {
    val prefs = rememberRiftPrefs()
    var clock24h by rememberSaveable { mutableStateOf(prefs.clock24h) }
    var battery by rememberSaveable { mutableStateOf(prefs.showBattery) }
    var network by rememberSaveable { mutableStateOf(prefs.showNetwork) }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_top_bar)) {
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_clock),
            icon = Icons.Rounded.Schedule,
            options = listOf("24", "12"),
            selectedIndex = if (clock24h) 0 else 1,
            onSelect = {
                clock24h = it == 0
                prefs.clock24h = clock24h
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_show_battery),
            icon = Icons.Rounded.BatteryStd,
            checked = battery,
            onCheckedChange = {
                battery = it
                prefs.showBattery = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_show_network),
            icon = Icons.Rounded.Wifi,
            checked = network,
            onCheckedChange = {
                network = it
                prefs.showNetwork = it
            },
        )
    }
}

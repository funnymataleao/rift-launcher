package dev.rift.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/**
 * The place to add where games come from, as in Beacon's Platforms: nothing is shown on the home screen
 * until a source is added here, and each added source gets its own tab. Every row jumps to the page where
 * that source is set up.
 */
@Composable
internal fun SourcesSettings(onOpen: (RiftSettingsSection) -> Unit) {
    val prefs = rememberRiftPrefs()
    val androidCount = prefs.androidGamePackages.size
    val platformCount = prefs.emulatorPlatforms.size
    val notAdded = stringResource(R.string.rift_settings_source_not_added)

    NoteText(stringResource(R.string.rift_settings_sources_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_group_your_sources)) {
        SourceRow(RiftSettingsSection.Accounts, onOpen, value = null)
        SourceRow(
            RiftSettingsSection.AndroidGames,
            onOpen,
            value = if (androidCount > 0) androidCount.toString() else notAdded,
        )
        SourceRow(RiftSettingsSection.Streaming, onOpen, value = null)
        SourceRow(
            RiftSettingsSection.Emulators,
            onOpen,
            value = if (platformCount > 0) platformCount.toString() else notAdded,
        )
    }
}

@Composable
private fun SourceRow(section: RiftSettingsSection, onOpen: (RiftSettingsSection) -> Unit, value: String?) {
    LinkRow(
        title = stringResource(section.titleRes),
        subtitle = stringResource(section.summaryRes),
        icon = section.icon,
        value = value,
        onClick = { onOpen(section) },
    )
}

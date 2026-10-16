package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.component.dialog.Box64PresetsDialog
import app.gamenative.ui.component.dialog.FEXCorePresetsDialog
import app.gamenative.ui.screen.settings.WineProtonManagerDialog
import com.winlator.container.Container

/** How a Windows game is started: the Wine build, the CPU translator and what happens at start and exit. */
@Composable
internal fun LaunchSettings() {
    val defaults = rememberContainerDefaults()
    val data = defaults.data

    var autoApplyKnownConfig by rememberSaveable { mutableStateOf(PrefManager.autoApplyKnownConfig) }
    var powerControl by rememberSaveable { mutableStateOf(PrefManager.powerControlDefaultEnabled) }
    var wineManager by rememberSaveable { mutableStateOf(false) }
    var box64Presets by rememberSaveable { mutableStateOf(false) }
    var fexPresets by rememberSaveable { mutableStateOf(false) }
    var fullConfig by rememberSaveable { mutableStateOf(false) }

    val glibc = data.containerVariant.equals(Container.GLIBC, ignoreCase = true)
    val startupLabels = listOf(
        stringResource(R.string.rift_settings_startup_normal),
        stringResource(R.string.rift_settings_startup_essential),
        stringResource(R.string.rift_settings_startup_aggressive),
    ).let { if (glibc) it else it.take(2) }
    // The stored values are 0 normal, 1 essential, 2 aggressive (the last one only for glibc).
    val startupIndex = data.startupSelection.toInt().coerceIn(0, startupLabels.lastIndex)

    val suspendValues = listOf(
        Container.SUSPEND_POLICY_MANUAL,
        Container.SUSPEND_POLICY_AUTO,
        Container.SUSPEND_POLICY_NEVER,
    )
    val suspendLabels = listOf(
        stringResource(R.string.rift_settings_suspend_manual),
        stringResource(R.string.rift_settings_suspend_auto),
        stringResource(R.string.rift_settings_suspend_never),
    )

    SettingsGroup(title = stringResource(R.string.rift_settings_group_start)) {
        SwitchRow(
            title = stringResource(R.string.settings_emulation_auto_apply_known_config_title),
            subtitle = stringResource(R.string.settings_emulation_auto_apply_known_config_subtitle),
            icon = Icons.Rounded.AutoFixHigh,
            checked = autoApplyKnownConfig,
            onCheckedChange = {
                autoApplyKnownConfig = it
                PrefManager.autoApplyKnownConfig = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_performance_power_control_title),
            subtitle = stringResource(R.string.settings_performance_power_control_subtitle),
            icon = Icons.Rounded.Bolt,
            checked = powerControl,
            onCheckedChange = {
                powerControl = it
                PrefManager.powerControlDefaultEnabled = it
            },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.startup_selection),
            subtitle = stringResource(R.string.rift_settings_startup_summary),
            icon = Icons.Rounded.PowerSettingsNew,
            options = startupLabels,
            selectedIndex = startupIndex,
            onSelect = { index -> defaults.update { it.copy(startupSelection = index.toByte()) } },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_suspend),
            subtitle = stringResource(R.string.rift_settings_suspend_summary),
            icon = Icons.Rounded.PauseCircle,
            options = suspendLabels,
            selectedIndex = suspendValues.indexOfFirst { it.equals(data.suspendPolicy, ignoreCase = true) }
                .coerceAtLeast(0),
            onSelect = { index -> defaults.update { it.copy(suspendPolicy = suspendValues[index]) } },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_environment)) {
        InfoRow(
            title = stringResource(R.string.rift_settings_environment),
            value = "${data.containerVariant} · ${data.wineVersion}",
            icon = Icons.Rounded.Settings,
        )
        LinkRow(
            title = stringResource(R.string.settings_emulation_wine_proton_manager_title),
            subtitle = stringResource(R.string.settings_emulation_wine_proton_manager_subtitle),
            icon = Icons.Rounded.WineBar,
            onClick = { wineManager = true },
        )
        LinkRow(
            title = stringResource(R.string.settings_emulation_box64_presets_title),
            subtitle = stringResource(R.string.settings_emulation_box64_presets_subtitle),
            icon = Icons.Rounded.Memory,
            onClick = { box64Presets = true },
        )
        LinkRow(
            title = stringResource(R.string.fexcore_presets),
            subtitle = stringResource(R.string.fexcore_presets_description),
            icon = Icons.Rounded.Memory,
            onClick = { fexPresets = true },
        )
        LinkRow(
            title = stringResource(R.string.rift_settings_full_config),
            subtitle = stringResource(R.string.rift_settings_full_config_launch),
            icon = Icons.Rounded.Tune,
            onClick = { fullConfig = true },
        )
    }

    if (wineManager) {
        WineProtonManagerDialog(
            open = true,
            onDismiss = {
                wineManager = false
                defaults.reload()
            },
        )
    }
    Box64PresetsDialog(visible = box64Presets, onDismissRequest = { box64Presets = false })
    if (fexPresets) FEXCorePresetsDialog(visible = true, onDismissRequest = { fexPresets = false })
    DefaultConfigDialog(visible = fullConfig, defaults = defaults, onDismiss = { fullConfig = false })
}

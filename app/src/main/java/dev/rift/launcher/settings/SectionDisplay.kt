package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.ui.component.dialog.OrientationDialog
import com.winlator.container.Container
import com.winlator.core.StringUtils

/** How the game picture is made: size, renderer, sharpness, orientation and a second display. */
@Composable
internal fun DisplaySettings() {
    val defaults = rememberContainerDefaults()
    val data = defaults.data

    var sizeDialog by rememberSaveable { mutableStateOf(false) }
    var rendererDialog by rememberSaveable { mutableStateOf(false) }
    var externalDialog by rememberSaveable { mutableStateOf(false) }
    var orientationDialog by rememberSaveable { mutableStateOf(false) }

    // Screen size: GameNative's list without its "Custom" entry, plus the device's own size first.
    val deviceSize = remember { PluviaApp.getDefaultScreenSize() }
    val sizeEntries = stringArrayResource(R.array.screen_size_entries).drop(1)
    val sizeValues = listOf(deviceSize) + sizeEntries.map { it.substringBefore(' ') }
    val sizeLabels = listOf(stringResource(R.string.rift_settings_device_screen, deviceSize)) + sizeEntries
    val sizeIndex = sizeValues.indexOf(data.screenSize)

    val rendererEntries = stringArrayResource(R.array.displayrenderers_entries)
    val rendererIndex = rendererEntries.indexOfFirst { StringUtils.parseIdentifier(it) == data.displayRenderer }

    val presentModes = listOf("fifo", "mailbox")
    val presentLabels = listOf(
        stringResource(R.string.rift_settings_present_fifo),
        stringResource(R.string.rift_settings_present_mailbox),
    )

    val externalValues = listOf(
        Container.EXTERNAL_DISPLAY_MODE_OFF,
        Container.EXTERNAL_DISPLAY_MODE_TOUCHPAD,
        Container.EXTERNAL_DISPLAY_MODE_KEYBOARD,
        Container.EXTERNAL_DISPLAY_MODE_HYBRID,
    )
    val externalLabels = listOf(
        stringResource(R.string.external_display_mode_off),
        stringResource(R.string.external_display_mode_touchpad),
        stringResource(R.string.external_display_mode_keyboard),
        stringResource(R.string.external_display_mode_hybrid),
    )
    val externalIndex = externalValues.indexOfFirst { it.equals(data.externalDisplayMode, ignoreCase = true) }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_picture)) {
        LinkRow(
            title = stringResource(R.string.screen_size),
            subtitle = stringResource(R.string.rift_settings_screen_size_summary),
            icon = Icons.Rounded.AspectRatio,
            value = sizeLabels.getOrNull(sizeIndex)?.substringBefore(" (") ?: data.screenSize,
            onClick = { sizeDialog = true },
        )
        LinkRow(
            title = stringResource(R.string.display_renderer),
            subtitle = stringResource(R.string.rift_settings_renderer_summary),
            icon = Icons.Rounded.Layers,
            value = rendererEntries.getOrNull(rendererIndex) ?: data.displayRenderer,
            onClick = { rendererDialog = true },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_present_mode),
            subtitle = stringResource(R.string.rift_settings_present_mode_summary),
            icon = Icons.Rounded.Speed,
            options = presentLabels,
            selectedIndex = presentModes.indexOfFirst { it.equals(data.rendererPresentMode, ignoreCase = true) }
                .coerceAtLeast(0),
            onSelect = { index -> defaults.update { it.copy(rendererPresentMode = presentModes[index]) } },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_sharpness)) {
        val effects = listOf("None", "CAS", "DLS")
        val effectLabels = listOf(
            stringResource(R.string.rift_settings_off),
            stringResource(R.string.rift_settings_sharp_cas),
            stringResource(R.string.rift_settings_sharp_dls),
        )
        val effectIndex = effects.indexOfFirst { it.equals(data.sharpnessEffect, ignoreCase = true) }.coerceAtLeast(0)
        ChoiceChipsRow(
            title = stringResource(R.string.sharpness_effect),
            subtitle = stringResource(R.string.rift_settings_sharpness_summary),
            icon = Icons.Rounded.AutoFixHigh,
            options = effectLabels,
            selectedIndex = effectIndex,
            onSelect = { index -> defaults.update { it.copy(sharpnessEffect = effects[index]) } },
        )
        if (effectIndex > 0) {
            val levels = listOf(25, 50, 75, 100)
            ChoiceChipsRow(
                title = stringResource(R.string.sharpness_level),
                icon = Icons.Rounded.Tune,
                options = levels.map { "$it%" },
                selectedIndex = levels.indexOfFirst { it >= data.sharpnessLevel }.let { if (it < 0) levels.lastIndex else it },
                onSelect = { index -> defaults.update { it.copy(sharpnessLevel = levels[index]) } },
            )
            ChoiceChipsRow(
                title = stringResource(R.string.sharpness_denoise),
                icon = Icons.Rounded.Tune,
                options = levels.map { "$it%" },
                selectedIndex = levels.indexOfFirst { it >= data.sharpnessDenoise }.let { if (it < 0) levels.lastIndex else it },
                onSelect = { index -> defaults.update { it.copy(sharpnessDenoise = levels[index]) } },
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_orientation)) {
        LinkRow(
            title = stringResource(R.string.settings_emulation_orientations_title),
            subtitle = stringResource(R.string.settings_emulation_orientations_subtitle),
            icon = Icons.Rounded.ScreenRotation,
            onClick = { orientationDialog = true },
        )
        SwitchRow(
            title = stringResource(R.string.portrait_mode),
            subtitle = stringResource(R.string.portrait_mode_description),
            icon = Icons.Rounded.StayCurrentPortrait,
            checked = data.portraitMode,
            onCheckedChange = { value -> defaults.update { it.copy(portraitMode = value) } },
        )
        if (data.portraitMode) {
            SwitchRow(
                title = stringResource(R.string.portrait_below_cutout),
                subtitle = stringResource(R.string.portrait_below_cutout_description),
                icon = Icons.Rounded.VerticalAlignBottom,
                checked = data.portraitBelowCutout,
                onCheckedChange = { value -> defaults.update { it.copy(portraitBelowCutout = value) } },
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_external_display)) {
        LinkRow(
            title = stringResource(R.string.external_display_input),
            subtitle = stringResource(R.string.external_display_input_subtitle),
            icon = Icons.Rounded.Tv,
            value = externalLabels.getOrNull(externalIndex),
            onClick = { externalDialog = true },
        )
        SwitchRow(
            title = stringResource(R.string.external_display_swap),
            subtitle = stringResource(R.string.external_display_swap_subtitle),
            icon = Icons.Rounded.SwapVert,
            checked = data.externalDisplaySwap,
            onCheckedChange = { value -> defaults.update { it.copy(externalDisplaySwap = value) } },
        )
    }

    if (sizeDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.screen_size),
            options = sizeLabels,
            selectedIndex = sizeIndex,
            onSelect = { index -> defaults.update { it.copy(screenSize = sizeValues[index]) } },
            onDismiss = { sizeDialog = false },
        )
    }
    if (rendererDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.display_renderer),
            options = rendererEntries.toList(),
            selectedIndex = rendererIndex,
            onSelect = { index ->
                defaults.update { it.copy(displayRenderer = StringUtils.parseIdentifier(rendererEntries[index])) }
            },
            onDismiss = { rendererDialog = false },
        )
    }
    if (externalDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.external_display_input),
            options = externalLabels,
            selectedIndex = externalIndex,
            onSelect = { index -> defaults.update { it.copy(externalDisplayMode = externalValues[index]) } },
            onDismiss = { externalDialog = false },
        )
    }
    OrientationDialog(openDialog = orientationDialog, onDismiss = { orientationDialog = false })
}

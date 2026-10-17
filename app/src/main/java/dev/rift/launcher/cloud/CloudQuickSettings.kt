package dev.rift.launcher.cloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.component.QuickMenuItem
import app.gamenative.ui.component.QuickMenuItemRow
import app.gamenative.ui.component.QuickMenuToggleRow
import dev.rift.launcher.stream.ChoiceRow
import dev.rift.launcher.stream.StepRow
import dev.rift.launcher.stream.StreamMenuRow
import dev.rift.launcher.ui.theme.RiftAccent

/**
 * The cloud game's part of the in-game panel (the gamepad's Home): xCloud's own guide, which Home no
 * longer opens ([onXboxMenu]), then every setting that changes while playing, each applied to the
 * stream at once by [onChange]. What only takes effect when a game starts (resolution, quality,
 * bitrate) and the page's own interface are in Settings → Cloud streaming.
 */
@Composable
internal fun CloudQuickSettings(
    requester: (String) -> FocusRequester,
    onXboxMenu: () -> Unit,
    onChange: (CloudSetting, Any) -> Unit,
) {
    val context = LocalContext.current
    val accent = RiftAccent.current.main
    var values by remember { mutableStateOf(CloudPlaySettings.values(context)) }
    fun change(setting: CloudSetting, value: Any) {
        values = values + (setting.key to value)
        onChange(setting, value)
    }

    @Composable
    fun Toggle(setting: CloudSetting.Toggle) {
        val on = values[setting.key] as? Boolean ?: setting.default
        QuickMenuToggleRow(
            title = stringResource(setting.title),
            enabled = on,
            onToggle = { change(setting, !on) },
            accentColor = accent,
            focusRequester = requester(setting.key),
        )
    }

    // A few answers are pills, a longer list or a scale is a slider.
    @Composable
    fun Choice(setting: CloudSetting.Choice) {
        val labels = setting.options.associate { it.value to it.label() }
        val value = values[setting.key] ?: setting.default
        if (setting.options.size <= 3) {
            ChoiceRow(
                title = stringResource(setting.title),
                choices = setting.options.map { it.value },
                value = value,
                label = { labels[it].orEmpty() },
                onChange = { change(setting, it) },
                accent = accent,
                focusRequester = requester(setting.key),
            )
        } else {
            StepRow(
                title = stringResource(setting.title),
                steps = setting.options.map { it.value },
                value = value,
                label = { labels[it].orEmpty() },
                onChange = { change(setting, it) },
                accent = accent,
                focusRequester = requester(setting.key),
            )
        }
    }

    QuickMenuItemRow(
        item = QuickMenuItem(2, Icons.Rounded.VideogameAsset, R.string.rift_cloud_xbox_menu),
        onClick = onXboxMenu,
        focusRequester = requester(StreamMenuRow.CLOUD_FIRST),
    )
    with(CloudPlaySettings) {
        Toggle(stats)
        Choice(statsPosition)
        Choice(maxFps)
        Choice(sharpness)
        Choice(clarity)
        Choice(clarityMode)
        Choice(renderer)
        Choice(power)
        Choice(brightness)
        Choice(contrast)
        Choice(saturation)
        Choice(ratio)
        Choice(position)
        // Sound above 100 % takes the volume control, which the page sets up when the game starts.
        if (values[volumeControl.key] == true) Choice(volume)
        Choice(deviceVibration)
        Choice(vibrationStrength)
        Choice(pollingRate)
        Toggle(localCoop)
    }
}

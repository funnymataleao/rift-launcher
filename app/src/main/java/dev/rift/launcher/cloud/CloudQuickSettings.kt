package dev.rift.launcher.cloud

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.QuickMenuSectionHeader
import app.gamenative.ui.component.QuickMenuToggleRow
import dev.rift.launcher.stream.StepRow
import dev.rift.launcher.ui.theme.RiftAccent

/**
 * The cloud game's settings in the in-game panel (the gamepad's Home): the ones worth changing while
 * playing, each applied to the stream at once by [onChange]. The rest are in Settings → Cloud streaming.
 */
@Composable
internal fun CloudQuickSettings(requester: (String) -> FocusRequester, onChange: (CloudSetting, Any) -> Unit) {
    val context = LocalContext.current
    val accent = RiftAccent.current.main
    var values by remember { mutableStateOf(CloudPlaySettings.values(context)) }
    fun change(setting: CloudSetting, value: Any) {
        values = values + (setting.key to value)
        onChange(setting, value)
    }

    QuickMenuSectionHeader(title = stringResource(R.string.rift_stream_settings), modifier = Modifier.padding(top = 12.dp))
    val stats = CloudPlaySettings.stats
    val statsOn = values[stats.key] == true
    QuickMenuToggleRow(
        title = stringResource(stats.title),
        enabled = statsOn,
        onToggle = { change(stats, !statsOn) },
        accentColor = accent,
        focusRequester = requester(stats.key),
    )
    // Sound above 100 % takes the volume control, which the page sets up when the game starts.
    val volume = CloudPlaySettings.volume.takeIf { values[CloudPlaySettings.volumeControl.key] == true }
    listOfNotNull(
        CloudPlaySettings.maxFps,
        CloudPlaySettings.sharpness,
        CloudPlaySettings.brightness,
        CloudPlaySettings.contrast,
        CloudPlaySettings.saturation,
        CloudPlaySettings.ratio,
        volume,
    ).forEach { setting ->
        val labels = setting.options.associate { it.value to it.label() }
        StepRow(
            title = stringResource(setting.title),
            steps = setting.options.map { it.value },
            value = values[setting.key] ?: setting.default,
            label = { labels[it].orEmpty() },
            onChange = { change(setting, it) },
            accent = accent,
            focusRequester = requester(setting.key),
        )
    }
}

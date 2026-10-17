package dev.rift.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.home.RiftSection
import dev.rift.launcher.home.RiftTabPrefs
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

private sealed interface TabDialog {
    val section: RiftSection

    data class Manage(override val section: RiftSection) : TabDialog
    data class Rename(override val section: RiftSection) : TabDialog
}

/**
 * The tabs along the top of the home screen, like the platform list in Beacon Game Launcher: each can be
 * renamed, moved and hidden. A tab still appears only once its source is set up.
 */
@Composable
internal fun TabsSettings() {
    val context = LocalContext.current
    var order by remember { mutableStateOf(RiftTabPrefs.order(context)) }
    var hidden by remember { mutableStateOf(RiftTabPrefs.hidden(context)) }
    var names by remember { mutableStateOf(RiftTabPrefs.names(context)) }
    var dialog by remember { mutableStateOf<TabDialog?>(null) }

    fun move(section: RiftSection, step: Int) {
        val from = order.indexOf(section)
        val to = (from + step).coerceIn(0, order.lastIndex)
        if (from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        RiftTabPrefs.setOrder(context, order)
    }

    fun setHidden(section: RiftSection, hide: Boolean) {
        hidden = if (hide) hidden + section.key else hidden - section.key
        RiftTabPrefs.setHidden(context, hidden)
    }

    fun rename(section: RiftSection, name: String) {
        RiftTabPrefs.setName(context, section, name)
        names = RiftTabPrefs.names(context)
    }

    NoteText(stringResource(R.string.rift_settings_tabs_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_tabs_group)) {
        order.forEachIndexed { index, section ->
            val defaultName = stringResource(section.titleRes)
            val custom = names[section.key]
            val state = stringResource(
                if (section.key in hidden) R.string.rift_settings_tab_hidden else R.string.rift_settings_tab_shown,
            )
            CompositionLocalProvider(LocalSettingsAccent provides section.accent()) {
                LinkRow(
                    title = custom ?: defaultName,
                    subtitle = if (custom != null) "$state · $defaultName" else state,
                    icon = section.icon(),
                    value = (index + 1).toString(),
                    onClick = { dialog = TabDialog.Manage(section) },
                )
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        is TabDialog.Manage -> {
            val section = current.section
            val index = order.indexOf(section)
            val isHidden = section.key in hidden
            // The last tab left showing cannot be hidden, so the home screen never ends up without tabs.
            val canHide = isHidden || order.count { it.key !in hidden } > 1
            val actions = buildList<Pair<String, () -> Unit>> {
                add(stringResource(R.string.rift_settings_tab_rename) to { dialog = TabDialog.Rename(section) })
                if (index > 0) add(stringResource(R.string.rift_settings_tab_move_up) to { move(section, -1) })
                if (index < order.lastIndex) add(stringResource(R.string.rift_settings_tab_move_down) to { move(section, 1) })
                if (canHide) {
                    add(
                        stringResource(if (isHidden) R.string.rift_settings_tab_show else R.string.rift_settings_tab_hide) to
                            { setHidden(section, !isHidden) },
                    )
                }
                if (section.key in names) add(stringResource(R.string.rift_settings_tab_reset_name) to { rename(section, "") })
            }
            RiftChoiceDialog(
                title = names[section.key] ?: stringResource(section.titleRes),
                options = actions.map { it.first },
                selectedIndex = -1,
                onSelect = { actions[it].second() },
                onDismiss = { if (dialog == current) dialog = null },
            )
        }
        is TabDialog.Rename -> {
            val section = current.section
            RenameTabDialog(
                initial = names[section.key] ?: stringResource(section.titleRes),
                onSave = {
                    rename(section, it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

@Composable
private fun RenameTabDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_settings_tab_rename),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_settings_tab_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RiftSizes.button)
                .background(RiftColors.SurfaceContainerHigh, RiftShapes.full)
                .border(1.dp, RiftColors.OutlineVariant, RiftShapes.full)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(RiftTabPrefs.MAX_NAME_LENGTH) },
                singleLine = true,
                textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 17.sp),
                cursorBrush = SolidColor(RiftColors.OnSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        NoteText(stringResource(R.string.rift_settings_tab_rename_hint))
    }
}

private fun RiftSection.icon(): ImageVector = when (this) {
    RiftSection.PcGames -> Icons.Rounded.DesktopWindows
    RiftSection.AndroidGames -> Icons.Rounded.Android
    RiftSection.PcStreaming -> Icons.Rounded.Cast
    RiftSection.CloudStreaming -> Icons.Rounded.Cloud
}

private fun RiftSection.accent(): SettingsAccent = when (this) {
    RiftSection.CloudStreaming -> SettingsAccent.Sky
    RiftSection.PcGames -> SettingsAccent.Blue
    RiftSection.AndroidGames -> SettingsAccent.Green
    RiftSection.PcStreaming -> SettingsAccent.Mint
}

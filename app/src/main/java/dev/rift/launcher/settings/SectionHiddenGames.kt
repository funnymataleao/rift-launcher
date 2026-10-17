package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import dev.rift.launcher.home.RiftHiddenGames

/** The games hidden from the home with "Hide game"; pressing one brings it back. */
@Composable
internal fun HiddenGamesSettings() {
    val context = LocalContext.current
    val hidden by RiftHiddenGames.hidden(context).collectAsStateWithLifecycle()
    if (hidden.isEmpty()) {
        NoteText(stringResource(R.string.rift_settings_hidden_none))
        return
    }
    NoteText(stringResource(R.string.rift_settings_hidden_note))
    SettingsGroup(title = stringResource(R.string.rift_settings_hidden)) {
        hidden.entries.sortedBy { it.value.lowercase() }.forEach { (appId, name) ->
            LinkRow(
                title = name.ifBlank { appId },
                icon = Icons.Rounded.Visibility,
                value = stringResource(R.string.rift_settings_hidden_restore),
                onClick = { RiftHiddenGames.show(context, appId) },
            )
        }
    }
}

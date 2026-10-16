package dev.rift.launcher.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/**
 * Interface sounds. The values live in the `rift_sounds` file the sound player reads on every play, so a
 * change here is heard at once: `enabled`, `volume` (0..100) and `pack_uri` (a folder with files named
 * move, select, back, tab, open, close and launch; empty means the built-in set).
 */
@Composable
internal fun SoundSettings() {
    val context = LocalContext.current
    val sounds = remember { context.getSharedPreferences("rift_sounds", Context.MODE_PRIVATE) }
    var enabled by rememberSaveable { mutableStateOf(sounds.getBoolean("enabled", true)) }
    var volume by rememberSaveable { mutableStateOf(sounds.getInt("volume", 35)) }
    var packUri by rememberSaveable { mutableStateOf(sounds.getString("pack_uri", "").orEmpty()) }

    val pickFolder = rememberFolderPicker { uri ->
        packUri = uri
        sounds.edit().putString("pack_uri", uri).apply()
    }

    SettingsGroup {
        SwitchRow(
            title = stringResource(R.string.rift_settings_ui_sounds),
            subtitle = stringResource(R.string.rift_settings_ui_sounds_summary),
            icon = Icons.Rounded.VolumeUp,
            checked = enabled,
            onCheckedChange = {
                enabled = it
                sounds.edit().putBoolean("enabled", it).apply()
            },
        )
        val levels = listOf(15, 35, 50, 75, 100)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_sound_volume),
            icon = Icons.Rounded.GraphicEq,
            options = levels.map { "$it%" },
            selectedIndex = levels.indexOf(volume),
            onSelect = {
                volume = levels[it]
                sounds.edit().putInt("volume", volume).apply()
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_sound_pack)) {
        LinkRow(
            title = stringResource(R.string.rift_settings_sound_pack),
            subtitle = folderLabel(packUri) ?: stringResource(R.string.rift_settings_sound_pack_builtin),
            icon = Icons.Rounded.Folder,
            value = stringResource(if (packUri.isBlank()) R.string.rift_settings_choose else R.string.rift_settings_change),
            onClick = pickFolder,
        )
        if (packUri.isNotBlank()) {
            LinkRow(
                title = stringResource(R.string.rift_settings_sound_pack_reset),
                icon = Icons.Rounded.RestartAlt,
                onClick = {
                    packUri = ""
                    sounds.edit().putString("pack_uri", "").apply()
                },
            )
        }
        NoteText(stringResource(R.string.rift_settings_sound_note))
    }
}

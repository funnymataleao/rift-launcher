package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import dev.rift.launcher.sound.RiftMusic
import dev.rift.launcher.sound.RiftSoundSettings

/**
 * Interface sounds. The values live in the `rift_sounds` file the sound player reads on every play, so a
 * change here is heard at once: `enabled`, `volume` (0..100) and `pack_uri` (a folder with files named
 * move, select, back, tab, open, close and launch; empty means the built-in set).
 */
@Composable
internal fun SoundSettings() {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(RiftSoundSettings.isEnabled(context)) }
    var volume by rememberSaveable { mutableStateOf(RiftSoundSettings.volume(context)) }
    var packUri by rememberSaveable { mutableStateOf(RiftSoundSettings.packUri(context)) }
    var musicEnabled by rememberSaveable { mutableStateOf(RiftSoundSettings.isMusicEnabled(context)) }
    var musicVolume by rememberSaveable { mutableStateOf(RiftSoundSettings.musicVolume(context)) }
    var soundPackId by rememberSaveable { mutableStateOf(RiftSoundSettings.soundPackId(context)) }
    var musicPackId by rememberSaveable { mutableStateOf(RiftSoundSettings.musicPackId(context)) }
    // The pack catalog is a page of this section, not a pop-up; Back closes it and focus returns to its row.
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var cameFrom by rememberSaveable { mutableStateOf<String?>(null) }
    val soundLink = remember { FocusRequester() }
    val musicLink = remember { FocusRequester() }
    BackHandler(enabled = page != null) { page = null }
    LaunchedEffect(page) {
        if (page == null) {
            cameFrom?.let { runCatching { (if (it == "music") musicLink else soundLink).requestFocus() } }
        } else {
            cameFrom = page
        }
    }

    val pickFolder = rememberFolderPicker { uri ->
        packUri = uri
        RiftSoundSettings.setPackUri(context, android.net.Uri.parse(uri))
    }

    page?.let { kind ->
        PackCatalogPage(
            music = kind == "music",
            onChanged = {
                soundPackId = RiftSoundSettings.soundPackId(context)
                musicPackId = RiftSoundSettings.musicPackId(context)
            },
        )
        return
    }

    SettingsGroup {
        SwitchRow(
            title = stringResource(R.string.rift_settings_ui_sounds),
            subtitle = stringResource(R.string.rift_settings_ui_sounds_summary),
            icon = Icons.Rounded.VolumeUp,
            checked = enabled,
            onCheckedChange = {
                enabled = it
                RiftSoundSettings.setEnabled(context, it)
            },
        )
        val levels = listOf(15, 35, 60, 80, 100)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_sound_volume),
            icon = Icons.Rounded.GraphicEq,
            options = levels.map { "$it%" },
            selectedIndex = levels.indexOfLast { it <= volume }.coerceAtLeast(0),
            onSelect = {
                volume = levels[it]
                RiftSoundSettings.setVolume(context, volume)
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_deckthemes)) {
        LinkRow(
            modifier = Modifier.focusRequester(soundLink),
            title = stringResource(R.string.rift_settings_packs_sound_title),
            subtitle = stringResource(R.string.rift_settings_packs_sound_summary),
            icon = Icons.Rounded.CloudDownload,
            value = packName(context, soundPackId) ?: stringResource(R.string.rift_settings_sound_pack_builtin),
            onClick = { page = "sound" },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_music),
            subtitle = stringResource(R.string.rift_settings_music_summary),
            icon = Icons.Rounded.MusicNote,
            checked = musicEnabled,
            onCheckedChange = {
                musicEnabled = it
                RiftSoundSettings.setMusicEnabled(context, it)
                RiftMusic.apply(context)
            },
        )
        LinkRow(
            modifier = Modifier.focusRequester(musicLink),
            title = stringResource(R.string.rift_settings_packs_music_title),
            subtitle = stringResource(R.string.rift_settings_packs_music_summary),
            icon = Icons.Rounded.CloudDownload,
            value = packName(context, musicPackId) ?: stringResource(R.string.rift_settings_music_none),
            onClick = { page = "music" },
        )
        val musicLevels = listOf(10, 25, 40, 60, 100)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_music_volume),
            icon = Icons.Rounded.GraphicEq,
            options = musicLevels.map { "$it%" },
            selectedIndex = musicLevels.indexOfLast { it <= musicVolume }.coerceAtLeast(0),
            onSelect = {
                musicVolume = musicLevels[it]
                RiftSoundSettings.setMusicVolume(context, musicVolume)
                RiftMusic.apply(context)
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
                    RiftSoundSettings.setPackUri(context, null)
                },
            )
        }
        NoteText(stringResource(R.string.rift_settings_sound_note))
    }
}

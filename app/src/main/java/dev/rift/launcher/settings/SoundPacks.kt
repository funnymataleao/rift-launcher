package dev.rift.launcher.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.R
import dev.rift.launcher.sound.RiftMusic
import dev.rift.launcher.sound.RiftSoundSettings
import dev.rift.launcher.sound.pack.DeckPack
import dev.rift.launcher.sound.pack.DeckThemesClient
import dev.rift.launcher.sound.pack.InstalledPack
import dev.rift.launcher.sound.pack.InstalledPacks
import dev.rift.launcher.sound.pack.PackError
import kotlinx.coroutines.launch

/** Picks an interface-sound or a music pack: the ones on the device first, then the DeckThemes catalog. */
@Composable
internal fun PackManagerDialog(music: Boolean, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(InstalledPacks.list(context).filter { it.music == music }) }
    var selected by remember { mutableStateOf(selectedId(context, music)) }
    val catalog = remember { mutableStateListOf<DeckPack>() }
    var page by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<InstalledPack?>(null) }

    fun select(id: String) {
        if (music) RiftSoundSettings.setMusicPackId(context, id) else RiftSoundSettings.setSoundPackId(context, id)
        selected = id
        if (music) RiftMusic.apply(context)
        onChanged()
    }

    fun loadMore() {
        if (loading) return
        loading = true
        failed = false
        scope.launch {
            try {
                catalog += DeckThemesClient.list(music, page + 1)
                page += 1
            } catch (e: PackError) {
                failed = true
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    RiftDialog(
        title = stringResource(if (music) R.string.rift_settings_packs_music_title else R.string.rift_settings_packs_sound_title),
        onDismiss = onDismiss,
        actions = { RiftPillButton(text = stringResource(R.string.rift_settings_done), onClick = onDismiss, primary = true) },
    ) {
        Column(
            modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val first = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            LinkRow(
                modifier = Modifier.focusRequester(first),
                title = stringResource(if (music) R.string.rift_settings_packs_none_music else R.string.rift_settings_packs_builtin),
                icon = Icons.Rounded.RestartAlt,
                value = if (selected.isBlank()) stringResource(R.string.rift_settings_packs_selected) else null,
                onClick = { select("") },
            )
            installed.forEach { pack ->
                LinkRow(
                    title = pack.name,
                    subtitle = pack.author.takeIf { it.isNotBlank() },
                    icon = if (music) Icons.Rounded.MusicNote else Icons.Rounded.Check,
                    value = stringResource(if (pack.id == selected) R.string.rift_settings_packs_selected else R.string.rift_settings_packs_choose),
                    onClick = { select(pack.id) },
                    onLongClick = { removing = pack },
                )
            }
            if (installed.isNotEmpty()) NoteText(stringResource(R.string.rift_settings_packs_remove_hint))

            val onDevice = installed.map { it.id }.toSet()
            catalog.filter { it.id !in onDevice }.forEach { pack ->
                LinkRow(
                    title = pack.name,
                    subtitle = listOf(pack.author, stringResource(R.string.rift_settings_packs_downloads, pack.downloads))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    icon = Icons.Rounded.CloudDownload,
                    value = stringResource(if (busyId == pack.id) R.string.rift_settings_packs_downloading else R.string.rift_settings_packs_download),
                    enabled = busyId == null,
                    onClick = {
                        busyId = pack.id
                        scope.launch {
                            try {
                                DeckThemesClient.install(context, pack)
                                installed = InstalledPacks.list(context).filter { it.music == music }
                                select(pack.id)
                                failed = false
                            } catch (e: PackError) {
                                failed = true
                            }
                            busyId = null
                        }
                    },
                )
            }
            if (failed) NoteText(stringResource(R.string.rift_settings_packs_error))
            LinkRow(
                title = stringResource(
                    when {
                        loading -> R.string.rift_settings_packs_loading
                        failed -> R.string.rift_settings_packs_retry
                        else -> R.string.rift_settings_packs_more
                    },
                ),
                enabled = !loading,
                onClick = ::loadMore,
            )
            NoteText(stringResource(R.string.rift_settings_packs_note))
        }
    }

    removing?.let { pack ->
        RiftConfirmDialog(
            title = stringResource(R.string.rift_settings_packs_remove_title, pack.name),
            message = stringResource(R.string.rift_settings_packs_remove_message),
            confirmText = stringResource(R.string.rift_settings_packs_remove),
            dismissText = stringResource(R.string.rift_settings_cancel),
            onConfirm = {
                if (pack.id == selected) select("")
                InstalledPacks.remove(context, pack.id)
                installed = InstalledPacks.list(context).filter { it.music == music }
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

private fun selectedId(context: Context, music: Boolean) =
    if (music) RiftSoundSettings.musicPackId(context) else RiftSoundSettings.soundPackId(context)

/** The name shown for the pack in use, or null when nothing is picked. */
internal fun packName(context: Context, id: String): String? = InstalledPacks.get(context, id)?.name

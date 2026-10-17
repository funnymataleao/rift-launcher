package dev.rift.launcher.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import dev.rift.launcher.sound.pack.PackPreview
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
import dev.rift.launcher.sound.riftSilentPress
import dev.rift.launcher.sound.RiftSoundSettings
import dev.rift.launcher.sound.pack.DeckPack
import dev.rift.launcher.sound.pack.DeckThemesClient
import dev.rift.launcher.sound.pack.InstalledPack
import dev.rift.launcher.sound.pack.InstalledPacks
import dev.rift.launcher.sound.pack.PackError
import kotlinx.coroutines.launch
import java.io.File

/**
 * A page of the Sound section (not a pop-up): the packs on the device first, then the DeckThemes catalog, which
 * loads further pages as the list is walked down. Every pack has a play button to hear it before it is kept.
 * The caller handles Back by closing the page.
 */
@Composable
internal fun PackCatalogPage(music: Boolean, onChanged: () -> Unit, hold: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(InstalledPacks.list(context).filter { it.music == music }) }
    var selected by remember { mutableStateOf(selectedId(context, music)) }
    val catalog = remember { mutableStateListOf<DeckPack>() }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var previewBusyId by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<InstalledPack?>(null) }
    val previewing = PackPreview.playing
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    var refocus by remember { mutableStateOf(0) }
    var focusId by remember { mutableStateOf<String?>(null) }
    val rowFocus = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    LaunchedEffect(refocus) {
        kotlinx.coroutines.yield()
        runCatching { (focusId?.let { rowFocus[it] } ?: first).requestFocus() }
        focusId = null
    }

    DisposableEffect(Unit) { onDispose { PackPreview.stop(context) } }

    fun select(id: String) {
        if (music) RiftSoundSettings.setMusicPackId(context, id) else RiftSoundSettings.setSoundPackId(context, id)
        selected = id
        if (music) RiftMusic.apply(context)
        onChanged()
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        failed = false
        scope.launch {
            try {
                val batch = DeckThemesClient.list(music, page + 1)
                catalog += batch
                page += 1
                hasMore = batch.size >= DeckThemesClient.PAGE_SIZE
            } catch (e: PackError) {
                failed = true
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadMore() }

    fun hear(key: String, ready: File?, pack: DeckPack?) {
        if (PackPreview.playing == key) {
            PackPreview.stop(context)
            return
        }
        scope.launch {
            val dir = ready ?: run {
                if (pack == null) return@launch
                if (previewBusyId != null) {
                    timber.log.Timber.tag("RiftPreview").i("Preview of %s ignored: %s is still downloading", key, previewBusyId)
                    return@launch
                }
                previewBusyId = key
                try {
                    DeckThemesClient.preview(context, pack).also { failed = false }
                } catch (e: PackError) {
                    timber.log.Timber.tag("RiftPreview").w(e, "Preview download of %s failed", key)
                    failed = true
                    null
                } finally {
                    previewBusyId = null
                }
            } ?: return@launch
            if (music) PackPreview.playMusic(context, key, dir) else PackPreview.playSounds(context, key, dir)
        }
    }

    SettingsGroup(
        title = stringResource(if (music) R.string.rift_settings_packs_music_title else R.string.rift_settings_packs_sound_title),
    ) {
        LinkRow(
            modifier = Modifier.focusRequester(first),
            title = stringResource(if (music) R.string.rift_settings_packs_none_music else R.string.rift_settings_packs_builtin),
            icon = Icons.Rounded.RestartAlt,
            value = if (selected.isBlank()) stringResource(R.string.rift_settings_packs_selected) else null,
            onClick = { select("") },
        )
        installed.forEach { pack ->
            PackRow(
                modifier = Modifier.focusRequester(rowFocus.getOrPut(pack.id) { androidx.compose.ui.focus.FocusRequester() }),
                title = pack.name,
                subtitle = pack.author.takeIf { it.isNotBlank() },
                icon = if (music) Icons.Rounded.MusicNote else Icons.Rounded.Check,
                value = stringResource(if (pack.id == selected) R.string.rift_settings_packs_selected else R.string.rift_settings_packs_choose),
                onClick = { select(pack.id) },
                onLongClick = { removing = pack },
                playing = previewing == pack.id,
                onPreview = { hear(pack.id, pack.dir, null) },
            )
        }
        if (installed.isNotEmpty()) NoteText(stringResource(R.string.rift_settings_packs_remove_hint))
    }

    val onDevice = installed.map { it.id }.toSet()
    SettingsGroup(title = stringResource(R.string.rift_settings_packs_catalog)) {
        catalog.filter { it.id !in onDevice }.forEach { pack ->
            PackRow(
                title = pack.name,
                subtitle = listOf(pack.author, stringResource(R.string.rift_settings_packs_downloads, pack.downloads))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                icon = Icons.Rounded.CloudDownload,
                value = stringResource(if (busyId == pack.id) R.string.rift_settings_packs_downloading else R.string.rift_settings_packs_download),
                onClick = {
                    if (busyId != null) return@PackRow
                    timber.log.Timber.tag("RiftPreview").i("Download %s started", pack.id)
                    busyId = pack.id
                    scope.launch {
                        try {
                            DeckThemesClient.install(context, pack)
                            hold()
                            installed = InstalledPacks.list(context).filter { it.music == music }
                            select(pack.id)
                            focusId = pack.id
                            refocus += 1
                            failed = false
                            timber.log.Timber.tag("RiftPreview").i("Download %s done", pack.id)
                        } catch (e: PackError) {
                            timber.log.Timber.tag("RiftPreview").w(e, "Download %s failed", pack.id)
                            failed = true
                        }
                        busyId = null
                    }
                },
                playing = previewing == pack.id,
                busy = previewBusyId == pack.id,
                onPreview = { hear(pack.id, null, pack) },
            )
        }
        if (failed) NoteText(stringResource(R.string.rift_settings_packs_error))
        if (hasMore || failed) {
            LinkRow(
                modifier = Modifier.keepInView().onFocusChanged { if (it.hasFocus && !failed) loadMore() },
                title = stringResource(
                    when {
                        loading -> R.string.rift_settings_packs_loading
                        failed -> R.string.rift_settings_packs_retry
                        else -> R.string.rift_settings_packs_more
                    },
                ),
                onClick = { if (failed) failed = false; loadMore() },
            )
        }
        NoteText(stringResource(R.string.rift_settings_packs_preview_hint))
        NoteText(stringResource(R.string.rift_settings_packs_note))
    }

    removing?.let { pack ->
        RiftConfirmDialog(
            title = stringResource(R.string.rift_settings_packs_remove_title, pack.name),
            message = stringResource(R.string.rift_settings_packs_remove_message),
            confirmText = stringResource(R.string.rift_settings_packs_remove),
            dismissText = stringResource(R.string.rift_settings_cancel),
            onConfirm = {
                hold()
                if (pack.id == selected) select("")
                if (PackPreview.playing == pack.id) PackPreview.stop(context)
                InstalledPacks.remove(context, pack.id)
                installed = InstalledPacks.list(context).filter { it.music == music }
                removing = null
                refocus += 1
            },
            onDismiss = { removing = null },
        )
    }
}

/** A pack row with its play button on the right: D-pad right reaches the button, A on the row chooses or downloads. */
@Composable
private fun PackRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onClick: () -> Unit,
    playing: Boolean,
    onPreview: () -> Unit,
    busy: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.keepInView(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinkRow(
            modifier = Modifier.weight(1f),
            title = title,
            subtitle = subtitle,
            icon = icon,
            value = value,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        RiftRoundButton(
            icon = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            description = stringResource(if (playing) R.string.rift_settings_packs_preview_stop else R.string.rift_settings_packs_preview),
            modifier = Modifier.riftSilentPress(),
            onClick = { if (!busy) onPreview() },
        )
    }
}

/** When anything inside gains focus, scrolls so the whole row shows with a margin above and below, not flush with the edge. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.keepInView(margin: androidx.compose.ui.unit.Dp = 48.dp): Modifier {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { margin.toPx() }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    return this
        .onSizeChanged { size = it }
        .bringIntoViewRequester(requester)
        .onFocusChanged {
            if (it.hasFocus) scope.launch {
                requester.bringIntoView(androidx.compose.ui.geometry.Rect(0f, -px, size.width.toFloat(), size.height + px))
            }
        }
}

private fun selectedId(context: Context, music: Boolean) =
    if (music) RiftSoundSettings.musicPackId(context) else RiftSoundSettings.soundPackId(context)

/** The name shown for the pack in use, or null when nothing is picked. */
internal fun packName(context: Context, id: String): String? = InstalledPacks.get(context, id)?.name

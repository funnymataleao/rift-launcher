package dev.rift.launcher.home

import android.text.format.Formatter
import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.ui.data.DownloadItemState
import app.gamenative.ui.data.DownloadItemStatus
import app.gamenative.ui.model.DownloadsViewModel
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.PlateShape
import dev.rift.launcher.settings.RiftConfirmDialog
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.settings.RiftRoundButton
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow

private val ScreenPadding = 48.dp

/** Lets other screens (the install screen) ask the home to show the downloads page. */
object RiftDownloadsNav {
    val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun open() {
        requests.tryEmit(Unit)
    }
}
private val CoverSize = 72.dp
private val BarHeight = 8.dp

/**
 * The downloads page: every store's downloads (Steam, Epic, GOG, Amazon) in one list, from the same
 * [DownloadsViewModel] the stores feed. Unfinished ones show up after a restart as well, with their game
 * name, so a download started earlier is never lost. A pauses or resumes the focused download, holding A
 * cancels it; the buttons above act on all of them.
 */
@Composable
fun RiftDownloadsScreen(
    onBack: () -> Unit,
    onOpenStorage: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val all = remember(state.downloads) { state.downloads.values.toList() }
    val running = remember(all) { all.filter { !it.isFinished } }
    val finished = remember(all) { all.filter { it.isFinished } }
    val activeCount = remember(all) { all.count { it.canPause } }
    val resumableCount = remember(all) { all.count { it.canResume } }
    var confirmCancelAll by remember { mutableStateOf(false) }
    // Android BackHandler does not receive a gamepad B on every controller. Consume B on this top layer
    // itself, otherwise the still-mounted home underneath hears it and plays/navigates behind Downloads.
    var gamepadBackDown by remember { mutableStateOf(false) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(all.isNotEmpty()) {
        if (all.isEmpty()) return@LaunchedEffect
        repeat(6) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RiftColors.Background)
            .statusBarsPadding()
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                if (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) {
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) gamepadBackDown = true
                if (event.type == KeyEventType.KeyUp && gamepadBackDown) {
                    gamepadBackDown = false
                    onBack()
                }
                true
            },
    ) {
        Column(
            modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RiftRoundButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    description = stringResource(R.string.back),
                    onClick = onBack,
                )
                Column {
                    Text(
                        text = stringResource(R.string.rift_downloads_title),
                        color = RiftColors.OnSurface,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = stringResource(R.string.downloads_summary, activeCount, resumableCount, finished.size),
                        color = RiftColors.OnSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (all.isNotEmpty()) {
                    if (activeCount > 0) {
                        RiftPillButton(
                            text = stringResource(R.string.downloads_pause_all),
                            onClick = viewModel::onPauseAll,
                            icon = Icons.Rounded.Pause,
                            primary = true,
                        )
                    } else {
                        RiftPillButton(
                            text = stringResource(R.string.downloads_resume_all),
                            onClick = viewModel::onResumeAll,
                            icon = Icons.Rounded.PlayArrow,
                            primary = true,
                            enabled = resumableCount > 0,
                        )
                    }
                    RiftPillButton(
                        text = stringResource(R.string.downloads_cancel_all),
                        onClick = { confirmCancelAll = true },
                        enabled = all.any { it.canCancel },
                    )
                    RiftPillButton(
                        text = stringResource(R.string.downloads_clear_finished),
                        onClick = viewModel::onClearFinished,
                        enabled = finished.isNotEmpty(),
                    )
                }
                RiftPillButton(
                    text = stringResource(R.string.settings_storage_manage_title),
                    onClick = onOpenStorage,
                    icon = Icons.Rounded.Storage,
                )
            }
            if (all.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.rift_downloads_hint),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 15.sp,
                )
            }
        }

        if (all.isEmpty()) {
            EmptyDownloads(modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(PlateGap),
            ) {
                if (running.isNotEmpty()) {
                    item(key = "header-running") { GroupHeader(stringResource(R.string.rift_downloads_group_active)) }
                    items(running, key = { it.uniqueId }) { item ->
                        DownloadPlate(
                            item = item,
                            onPrimary = { if (item.canResume) viewModel.onResumeDownload(item) else if (item.canPause) viewModel.onPauseDownload(item) },
                            onCancel = { if (item.canCancel) viewModel.onCancelDownload(item) },
                            modifier = if (item === running.first()) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                if (finished.isNotEmpty()) {
                    item(key = "header-finished") { GroupHeader(stringResource(R.string.rift_downloads_group_finished), top = running.isNotEmpty()) }
                    items(finished, key = { it.uniqueId }) { item ->
                        DownloadPlate(
                            item = item,
                            onPrimary = {},
                            onCancel = {},
                            modifier = if (running.isEmpty() && item === finished.first()) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }

    val confirmation = state.cancelConfirmation
    if (confirmation != null) {
        RiftConfirmDialog(
            title = stringResource(R.string.cancel_download_prompt_title),
            message = stringResource(R.string.downloads_cancel_confirm, confirmation.gameName),
            confirmText = stringResource(R.string.yes),
            dismissText = stringResource(R.string.no),
            onConfirm = viewModel::onConfirmCancel,
            onDismiss = viewModel::onDismissCancel,
        )
    }
    if (confirmCancelAll) {
        RiftConfirmDialog(
            title = stringResource(R.string.cancel_download_prompt_title),
            message = stringResource(R.string.rift_downloads_cancel_all_message),
            confirmText = stringResource(R.string.yes),
            dismissText = stringResource(R.string.no),
            onConfirm = {
                confirmCancelAll = false
                viewModel.onCancelAll()
            },
            onDismiss = { confirmCancelAll = false },
        )
    }
}

@Composable
private fun GroupHeader(title: String, top: Boolean = false) {
    Text(
        text = title,
        color = RiftColors.OnSurface,
        fontSize = 21.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(start = 6.dp, top = if (top) 24.dp else 0.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = null,
            tint = RiftColors.OnSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.rift_downloads_empty_title),
            color = RiftColors.OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = stringResource(R.string.rift_downloads_empty_body),
            color = RiftColors.OnSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadPlate(
    item: DownloadItemState,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val statusText = when (item.status) {
        DownloadItemStatus.DOWNLOADING -> stringResource(R.string.downloading).trimEnd('.', '…')
        DownloadItemStatus.PAUSED -> stringResource(R.string.downloads_status_paused)
        DownloadItemStatus.RESUMABLE -> stringResource(R.string.downloads_resume_available)
        DownloadItemStatus.COMPLETED -> stringResource(R.string.downloads_status_complete)
        DownloadItemStatus.CANCELLED -> stringResource(R.string.downloads_status_cancelled)
        DownloadItemStatus.FAILED -> stringResource(R.string.downloads_status_failed)
    }
    val statusColor = when {
        focused -> plateText(true)
        item.status == DownloadItemStatus.FAILED || item.status == DownloadItemStatus.CANCELLED -> RiftColors.Error
        item.status == DownloadItemStatus.COMPLETED -> RiftColors.Success
        item.status == DownloadItemStatus.DOWNLOADING -> RiftColors.OnSurface
        else -> RiftColors.Warning
    }
    val bytes = if (item.bytesDownloaded != null && item.bytesTotal != null) {
        "${Formatter.formatFileSize(context, item.bytesDownloaded)} / ${Formatter.formatFileSize(context, item.bytesTotal)}"
    } else {
        null
    }
    val details = listOfNotNull(
        sourceName(item.gameSource),
        statusText,
        bytes,
        item.etaMs?.takeIf { item.status == DownloadItemStatus.DOWNLOADING && it > 0 }?.let(::formatEta),
    )
    val note = item.statusMessage?.takeIf { it.isNotBlank() && !it.equals(statusText, ignoreCase = true) && item.status != DownloadItemStatus.DOWNLOADING }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(focused)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = onCancel,
                onClick = onPrimary,
            )
            .heightIn(min = 104.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Cover(item)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = item.gameName,
                    color = plateText(focused),
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                item.progress?.let {
                    Text(
                        text = "${(it * 100).toInt()}%",
                        color = plateText(focused),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item.progress?.let { ProgressBar(it, focused) }
            Text(
                text = details.joinToString(" • "),
                color = if (focused) plateSubtext(true) else if (item.status == DownloadItemStatus.DOWNLOADING) RiftColors.OnSurfaceVariant else statusColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
            if (note != null) {
                Text(text = note, color = plateSubtext(focused), fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3)
            }
        }
        val action: ImageVector? = when {
            item.canResume -> Icons.Rounded.PlayArrow
            item.canPause -> Icons.Rounded.Pause
            else -> null
        }
        if (action != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (focused) Color.Black.copy(alpha = 0.14f) else RiftColors.SurfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = action, contentDescription = null, tint = plateText(focused), modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun Cover(item: DownloadItemState) {
    Box(
        modifier = Modifier
            .size(CoverSize)
            .clip(RoundedCornerShape(14.dp))
            .background(RiftColors.SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (item.iconUrl.isNotBlank()) {
            CoilImage(
                modifier = Modifier.fillMaxSize(),
                imageModel = { item.iconUrl },
                imageOptions = ImageOptions(contentScale = ContentScale.Crop),
                failure = { CoverPlaceholder() },
            )
        } else {
            CoverPlaceholder()
        }
    }
}

@Composable
private fun CoverPlaceholder() {
    Icon(
        imageVector = Icons.Rounded.Download,
        contentDescription = null,
        tint = RiftColors.OnSurfaceVariant,
        modifier = Modifier.size(30.dp),
    )
}

/** The bar fills with the accent while the plate rests and goes dark on the focused (accent) plate. */
@Composable
private fun ProgressBar(progress: Float, focused: Boolean) {
    val shape = RoundedCornerShape(50)
    val track = if (focused) Color.Black.copy(alpha = 0.18f) else RiftColors.SurfaceContainerHigh
    Box(modifier = Modifier.fillMaxWidth().height(BarHeight).clip(shape).background(track)) {
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(BarHeight)
                    .clip(shape)
                    .then(
                        if (focused) Modifier.background(RiftAccent.current.onAccent) else Modifier.background(RiftAccent.current.brush),
                    ),
            )
        }
    }
}

@Composable
private fun sourceName(source: GameSource): String = when (source) {
    GameSource.STEAM -> stringResource(R.string.library_source_steam)
    GameSource.GOG -> stringResource(R.string.tab_gog)
    GameSource.EPIC -> stringResource(R.string.tab_epic)
    GameSource.AMAZON -> stringResource(R.string.tab_amazon)
    GameSource.CUSTOM_GAME -> stringResource(R.string.library_source_custom)
}

private fun formatEta(etaMs: Long): String {
    val totalSeconds = etaMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

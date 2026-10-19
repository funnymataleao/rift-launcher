package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.settings.ContainerStorageManagerTransientUi
import app.gamenative.ui.screen.settings.gameSourceLabel
import app.gamenative.ui.screen.settings.inventorySummaryBytes
import app.gamenative.ui.screen.settings.rememberContainerStorageManagerUiState
import app.gamenative.ui.screen.settings.sizeBreakdown
import app.gamenative.ui.screen.settings.statusLabel
import app.gamenative.ui.screen.settings.storageLocationLabel
import app.gamenative.utils.ContainerStorageManager
import app.gamenative.utils.StorageUtils
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.settings.RiftRoundButton
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay

private val Edge = 48.dp

/**
 * Rift: GameNative's storage manager (every game and container, their size and place, uninstall, remove, move) as a
 * Rift page, like Downloads: the same structure, Rift's plates and buttons, and B leaves it.
 */
@Composable
fun RiftStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state = rememberContainerStorageManagerUiState()
    LaunchedEffect(state) { state.ensureLoaded() }
    ContainerStorageManagerTransientUi(state)

    val backFocus = remember { FocusRequester() }
    var screenFocused by remember { mutableStateOf(false) }
    var keyPressed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(30) {
            if (keyPressed) return@LaunchedEffect
            if (!screenFocused) runCatching { backFocus.requestFocus() }
            delay(50)
        }
    }
    // B leaves wherever the focus is.
    val latestBack = rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        var down = false
        val listener: (AndroidEvent.KeyEvent) -> Boolean = { e ->
            val code = e.event.keyCode
            if (screenFocused || state.isMoving || (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_ESCAPE)) {
                false
            } else {
                if (e.event.action == KeyEvent.ACTION_DOWN) down = true
                if (e.event.action == KeyEvent.ACTION_UP && down) {
                    down = false
                    latestBack.value()
                }
                true
            }
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(listener)
        onDispose { PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(listener) }
    }
    var backDown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RiftColors.Background)
            .statusBarsPadding()
            .onFocusChanged { screenFocused = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) keyPressed = true
                val code = event.nativeKeyEvent.keyCode
                if (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) {
                    return@onPreviewKeyEvent false
                }
                if (state.isMoving) return@onPreviewKeyEvent true
                if (event.type == KeyEventType.KeyDown) backDown = true
                if (event.type == KeyEventType.KeyUp && backDown) {
                    backDown = false
                    onBack()
                }
                true
            },
    ) {
        Row(
            modifier = Modifier.padding(start = Edge, end = Edge, top = 32.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            RiftRoundButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                description = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocus),
            )
            Column {
                Text(
                    text = stringResource(R.string.settings_storage_manage_title),
                    color = RiftColors.OnSurface,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = if (state.isLoading && !state.hasLoaded) {
                        stringResource(R.string.container_storage_loading)
                    } else {
                        stringResource(
                            R.string.container_storage_summary,
                            state.entries.size,
                            StorageUtils.formatBinarySize(inventorySummaryBytes(state.entries)),
                        )
                    },
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Each drive: its name, then free of total.
        if (state.volumeInfo.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = Edge, end = Edge, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.volumeInfo.forEach { volume ->
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(RiftColors.SurfaceContainer)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(volume.label, color = RiftColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${StorageUtils.formatBinarySize(volume.freeBytes)} / ${StorageUtils.formatBinarySize(volume.totalBytes)}",
                            color = RiftColors.OnSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        if (state.hasLoaded && state.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(Edge), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.container_storage_empty), color = RiftColors.OnSurfaceVariant, fontSize = 17.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Edge, end = Edge, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.entries, key = { it.containerId + (it.appId ?: "") }) { entry ->
                StoragePlate(
                    entry = entry,
                    canMoveToExternal = remember(entry) { ContainerStorageManager.canMoveToExternal(context, entry) },
                    canMoveToInternal = remember(entry) { ContainerStorageManager.canMoveToInternal(context, entry) },
                    location = remember(entry) { ContainerStorageManager.getStorageLocation(context, entry) },
                    busy = state.isMoving,
                    onUninstall = { state.requestUninstall(entry) },
                    onRemove = { state.requestRemove(entry) },
                    onMove = { target -> state.startMove(entry, target) },
                )
            }
        }
    }
}

@Composable
private fun StoragePlate(
    entry: ContainerStorageManager.Entry,
    canMoveToExternal: Boolean,
    canMoveToInternal: Boolean,
    location: ContainerStorageManager.StorageLocation,
    busy: Boolean,
    onUninstall: () -> Unit,
    onRemove: () -> Unit,
    onMove: (ContainerStorageManager.MoveTarget) -> Unit,
) {
    val name = entry.displayName.ifBlank { stringResource(R.string.container_storage_unknown_container) }
    val details = listOfNotNull(
        gameSourceLabel(entry.gameSource),
        statusLabel(entry.status),
        storageLocationLabel(location).takeIf { location != ContainerStorageManager.StorageLocation.UNKNOWN },
        entry.combinedSizeBytes?.let { StorageUtils.formatBinarySize(it) },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(RiftColors.SurfaceContainer)
            .heightIn(min = 96.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RiftColors.SurfaceContainerHigh),
        ) {
            if (entry.iconUrl.isNotBlank()) CoverImage(urls = listOf(entry.iconUrl), contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, color = RiftColors.OnSurface, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(details.joinToString(" • "), color = RiftColors.OnSurfaceVariant, fontSize = 15.sp)
            val breakdown = sizeBreakdown(entry)
            if (breakdown.isNotBlank()) Text(breakdown, color = RiftColors.OnSurfaceVariant, fontSize = 13.5.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canMoveToExternal) {
                RiftPillButton(
                    text = stringResource(R.string.container_storage_move_to_external_button),
                    onClick = { onMove(ContainerStorageManager.MoveTarget.EXTERNAL) },
                    icon = Icons.Rounded.DriveFileMove,
                    enabled = !busy,
                )
            }
            if (canMoveToInternal) {
                RiftPillButton(
                    text = stringResource(R.string.container_storage_move_to_internal_button),
                    onClick = { onMove(ContainerStorageManager.MoveTarget.INTERNAL) },
                    icon = Icons.Rounded.DriveFileMove,
                    enabled = !busy,
                )
            }
            if (entry.canUninstallGame) {
                RiftPillButton(
                    text = stringResource(R.string.container_storage_uninstall_button),
                    onClick = onUninstall,
                    icon = Icons.Rounded.Delete,
                    enabled = !busy,
                )
            }
            if (entry.hasContainer) {
                RiftPillButton(
                    text = stringResource(R.string.container_storage_remove_button),
                    onClick = onRemove,
                    icon = Icons.Rounded.DeleteSweep,
                    enabled = !busy,
                )
            }
        }
    }
}

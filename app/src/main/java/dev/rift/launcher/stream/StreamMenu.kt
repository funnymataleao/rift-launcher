package dev.rift.launcher.stream

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.component.QuickMenuAdjustmentRow
import app.gamenative.ui.component.QuickMenuItem
import app.gamenative.ui.component.QuickMenuItemRow
import app.gamenative.ui.component.QuickMenuSectionHeader
import app.gamenative.ui.component.QuickMenuToggleRow
import app.gamenative.ui.util.adaptivePanelWidth
import dev.rift.launcher.settings.rememberRiftPrefs
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftGlassTheme

/** The panel's rows, so a reconnect can give the focus back to the row that was being changed. */
object StreamMenuRow {
    const val END = "end"
    const val OVERLAY = "overlay"
    const val VIBRATION = "vibration"
    const val RESOLUTION = "resolution"
    const val FPS = "fps"
    const val BITRATE = "bitrate"
    const val CODEC = "codec"
    const val HDR = "hdr"
}

/**
 * The in-stream panel on the right, opened with Select + Start: the running game and "End stream",
 * which quits the game on the PC so the host lets go of its virtual display, then the stream settings.
 * B closes it. When the stream broke, the panel says why and offers to quit the game on the PC and
 * start it again.
 *
 * Drawn with the in-game quick menu's shell and rows (black glass, plates filled with the accent when
 * focused, sliders with steps). Over the video there is nothing to blur, so the glass is its solid black.
 * Picture settings take a new connection: [onPictureChange] gets the row, and the stream reconnects with
 * them a moment after the last change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StreamMenu(
    isOpen: Boolean,
    appName: String,
    pcName: String,
    onDismiss: () -> Unit,
    onEndStream: () -> Unit,
    onPerfOverlayChange: ((Boolean) -> Unit)? = null,
    onPictureChange: ((String) -> Unit)? = null,
    error: String? = null,
    onRelaunch: (() -> Unit)? = null,
    focusRow: String = StreamMenuRow.END,
    maxFps: Int = 60,
    screenSize: Pair<Int, Int>? = null,
    ownWindow: Boolean = false,
) = RiftGlassTheme {
    Box(modifier = Modifier.fillMaxSize()) {
        // In a window of its own the window is the glass, slides in and closes on a tap outside.
        AnimatedVisibility(
            visible = isOpen && !ownWindow,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = if (ownWindow) EnterTransition.None else slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit = if (ownWindow) ExitTransition.None else slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val requesters = remember { mutableMapOf<String, FocusRequester>() }
            fun requester(row: String) = requesters.getOrPut(row) { FocusRequester() }
            var focused by remember { mutableStateOf(false) }
            val relaunch = error != null && onRelaunch != null
            // After an error the first button ("Quit and start again") holds the focus.
            val first = if (relaunch) RELAUNCH else focusRow
            Surface(
                modifier = Modifier
                    .then(if (ownWindow) Modifier.fillMaxSize() else Modifier.width(adaptivePanelWidth(440.dp)).fillMaxHeight().blackGlass())
                    .onFocusChanged {
                        focused = it.hasFocus
                        Log.i("RiftStream", "panel focus=${it.hasFocus}")
                    },
                shape = RectangleShape,
                color = Color.Transparent,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = pcName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (error != null) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    if (relaunch) {
                        QuickMenuItemRow(
                            item = QuickMenuItem(0, Icons.Rounded.RestartAlt, R.string.rift_stream_relaunch),
                            onClick = onRelaunch!!,
                            focusRequester = requester(RELAUNCH),
                        )
                    }
                    QuickMenuItemRow(
                        item = QuickMenuItem(1, Icons.Rounded.PowerSettingsNew, R.string.rift_stream_end),
                        onClick = onEndStream,
                        focusRequester = requester(StreamMenuRow.END),
                    )
                    // PC streams only, and only while the stream runs: the cloud stream has its own settings.
                    if (error == null && onPerfOverlayChange != null && onPictureChange != null) {
                        StreamQuickSettings(
                            requester = ::requester,
                            onPerfOverlayChange = onPerfOverlayChange,
                            onPictureChange = onPictureChange,
                            maxFps = maxFps,
                            screenSize = screenSize,
                        )
                    }
                }
            }
            val view = LocalView.current
            LaunchedEffect(Unit) {
                // The panel is composed a frame after it opens; until a row holds the focus, the view
                // leaves touch mode and the row asks for it again, frame by frame.
                repeat(FOCUS_ATTEMPTS) { attempt ->
                    withFrameNanos { }
                    if (focused) return@LaunchedEffect
                    if (view.isInTouchMode) view.requestFocusFromTouch()
                    runCatching { (requesters[first] ?: requester(StreamMenuRow.END)).requestFocus() }
                    Log.i("RiftStream", "panel focus attempt $attempt row=$first touch mode=${view.isInTouchMode} view focus=${view.hasFocus()}")
                }
            }
        }
    }
}

/**
 * The stream settings from Rift's settings. The overlay and vibration apply at once; the picture
 * settings are saved and handed to [onPictureChange], which reconnects the stream with them.
 */
@Composable
private fun StreamQuickSettings(
    requester: (String) -> FocusRequester,
    onPerfOverlayChange: (Boolean) -> Unit,
    onPictureChange: (String) -> Unit,
    maxFps: Int,
    screenSize: Pair<Int, Int>?,
) {
    val prefs = rememberRiftPrefs()
    val accent = RiftAccent.current.main
    var overlay by remember { mutableStateOf(prefs.streamPerfOverlay) }
    var vibration by remember { mutableStateOf(prefs.streamGamepadVibration) }
    var resolution by remember { mutableStateOf(prefs.streamResolution) }
    var fps by remember { mutableStateOf(prefs.streamFps) }
    var bitrate by remember { mutableStateOf(prefs.streamBitrateMbps) }
    var codec by remember { mutableStateOf(prefs.streamCodec) }
    var hdr by remember { mutableStateOf(prefs.streamHdr) }

    QuickMenuSectionHeader(title = stringResource(R.string.rift_stream_settings), modifier = Modifier.padding(top = 12.dp))
    QuickMenuToggleRow(
        title = stringResource(R.string.rift_settings_stream_overlay),
        enabled = overlay,
        onToggle = {
            overlay = !overlay
            prefs.streamPerfOverlay = overlay
            onPerfOverlayChange(overlay)
        },
        accentColor = accent,
        focusRequester = requester(StreamMenuRow.OVERLAY),
    )
    QuickMenuToggleRow(
        title = stringResource(R.string.rift_settings_stream_vibration),
        enabled = vibration,
        onToggle = {
            vibration = !vibration
            prefs.streamGamepadVibration = vibration
        },
        accentColor = accent,
        focusRequester = requester(StreamMenuRow.VIBRATION),
    )

    // The picture is set when a session starts, so these reconnect the stream (the game keeps running).
    QuickMenuSectionHeader(
        title = stringResource(R.string.rift_settings_group_stream_video),
        subtitle = stringResource(R.string.rift_stream_settings_picture_note),
        modifier = Modifier.padding(top = 12.dp),
    )
    // Smallest to largest picture; "Screen" is the tablet's own size, wherever that falls.
    val screenLabel = stringResource(R.string.rift_settings_stream_device_screen)
    val resolutions = remember(screenSize) {
        listOf("1280x720" to "720p", "1920x1080" to "1080p", "2560x1440" to "1440p", "3840x2160" to "4K")
            .plus("device" to "")
            .sortedBy { (value, _) -> (StreamSettings.sizeOf(value) ?: screenSize)?.let { it.first.toLong() * it.second } ?: 0L }
    }
    StepRow(
        title = stringResource(R.string.rift_settings_stream_resolution),
        steps = resolutions.map { it.first },
        value = resolution,
        label = { value -> resolutions.first { it.first == value }.second.ifEmpty { screenLabel } },
        onChange = {
            resolution = it
            prefs.streamResolution = it
            onPictureChange(StreamMenuRow.RESOLUTION)
        },
        accent = accent,
        focusRequester = requester(StreamMenuRow.RESOLUTION),
    )
    // 0 is the screen's own rate, the most the tablet shows.
    val rates = remember(maxFps) { (listOf(30, 45, 60, 90, 120, 165).filter { it < maxFps } + maxFps).distinct() }
    StepRow(
        title = stringResource(R.string.rift_settings_stream_fps),
        steps = rates,
        value = if (fps <= 0) maxFps else fps,
        label = { "$it" },
        onChange = {
            fps = if (it == maxFps) 0 else it
            prefs.streamFps = fps
            onPictureChange(StreamMenuRow.FPS)
        },
        accent = accent,
        focusRequester = requester(StreamMenuRow.FPS),
    )
    val auto = stringResource(R.string.rift_settings_auto)
    StepRow(
        title = stringResource(R.string.rift_settings_stream_bitrate),
        steps = listOf(0, 10, 20, 30, 40, 60, 80, 100, 150),
        value = bitrate,
        label = { if (it == 0) auto else "$it" },
        onChange = {
            bitrate = it
            prefs.streamBitrateMbps = it
            onPictureChange(StreamMenuRow.BITRATE)
        },
        accent = accent,
        focusRequester = requester(StreamMenuRow.BITRATE),
    )
    val codecNames = mapOf("auto" to auto, "h264" to "H.264", "hevc" to "HEVC", "av1" to "AV1")
    StepRow(
        title = stringResource(R.string.rift_settings_stream_codec),
        steps = codecNames.keys.toList(),
        value = codec,
        label = { codecNames.getValue(it) },
        onChange = {
            codec = it
            prefs.streamCodec = it
            onPictureChange(StreamMenuRow.CODEC)
        },
        accent = accent,
        focusRequester = requester(StreamMenuRow.CODEC),
    )
    QuickMenuToggleRow(
        title = stringResource(R.string.rift_settings_stream_hdr),
        enabled = hdr,
        onToggle = {
            hdr = !hdr
            prefs.streamHdr = hdr
            onPictureChange(StreamMenuRow.HDR)
        },
        accentColor = accent,
        focusRequester = requester(StreamMenuRow.HDR),
    )
}

/** A slider over fixed [steps]: A grabs it, left and right step, B lets go (the quick menu's row). */
@Composable
private fun <T> StepRow(
    title: String,
    steps: List<T>,
    value: T,
    label: (T) -> String,
    onChange: (T) -> Unit,
    accent: Color,
    focusRequester: FocusRequester,
) {
    val index = steps.indexOf(value).coerceAtLeast(0)
    QuickMenuAdjustmentRow(
        title = title,
        valueText = label(steps[index]),
        progress = if (steps.size > 1) index / (steps.size - 1f) else 1f,
        onDecrease = { if (index > 0) onChange(steps[index - 1]) },
        onIncrease = { if (index < steps.lastIndex) onChange(steps[index + 1]) },
        accentColor = accent,
        focusRequester = focusRequester,
    )
}

private const val RELAUNCH = "relaunch"
private const val FOCUS_ATTEMPTS = 20

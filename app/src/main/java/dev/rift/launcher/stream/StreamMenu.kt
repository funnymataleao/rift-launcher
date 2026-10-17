package dev.rift.launcher.stream

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.R
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import dev.rift.launcher.home.CoverImage
import dev.rift.launcher.settings.PlateShape
import app.gamenative.ui.component.QuickMenuAdjustmentRow
import app.gamenative.ui.component.QuickMenuChoiceChip
import app.gamenative.ui.component.QuickMenuItem
import app.gamenative.ui.component.QuickMenuItemRow
import app.gamenative.ui.component.QuickMenuRailActionButton
import app.gamenative.ui.component.QuickMenuTabButton
import app.gamenative.ui.component.QuickMenuToggleRow
import app.gamenative.ui.util.adaptivePanelWidth
import dev.rift.launcher.settings.rememberRiftPrefs
import dev.rift.launcher.ui.DrawerBackdrop
import dev.rift.launcher.ui.RiftDrawer
import dev.rift.launcher.ui.RiftDrawerPanel
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

    /** The first row of a cloud game's settings, where the panel opens. */
    const val CLOUD_FIRST = "cloud_first"
}

/**
 * The in-stream panel on the right, opened with Select + Start: the running game and "End stream",
 * which quits the game on the PC so the host lets go of its virtual display, then the stream settings.
 * B closes it. When the stream broke, the panel says why and offers to quit the game on the PC and
 * start it again.
 *
 * Drawn in the frame of every Rift panel ([RiftDrawerPanel]) with the in-game quick menu's rows (plates
 * filled with the accent when focused, sliders with steps). Its glass blurs [backdrop], a copy of what is
 * behind the panel (the stream, or the cloud game's page).
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
    extraSettings: (@Composable (requester: (String) -> FocusRequester) -> Unit)? = null,
    coverUrls: List<String> = emptyList(),
    backdrop: DrawerBackdrop? = null,
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
            // PC streams have their picture and stream tabs while the stream runs; a cloud game brings its own.
            val tabs = when {
                error != null -> emptyList()
                onPerfOverlayChange != null && onPictureChange != null -> listOf(StreamTab.PICTURE, StreamTab.STREAM)
                extraSettings != null -> listOf(StreamTab.CLOUD)
                else -> emptyList()
            }
            var tab by remember { mutableStateOf(StreamTab.of(focusRow).takeIf { it in tabs } ?: tabs.firstOrNull()) }
            val accent = RiftAccent.current.main
            // The frame and glass of every Rift panel; in a window of its own the window is the panel.
            RiftDrawerPanel(
                modifier = Modifier
                    .then(if (ownWindow) Modifier.fillMaxSize() else Modifier.width(adaptivePanelWidth(RiftDrawer.Width)).fillMaxHeight())
                    .onFocusChanged {
                        focused = it.hasFocus
                        Log.i("RiftStream", "panel focus=${it.hasFocus}")
                    },
                backdrop = backdrop,
                rowInset = PLATE_INSET,
                // The tab rail on the outer edge with "End stream" at its foot.
                rail = {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // One tab needs no buttons; with more, only the focused thing is filled with
                        // the accent and the open tab stays a quiet grey.
                        if (tabs.size > 1) {
                            tabs.forEach { option ->
                                QuickMenuTabButton(
                                    icon = option.icon,
                                    contentDescriptionResId = option.title,
                                    selected = tab == option,
                                    accentColor = accent,
                                    onSelected = { tab = option },
                                    quietSelection = true,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    QuickMenuRailActionButton(
                        item = QuickMenuItem(1, Icons.Rounded.PowerSettingsNew, R.string.rift_stream_end),
                        onClick = onEndStream,
                        focusRequester = requester(StreamMenuRow.END),
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // The game on top (Denis): its wide cover across the panel, its name, then the PC.
                    // It scrolls with the rows, so a short screen still reaches every setting.
                    StreamHeader(
                        appName = appName,
                        pcName = pcName,
                        coverUrls = coverUrls,
                        cloud = extraSettings != null,
                    )
                    if (error != null) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = CONTENT_INSET, vertical = 8.dp),
                        )
                    }
                    if (relaunch) {
                        QuickMenuItemRow(
                            item = QuickMenuItem(0, Icons.Rounded.RestartAlt, R.string.rift_stream_relaunch),
                            onClick = onRelaunch!!,
                            focusRequester = requester(RELAUNCH),
                        )
                    }
                    when (tab) {
                        StreamTab.PICTURE -> PictureSettings(::requester, onPictureChange!!, maxFps, screenSize)
                        StreamTab.STREAM -> StreamSettingsTab(::requester, onPerfOverlayChange!!)
                        StreamTab.CLOUD -> extraSettings!!(::requester)
                        null -> Unit
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

/** The rail's tabs and the rows each one holds, so a reconnect opens the tab of the row being changed. */
private enum class StreamTab(val title: Int, val icon: ImageVector) {
    PICTURE(R.string.rift_settings_group_stream_video, Icons.Rounded.Tv),
    STREAM(R.string.rift_stream_tab_stream, Icons.Rounded.Tune),
    CLOUD(R.string.rift_stream_settings, Icons.Rounded.Tune);

    companion object {
        fun of(row: String) = when (row) {
            StreamMenuRow.OVERLAY, StreamMenuRow.VIBRATION -> STREAM
            StreamMenuRow.CLOUD_FIRST -> CLOUD
            StreamMenuRow.END -> null
            else -> PICTURE
        }
    }
}

/** What changes on the fly: the overlay and the pad's vibration. */
@Composable
private fun StreamSettingsTab(
    requester: (String) -> FocusRequester,
    onPerfOverlayChange: (Boolean) -> Unit,
) {
    val prefs = rememberRiftPrefs()
    val accent = RiftAccent.current.main
    var overlay by remember { mutableStateOf(prefs.streamPerfOverlay) }
    var vibration by remember { mutableStateOf(prefs.streamGamepadVibration) }
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
}

/**
 * The picture, set when a session starts: a change is saved and handed to [onPictureChange], which
 * reconnects the stream with it (the game keeps running on the PC).
 */
@Composable
private fun PictureSettings(
    requester: (String) -> FocusRequester,
    onPictureChange: (String) -> Unit,
    maxFps: Int,
    screenSize: Pair<Int, Int>?,
) {
    val prefs = rememberRiftPrefs()
    val accent = RiftAccent.current.main
    var resolution by remember { mutableStateOf(prefs.streamResolution) }
    var fps by remember { mutableStateOf(prefs.streamFps) }
    var bitrate by remember { mutableStateOf(prefs.streamBitrateMbps) }
    var codec by remember { mutableStateOf(prefs.streamCodec) }
    var hdr by remember { mutableStateOf(prefs.streamHdr) }

    // Smallest to largest picture; "Screen" is the tablet's own size, wherever that falls.
    val screenLabel = stringResource(R.string.rift_stream_resolution_screen)
    val resolutions = remember(screenSize) {
        listOf("1280x720" to "720p", "1920x1080" to "1080p", "2560x1440" to "1440p", "3840x2160" to "4K")
            .plus("device" to "")
            .sortedBy { (value, _) -> (StreamSettings.sizeOf(value) ?: screenSize)?.let { it.first.toLong() * it.second } ?: 0L }
    }
    ChoiceRow(
        title = stringResource(R.string.rift_settings_stream_resolution),
        choices = resolutions.map { it.first },
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
    val mbps = stringResource(R.string.rift_stream_mbps)
    StepRow(
        title = stringResource(R.string.rift_stream_bitrate),
        steps = BITRATES,
        value = bitrate,
        label = { if (it == 0) auto else mbps.format(it) },
        onChange = {
            bitrate = it
            prefs.streamBitrateMbps = it
            onPictureChange(StreamMenuRow.BITRATE)
        },
        accent = accent,
        focusRequester = requester(StreamMenuRow.BITRATE),
    )
    val codecNames = mapOf("auto" to auto, "h264" to "H.264", "hevc" to "HEVC", "av1" to "AV1")
    ChoiceRow(
        title = stringResource(R.string.rift_settings_stream_codec),
        choices = codecNames.keys.toList(),
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
internal fun <T> StepRow(
    title: String,
    steps: List<T>,
    value: T,
    label: (T) -> String,
    onChange: (T) -> Unit,
    accent: Color,
    focusRequester: FocusRequester,
) {
    val index = steps.indexOf(value).coerceAtLeast(0)
    // The value always stands by the title, as in the Steam Deck's quick access menu.
    QuickMenuAdjustmentRow(
        title = "$title · ${label(steps[index])}",
        valueText = "",
        progress = if (steps.size > 1) index / (steps.size - 1f) else 1f,
        onDecrease = { if (index > 0) onChange(steps[index - 1]) },
        onIncrease = { if (index < steps.lastIndex) onChange(steps[index + 1]) },
        accentColor = accent,
        focusRequester = focusRequester,
    )
}

/** A few fixed [choices] as pills in a row: the d-pad moves between them, A picks one. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    choices: List<T>,
    value: T,
    label: (T) -> String,
    onChange: (T) -> Unit,
    accent: Color,
    focusRequester: FocusRequester,
) {
    // On the same lines as the quick menu's rows: their text and controls start and end CONTENT_INSET
    // from the panel's column, with the same space above, below and under the title.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTENT_INSET, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // The panel comes back to this row on the chosen pill.
        val chosen = choices.indexOf(value).coerceAtLeast(0)
        // Pills of one width on a grid, so the row ends where the others do and no label is cut:
        // more than four take two lines of three.
        val columns = if (choices.size > 4) 3 else choices.size
        choices.chunked(columns).forEachIndexed { line, items ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { column, choice ->
                    val index = line * columns + column
                    QuickMenuChoiceChip(
                        text = label(choice),
                        selected = index == chosen,
                        accentColor = accent,
                        onClick = { if (choice != value) onChange(choice) },
                        modifier = Modifier.weight(1f),
                        focusRequester = if (index == chosen) focusRequester else null,
                    )
                }
                repeat(columns - items.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The top of the panel: the game's wide cover across the column (when it has one), its name, and the
 * PC (or the cloud service) it runs on, on the same left line as the rows below.
 */
@Composable
private fun StreamHeader(appName: String, pcName: String, coverUrls: List<String>, cloud: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (coverUrls.any { it.isNotBlank() }) {
            Box(
                modifier = Modifier
                    .padding(horizontal = PLATE_INSET)
                    .fillMaxWidth()
                    .aspectRatio(WIDE_COVER_RATIO)
                    .clip(PlateShape)
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                CoverImage(urls = coverUrls, contentDescription = appName)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = CONTENT_INSET),
        )
        if (pcName.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.padding(horizontal = CONTENT_INSET),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (cloud) Icons.Rounded.Cloud else Icons.Rounded.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = pcName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Where the quick menu's plates (and the cover) start and end in the panel's column. */
private val PLATE_INSET = 8.dp

/** Where the rows' text and controls start and end: the plate's edge plus its padding. */
private val CONTENT_INSET = 24.dp

/** Steam's wide header picture, 460 x 215. */
private const val WIDE_COVER_RATIO = 460f / 215f

/** Bitrates in Mbps, 0 for automatic: fine steps low down, wider up to what Wi-Fi 7 carries. */
private val BITRATES = listOf(0, 5, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100, 120, 140, 160, 180, 200, 250, 300, 350, 400, 450, 500)

private const val RELAUNCH = "relaunch"
private const val FOCUS_ATTEMPTS = 20

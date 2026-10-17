package dev.rift.launcher.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.screen.library.components.SystemMenuItem
import app.gamenative.ui.util.adaptivePanelWidth
import dev.rift.launcher.ui.theme.RiftGlassTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.rift.launcher.settings.ChoiceChipsRow
import dev.rift.launcher.settings.SettingsGroup
import dev.rift.launcher.settings.SwitchRow
import dev.rift.launcher.settings.rememberRiftPrefs

/**
 * The in-stream panel on the right, opened with Select + Start: the running game and "End stream",
 * which quits the game on the PC so the host lets go of its virtual display, then quick stream
 * settings. B closes it.
 * Drawn like Rift's other side panels (GameNative's system menu in the glass theme).
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
    onRestart: (() -> Unit)? = null,
) = RiftGlassTheme {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit = slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val endFocus = remember { FocusRequester() }
            Surface(
                modifier = Modifier.width(adaptivePanelWidth(420.dp)).fillMaxHeight(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column {
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
                    }
                    SystemMenuItem(
                        text = stringResource(R.string.rift_stream_end),
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = onEndStream,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = endFocus,
                    )
                    // PC streams only: the cloud stream has its own settings in the page.
                    if (onPerfOverlayChange != null && onRestart != null) {
                        StreamQuickSettings(onPerfOverlayChange = onPerfOverlayChange, onRestart = onRestart)
                    }
                }
            }
            LaunchedEffect(Unit) {
                // One frame first, so the panel is laid out in the focused view before it takes the focus.
                withFrameNanos { }
                runCatching { endFocus.requestFocus() }
            }
        }
    }
}

/**
 * The stream settings from Rift's settings, in the same rows. The overlay and vibration apply at once;
 * picture settings need a new connection, so they apply with "Apply and restart stream".
 */
@Composable
private fun StreamQuickSettings(onPerfOverlayChange: (Boolean) -> Unit, onRestart: () -> Unit) {
    val prefs = rememberRiftPrefs()
    var overlay by remember { mutableStateOf(prefs.streamPerfOverlay) }
    var vibration by remember { mutableStateOf(prefs.streamGamepadVibration) }
    // What the running stream was started with; the panel lives as long as the stream.
    val started = remember { listOf(prefs.streamResolution, prefs.streamFps, prefs.streamBitrateMbps, prefs.streamCodec, prefs.streamHdr) }
    var resolution by remember { mutableStateOf(prefs.streamResolution) }
    var fps by remember { mutableStateOf(prefs.streamFps) }
    var bitrate by remember { mutableStateOf(prefs.streamBitrateMbps) }
    var codec by remember { mutableStateOf(prefs.streamCodec) }
    var hdr by remember { mutableStateOf(prefs.streamHdr) }
    val restartNeeded = listOf(resolution, fps, bitrate, codec, hdr) != started

    SettingsGroup(title = stringResource(R.string.rift_stream_settings_now)) {
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_overlay),
            icon = Icons.Rounded.Insights,
            checked = overlay,
            onCheckedChange = {
                overlay = it
                prefs.streamPerfOverlay = it
                onPerfOverlayChange(it)
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_vibration),
            icon = Icons.Rounded.Vibration,
            checked = vibration,
            onCheckedChange = {
                vibration = it
                prefs.streamGamepadVibration = it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_stream_settings_restart)) {
        val resolutions = listOf("1280x720", "1920x1080", "2560x1440", "3840x2160", "device")
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_resolution),
            icon = Icons.Rounded.AspectRatio,
            options = listOf("720p", "1080p", "1440p", "4K", stringResource(R.string.rift_settings_stream_device_screen)),
            selectedIndex = resolutions.indexOf(resolution),
            onSelect = {
                resolution = resolutions[it]
                prefs.streamResolution = resolution
            },
        )
        val rates = listOf(30, 60, 90, 120, 144, 165)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_fps),
            icon = Icons.Rounded.Speed,
            options = rates.map { "$it" },
            selectedIndex = rates.indexOf(fps),
            onSelect = {
                fps = rates[it]
                prefs.streamFps = fps
            },
        )
        val bitrates = listOf(0, 10, 20, 40, 60, 80, 100)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_bitrate),
            icon = Icons.Rounded.HighQuality,
            options = bitrates.map { if (it == 0) stringResource(R.string.rift_settings_auto) else "$it" },
            selectedIndex = bitrates.indexOf(bitrate),
            onSelect = {
                bitrate = bitrates[it]
                prefs.streamBitrateMbps = bitrate
            },
        )
        val codecs = listOf("auto", "h264", "hevc", "av1")
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_codec),
            icon = Icons.Rounded.Movie,
            options = listOf(stringResource(R.string.rift_settings_auto), "H.264", "HEVC", "AV1"),
            selectedIndex = codecs.indexOf(codec),
            onSelect = {
                codec = codecs[it]
                prefs.streamCodec = codec
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_hdr),
            icon = Icons.Rounded.Hd,
            checked = hdr,
            onCheckedChange = {
                hdr = it
                prefs.streamHdr = it
            },
        )
    }

    if (restartNeeded) {
        SystemMenuItem(
            text = stringResource(R.string.rift_stream_restart),
            icon = Icons.Rounded.RestartAlt,
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/**
 * How the picture and sound come from a PC through Artemis. The values are kept in [RiftPrefs] under the
 * names Artemis' stream configuration uses, ready for the streaming screen to read when it starts a session.
 */
@Composable
internal fun StreamingSettings(onOpenPcPairing: (() -> Unit)?) {
    val prefs = rememberRiftPrefs()

    var resolution by remember { mutableStateOf(prefs.streamResolution) }
    var fps by remember { mutableStateOf(prefs.streamFps) }
    var bitrate by remember { mutableStateOf(prefs.streamBitrateMbps) }
    var codec by remember { mutableStateOf(prefs.streamCodec) }
    var hdr by remember { mutableStateOf(prefs.streamHdr) }
    var fullRange by remember { mutableStateOf(prefs.streamFullRange) }
    var pacing by remember { mutableStateOf(prefs.streamFramePacing) }
    var lowLatency by remember { mutableStateOf(prefs.streamUltraLowLatency) }
    var adaptive by remember { mutableStateOf(prefs.streamAdaptiveResolution) }
    var overlay by remember { mutableStateOf(prefs.streamPerfOverlay) }
    var audio by remember { mutableStateOf(prefs.streamAudio) }
    var localAudio by remember { mutableStateOf(prefs.streamPlayLocalAudio) }
    var connection by remember { mutableStateOf(prefs.streamConnection) }
    var sops by remember { mutableStateOf(prefs.streamOptimizeGameSettings) }
    var virtualDisplay by remember { mutableStateOf(prefs.streamVirtualDisplay) }
    var gamepads by remember { mutableStateOf(prefs.streamGamepadCount) }
    var keepGamepads by remember { mutableStateOf(prefs.streamKeepGamepads) }

    if (onOpenPcPairing != null) {
        SettingsGroup {
            LinkRow(
                title = stringResource(R.string.rift_settings_stream_add_pc),
                subtitle = stringResource(R.string.rift_settings_stream_add_pc_summary),
                icon = Icons.Rounded.Computer,
                onClick = onOpenPcPairing,
            )
        }
    }

    NoteText(stringResource(R.string.rift_settings_stream_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_video)) {
        val resolutions = listOf("1280x720", "1920x1080", "2560x1440", "3840x2160", "device")
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_resolution),
            subtitle = stringResource(R.string.rift_settings_stream_resolution_summary),
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
            subtitle = stringResource(R.string.rift_settings_stream_bitrate_summary),
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
            subtitle = stringResource(R.string.rift_settings_stream_codec_summary),
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
            subtitle = stringResource(R.string.rift_settings_stream_hdr_summary),
            icon = Icons.Rounded.Hd,
            checked = hdr,
            onCheckedChange = {
                hdr = it
                prefs.streamHdr = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_full_range),
            subtitle = stringResource(R.string.rift_settings_stream_full_range_summary),
            icon = Icons.Rounded.Monitor,
            checked = fullRange,
            onCheckedChange = {
                fullRange = it
                prefs.streamFullRange = it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_latency)) {
        val pacings = listOf(0, 1, 2, 3)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_pacing),
            subtitle = stringResource(R.string.rift_settings_stream_pacing_summary),
            icon = Icons.Rounded.Tune,
            options = listOf(
                stringResource(R.string.rift_settings_pacing_latency),
                stringResource(R.string.rift_settings_pacing_balanced),
                stringResource(R.string.rift_settings_pacing_cap),
                stringResource(R.string.rift_settings_pacing_smooth),
            ),
            selectedIndex = pacings.indexOf(pacing),
            onSelect = {
                pacing = pacings[it]
                prefs.streamFramePacing = pacing
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_low_latency),
            subtitle = stringResource(R.string.rift_settings_stream_low_latency_summary),
            icon = Icons.Rounded.Bolt,
            checked = lowLatency,
            onCheckedChange = {
                lowLatency = it
                prefs.streamUltraLowLatency = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_adaptive),
            subtitle = stringResource(R.string.rift_settings_stream_adaptive_summary),
            icon = Icons.Rounded.Wifi,
            checked = adaptive,
            onCheckedChange = {
                adaptive = it
                prefs.streamAdaptiveResolution = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_overlay),
            subtitle = stringResource(R.string.rift_settings_stream_overlay_summary),
            icon = Icons.Rounded.Insights,
            checked = overlay,
            onCheckedChange = {
                overlay = it
                prefs.streamPerfOverlay = it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_audio)) {
        val layouts = listOf("stereo", "5.1", "7.1")
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_audio),
            icon = Icons.Rounded.GraphicEq,
            options = listOf(stringResource(R.string.rift_settings_stream_stereo), "5.1", "7.1"),
            selectedIndex = layouts.indexOf(audio),
            onSelect = {
                audio = layouts[it]
                prefs.streamAudio = audio
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_local_audio),
            subtitle = stringResource(R.string.rift_settings_stream_local_audio_summary),
            icon = Icons.Rounded.VolumeUp,
            checked = localAudio,
            onCheckedChange = {
                localAudio = it
                prefs.streamPlayLocalAudio = it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_host)) {
        val connections = listOf("auto", "local", "remote")
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_connection),
            subtitle = stringResource(R.string.rift_settings_stream_connection_summary),
            icon = Icons.Rounded.Router,
            options = listOf(
                stringResource(R.string.rift_settings_auto),
                stringResource(R.string.rift_settings_stream_local),
                stringResource(R.string.rift_settings_stream_remote),
            ),
            selectedIndex = connections.indexOf(connection),
            onSelect = {
                connection = connections[it]
                prefs.streamConnection = connection
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_sops),
            subtitle = stringResource(R.string.rift_settings_stream_sops_summary),
            icon = Icons.Rounded.Dns,
            checked = sops,
            onCheckedChange = {
                sops = it
                prefs.streamOptimizeGameSettings = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_virtual_display),
            subtitle = stringResource(R.string.rift_settings_stream_virtual_display_summary),
            icon = Icons.Rounded.Cast,
            checked = virtualDisplay,
            onCheckedChange = {
                virtualDisplay = it
                prefs.streamVirtualDisplay = it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_gamepad)) {
        val counts = listOf(0, 1, 2, 3, 4)
        ChoiceChipsRow(
            title = stringResource(R.string.rift_settings_stream_gamepads),
            subtitle = stringResource(R.string.rift_settings_stream_gamepads_summary),
            icon = Icons.Rounded.Gamepad,
            options = counts.map { if (it == 0) stringResource(R.string.rift_settings_auto) else "$it" },
            selectedIndex = counts.indexOf(gamepads),
            onSelect = {
                gamepads = counts[it]
                prefs.streamGamepadCount = gamepads
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_keep_gamepads),
            subtitle = stringResource(R.string.rift_settings_stream_keep_gamepads_summary),
            icon = Icons.Rounded.Gamepad,
            checked = keepGamepads,
            onCheckedChange = {
                keepGamepads = it
                prefs.streamKeepGamepads = it
            },
        )
    }
}

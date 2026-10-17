package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rift.launcher.home.RiftHiddenGames
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.rift.launcher.stream.PcStreaming
import dev.rift.launcher.stream.rememberPcStreamingTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var vibration by remember { mutableStateOf(prefs.streamGamepadVibration) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Null until the first read, so the PC block appears once in its final shape instead of jumping.
    var pairedPcs by remember { mutableStateOf<List<String>?>(null) }
    DisposableEffect(lifecycleOwner) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { pairedPcs = withContext(Dispatchers.IO) { runCatching { PcStreaming.pairedHostNames(context) }.getOrDefault(emptyList()) } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
        }
    }
    // Two tabs: the stream settings, in Artemis' order, and the PCs with their games.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SectionTabs(
        tabs = listOf(stringResource(R.string.rift_settings_stream_tab_settings), stringResource(R.string.rift_settings_stream_tab_games)),
        selectedIndex = tab,
        onSelect = { tab = it },
    )
    if (tab == 1) {
        pairedPcs?.let { pcs -> GamesTab(pcs, onOpenPcPairing) }
        return
    }

    NoteText(stringResource(R.string.rift_settings_stream_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_video)) {
        val resolutions = listOf("1280x720", "1920x1080", "2560x1440", "3840x2160", "device")
        StepSliderRow(
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
        // 0: the screen's own rate, the highest the tablet shows, so it ends the scale.
        val rates = listOf(30, 60, 90, 120, 144, 165, 0)
        val screen = stringResource(R.string.rift_settings_stream_device_screen)
        StepSliderRow(
            title = stringResource(R.string.rift_settings_stream_fps),
            icon = Icons.Rounded.Speed,
            options = rates.map { if (it == 0) screen else "$it" },
            selectedIndex = rates.indexOf(fps),
            onSelect = {
                fps = rates[it]
                prefs.streamFps = fps
            },
        )
        val bitrates = listOf(0, 20, 50, 100, 150, 200, 300, 500)
        StepSliderRow(
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
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_audio)) {
        val layouts = listOf("stereo", "5.1", "7.1")
        StepSliderRow(
            title = stringResource(R.string.rift_settings_stream_audio),
            icon = Icons.Rounded.GraphicEq,
            options = listOf(stringResource(R.string.rift_settings_stream_stereo), "5.1", "7.1"),
            selectedIndex = layouts.indexOf(audio),
            onSelect = {
                audio = layouts[it]
                prefs.streamAudio = audio
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_gamepad)) {
        val counts = listOf(0, 1, 2, 3, 4)
        StepSliderRow(
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
        SwitchRow(
            title = stringResource(R.string.rift_settings_stream_vibration),
            subtitle = stringResource(R.string.rift_settings_stream_vibration_summary),
            icon = Icons.Rounded.Vibration,
            checked = vibration,
            onCheckedChange = {
                vibration = it
                prefs.streamGamepadVibration = it
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
            title = stringResource(R.string.rift_settings_stream_local_audio),
            subtitle = stringResource(R.string.rift_settings_stream_local_audio_summary),
            icon = Icons.Rounded.VolumeUp,
            checked = localAudio,
            onCheckedChange = {
                localAudio = it
                prefs.streamPlayLocalAudio = it
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

    SettingsGroup(title = stringResource(R.string.rift_settings_group_stream_advanced)) {
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
}

/** Vibepollo's official releases (Windows installer and Linux packages). */
private const val VIBEPOLLO_DOWNLOAD_URL = "https://github.com/Nonary/Vibepollo/releases"

/**
 * The Games tab: the paired PCs and the card to add one, the PCs' games with check boxes to hide several
 * at once (they come back in Hidden games, as games hidden from the Start menu do), how to get PC games
 * when there are none yet, and the PCs' other host apps. Things that load from the network come last,
 * so nothing above them moves.
 */
@Composable
private fun GamesTab(pairedPcs: List<String>, onOpenPcPairing: (() -> Unit)?) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    SettingsGroup(title = if (pairedPcs.isNotEmpty()) stringResource(R.string.rift_settings_group_pcs) else null) {
        pairedPcs.forEach { name ->
            InfoRow(
                title = name,
                value = stringResource(R.string.rift_settings_pc_paired),
                icon = Icons.Rounded.Computer,
            )
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_stream_add_pc),
            subtitle = stringResource(R.string.rift_settings_stream_add_pc_summary),
            icon = Icons.Rounded.Add,
            onClick = { onOpenPcPairing?.invoke() ?: PcStreaming.openAddPc(context) },
        )
    }
    val streamTab = if (pairedPcs.isNotEmpty()) rememberPcStreamingTab() else null
    val hidden by RiftHiddenGames.hidden(context).collectAsStateWithLifecycle()
    val games = streamTab?.games.orEmpty().filter { it.item.appId !in hidden }
    if (games.isNotEmpty()) {
        var picked by remember { mutableStateOf(emptySet<String>()) }
        SettingsGroup(title = stringResource(R.string.rift_stream_group_games)) {
            NoteText(stringResource(R.string.rift_stream_games_note))
            games.forEach { game ->
                val appId = game.item.appId
                CheckRow(
                    title = game.item.name,
                    subtitle = game.pc.name,
                    icon = Icons.Rounded.SportsEsports,
                    checked = appId in picked,
                    onCheckedChange = { picked = if (it) picked + appId else picked - appId },
                )
            }
            val chosen = games.filter { it.item.appId in picked }
            if (chosen.isNotEmpty()) {
                RiftPillButton(
                    text = stringResource(R.string.rift_stream_hide_selected, chosen.size),
                    icon = Icons.Rounded.VisibilityOff,
                    primary = true,
                    onClick = {
                        chosen.forEach { RiftHiddenGames.hide(context, it.item.appId, it.item.name) }
                        picked = emptySet()
                    },
                )
            }
        }
    }
    // No PC games, so the home has no PC streaming tab: how to get them there. With PCs, only once
    // their apps have loaded, so the hint does not flash while they load.
    if (streamTab == null || (streamTab.apps.isNotEmpty() && streamTab.games.isEmpty())) {
        SettingsGroup(title = stringResource(R.string.rift_stream_setup_title)) {
            NoteText(stringResource(R.string.rift_stream_no_games))
            LinkRow(
                title = stringResource(R.string.rift_stream_get_vibepollo),
                subtitle = stringResource(R.string.rift_stream_get_vibepollo_summary),
                icon = Icons.Rounded.Download,
                onClick = { uriHandler.openUri(VIBEPOLLO_DOWNLOAD_URL) },
            )
        }
    }
    // Host apps that are not games (Desktop, Steam, Playnite, Game Pass...): the home tab shows only games.
    val services = streamTab?.services.orEmpty()
    if (services.isNotEmpty()) {
        SettingsGroup(title = stringResource(R.string.rift_stream_group_pc_apps)) {
            services.forEach { service ->
                LinkRow(
                    title = service.app.appName.orEmpty(),
                    subtitle = service.pc.name,
                    icon = Icons.Rounded.Cast,
                    onClick = { streamTab?.launch(context, service) },
                )
            }
        }
    }
}

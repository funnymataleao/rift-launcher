package dev.rift.launcher.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Download
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.R
import app.gamenative.ui.data.DownloadItemStatus
import app.gamenative.ui.model.DownloadsViewModel
import dev.rift.launcher.ui.theme.RiftAccent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.NetworkMonitor
import dev.rift.launcher.settings.RiftPrefs
import dev.rift.launcher.ui.theme.RiftColors
import java.util.Date

/** The one gap between the pictures of the corner group (search, downloads, Wi-Fi, battery, clock). */
internal val StatusGap = 22.dp

/** The status texts (battery, clock): one size and weight. */
private val StatusTextSize = 16.sp

/**
 * The right corner of the home's top bar, like Steam Big Picture: the downloads while a game downloads, the network
 * and the battery when "Show network" / "Show battery" are on in Settings → Appearance, then the clock. Every picture
 * is measured by what it draws, so the gaps between them are all [StatusGap]. The profile lives in the right panel.
 */
@Composable
internal fun StatusIcons(onDownloadsClick: () -> Unit) {
    val context = LocalContext.current
    var showBattery by remember { mutableStateOf(RiftPrefs.of(context).showBattery) }
    var showNetwork by remember { mutableStateOf(RiftPrefs.of(context).showNetwork) }
    // Settings change while the home is composed (it sits under the settings screen), so follow the prefs live.
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences(RiftPrefs.FILE, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            val rift = RiftPrefs.of(context)
            showBattery = rift.showBattery
            showNetwork = rift.showNetwork
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val downloads: DownloadsViewModel = hiltViewModel()
    val downloadsState by downloads.state.collectAsStateWithLifecycle()
    val downloading = downloadsState.downloads.values.any { it.status == DownloadItemStatus.DOWNLOADING }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatusGap),
    ) {
        if (downloading) DownloadsIcon(onDownloadsClick)
        if (showNetwork) NetworkIcon()
        if (showBattery) BatteryStatus()
        Clock()
    }
}

/**
 * Shown while a game downloads: the download arrow gently sinking and rising, A opens the downloads. It takes the
 * room of its picture only; the focus circle is drawn around it, like the search's.
 */
@Composable
private fun DownloadsIcon(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = RiftAccent.current
    val sink by rememberInfiniteTransition(label = "downloads").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "downloadsSink",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .drawBehind { if (focused) drawCircle(accent.brush, radius = 22.dp.toPx()) }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Download,
            contentDescription = stringResource(R.string.rift_downloads_active),
            tint = if (focused) accent.onAccent else RiftColors.OnSurface,
            modifier = Modifier.size(24.dp).graphicsLayer { translationY = sink.dp.toPx() },
        )
    }
}

/**
 * Hours and minutes in the system's 12/24 hour format, never focusable. The minute tick of the system moves it, the
 * time, time zone and 12/24 hour switches redraw it at once.
 */
@Composable
private fun Clock() {
    val context = LocalContext.current
    fun now(): String = DateFormat.getTimeFormat(context).format(Date())
    var time by remember { mutableStateOf(now()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                time = now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        // A tick may have passed between the first read and the registration.
        time = now()
        onDispose { context.unregisterReceiver(receiver) }
    }
    Text(time, color = RiftColors.OnSurface, fontSize = StatusTextSize, maxLines = 1, softWrap = false)
}

@Composable
private fun NetworkIcon() {
    val internet by NetworkMonitor.hasInternet.collectAsStateWithLifecycle()
    val wifi by NetworkMonitor.hasWifiOrEthernet.collectAsStateWithLifecycle()
    val icon = when {
        !internet -> Icons.Rounded.WifiOff
        wifi -> Icons.Rounded.Wifi
        else -> Icons.Rounded.SignalCellularAlt
    }
    Icon(icon, contentDescription = null, tint = RiftColors.OnSurface, modifier = Modifier.size(22.dp))
}

@Composable
private fun BatteryStatus() {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        fun read(intent: Intent?) {
            intent ?: return
            val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            level = if (raw >= 0 && scale > 0) raw * 100 / scale else -1
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = read(intent)
        }
        // The battery broadcast is sticky: registering returns the current state right away.
        read(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { context.unregisterReceiver(receiver) }
    }
    if (level < 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        // The battery picture is narrow inside its square; it takes only its own width, so the gap before it
        // is the group's gap.
        Box(modifier = Modifier.width(10.dp), contentAlignment = Alignment.Center) {
            Icon(
                if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = RiftColors.OnSurface,
                modifier = Modifier.requiredSize(22.dp),
            )
        }
        Text("$level%", color = RiftColors.OnSurface, fontSize = StatusTextSize, maxLines = 1, softWrap = false)
    }
}

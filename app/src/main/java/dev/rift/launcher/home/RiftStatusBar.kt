package dev.rift.launcher.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.NetworkMonitor
import app.gamenative.PrefManager
import app.gamenative.ui.util.SteamIconImage
import app.gamenative.utils.getAvatarURL
import dev.rift.launcher.settings.RiftPrefs
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.ui.theme.RiftColors

/**
 * The right corner of the home's top bar, like Steam Big Picture: the profile avatar, then the battery
 * and the network when "Show battery" / "Show network" are on in Settings → Appearance.
 */
@Composable
internal fun StatusIcons() {
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showNetwork) NetworkIcon()
        if (showBattery) BatteryStatus()
        ProfileIcon()
    }
}

@Composable
private fun ProfileIcon() {
    val avatarHash = PrefManager.steamUserAvatarHash
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .blackGlass(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarHash.isNotEmpty()) {
            SteamIconImage(size = 44.dp, image = { avatarHash.getAvatarURL() })
        } else {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = RiftColors.OnSurface, modifier = Modifier.size(26.dp))
        }
    }
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
    Icon(icon, contentDescription = null, tint = RiftColors.OnSurface, modifier = Modifier.size(28.dp))
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
        Icon(
            if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            contentDescription = null,
            tint = RiftColors.OnSurface,
            modifier = Modifier.size(28.dp),
        )
        Text("$level%", color = RiftColors.OnSurface, fontSize = 17.sp)
    }
}

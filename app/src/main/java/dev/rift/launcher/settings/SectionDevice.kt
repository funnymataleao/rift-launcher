package dev.rift.launcher.settings

import android.content.ClipData
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager
import dev.rift.launcher.assistant.CommunityDeviceMatch
import dev.rift.launcher.assistant.DeviceProfile
import dev.rift.launcher.assistant.DeviceProfiler
import dev.rift.launcher.ui.theme.RiftColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The device profile the AI assistant works from: chip, GPU, Vulkan, memory, screen, the container
 * components ready on the device or downloadable, heat and battery, and the matching device in GameNative's
 * community database. Every row takes focus so a gamepad can scroll the whole page.
 */
@Composable
internal fun DeviceSettings() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var community by remember { mutableStateOf<List<CommunityDeviceMatch>?>(null) }

    LaunchedEffect(reload) {
        val collected = DeviceProfiler.collect(context)
        profile = collected
        community = null
        community = DeviceProfiler.matchCommunityDevices(collected)
    }

    NoteText(stringResource(R.string.rift_device_note))

    val current = profile
    if (current == null) {
        SettingsGroup {
            ProfileRow(stringResource(R.string.rift_device_loading), value = "")
        }
        return
    }

    val unknown = stringResource(R.string.rift_device_unknown)
    fun size(mb: Long) = Formatter.formatShortFileSize(context, mb * 1024L * 1024L)

    SettingsGroup(title = stringResource(R.string.rift_device_group_device)) {
        val hw = current.hardware
        ProfileRow(stringResource(R.string.rift_device_model), "${hw.manufacturer} ${hw.model}".trim())
        ProfileRow(
            stringResource(R.string.rift_device_chip),
            listOfNotNull(hw.socName, hw.socModel.ifBlank { null }).joinToString(" · ").ifBlank { unknown },
        )
        ProfileRow(stringResource(R.string.rift_device_android), stringResource(R.string.rift_device_android_value, hw.androidVersion, hw.sdk))
        ProfileRow(stringResource(R.string.rift_device_build), stringResource(R.string.rift_device_build_value, hw.build, hw.containerVariant))
    }

    SettingsGroup(title = stringResource(R.string.rift_device_group_graphics)) {
        val gpu = current.gpu
        ProfileRow(stringResource(R.string.rift_device_gpu), gpu.renderer.ifBlank { unknown })
        ProfileRow(stringResource(R.string.rift_device_vulkan), gpu.vulkanApi ?: unknown)
        ProfileRow(stringResource(R.string.rift_device_opengl), gpu.glVersion.ifBlank { unknown })
        ProfileRow(stringResource(R.string.rift_device_gpu_class), stringResource(gpuClassLabel(gpu.communityClass)))
    }

    SettingsGroup(title = stringResource(R.string.rift_device_group_memory)) {
        val memory = current.memory
        val storage = current.storage
        val display = current.display
        ProfileRow(stringResource(R.string.rift_device_ram), stringResource(R.string.rift_device_ram_value, size(memory.totalMb), size(memory.availableMb)))
        ProfileRow(stringResource(R.string.rift_device_storage), stringResource(R.string.rift_device_storage_value, size(storage.freeMb), size(storage.totalMb)))
        ProfileRow(
            stringResource(R.string.rift_device_screen),
            stringResource(R.string.rift_device_screen_value, display.widthPx, display.heightPx, display.refreshHz),
        )
        if (display.supportedRefreshHz.size > 1) {
            ProfileRow(
                stringResource(R.string.rift_device_refresh_rates),
                stringResource(R.string.rift_device_hz_list, display.supportedRefreshHz.joinToString(", ")),
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_device_group_components)) {
        val c = current.components
        ComponentRow(stringResource(R.string.rift_device_drivers), c.drivers)
        ComponentRow(stringResource(R.string.rift_device_wine), c.wine)
        ComponentRow(stringResource(R.string.rift_device_dxvk), c.dxvk)
        ComponentRow(stringResource(R.string.rift_device_vkd3d), c.vkd3d)
        ComponentRow(stringResource(R.string.rift_device_box64), c.box64)
        ComponentRow(stringResource(R.string.rift_device_wowbox64), c.wowBox64)
        ComponentRow(stringResource(R.string.rift_device_fexcore), c.fexcore)
    }

    SettingsGroup(title = stringResource(R.string.rift_device_group_now)) {
        val power = current.power
        power.batteryPercent?.let { pct ->
            ProfileRow(
                stringResource(R.string.rift_device_battery),
                if (power.charging == true) {
                    stringResource(R.string.rift_device_battery_charging, pct)
                } else {
                    stringResource(R.string.rift_device_battery_value, pct)
                },
            )
        }
        power.batteryTempC?.let {
            ProfileRow(stringResource(R.string.rift_device_battery_temp), stringResource(R.string.rift_device_temp_value, it))
        }
        power.thermalStatus?.let { status ->
            val label = stringResource(thermalLabel(status))
            ProfileRow(
                stringResource(R.string.rift_device_thermal),
                power.thermalHeadroom?.let { headroom ->
                    val left = ((1f - headroom) * 100).roundToInt().coerceIn(0, 100)
                    stringResource(R.string.rift_device_thermal_headroom, label, left)
                } ?: label,
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_device_group_community)) {
        val matches = community
        when {
            matches == null -> ProfileRow(stringResource(R.string.rift_device_community_searching), value = "")
            matches.isEmpty() -> ProfileRow(stringResource(R.string.rift_device_community_none), value = "")
            else -> matches.take(3).forEach { match ->
                ProfileRow(
                    title = stringResource(R.string.rift_device_community_match),
                    value = match.model,
                    subtitle = listOf(match.gpu, match.soc).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
    }

    SettingsGroup {
        LinkRow(
            title = stringResource(R.string.rift_device_refresh),
            icon = Icons.Rounded.Refresh,
            onClick = {
                profile = null
                reload++
            },
        )
        LinkRow(
            title = stringResource(R.string.rift_device_copy),
            subtitle = stringResource(R.string.rift_device_copy_summary),
            icon = Icons.Rounded.ContentCopy,
            onClick = {
                val copiedText = context.getString(R.string.rift_device_copied)
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Rift device profile", current.toJson())))
                    SnackbarManager.show(copiedText)
                }
            },
        )
    }
}

@Composable
private fun ComponentRow(title: String, versions: DeviceProfile.ComponentVersions) {
    ProfileRow(
        title = title,
        value = stringResource(R.string.rift_device_component_value, versions.ready.size, versions.downloadable.size),
        subtitle = versions.ready.joinToString(", ").ifBlank { stringResource(R.string.rift_device_component_none) },
    )
}

/**
 * A read-only row like [InfoRow] that still takes focus, so the settings page scrolls to it with a gamepad.
 * Long values wrap under the title instead of being cut.
 */
@Composable
private fun ProfileRow(title: String, value: String, subtitle: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .plateFill(focused)
            .focusable(interactionSource = interaction)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = title, color = plateText(focused), fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = plateSubtext(focused), fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                color = plateSubtext(focused),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}

private fun gpuClassLabel(communityClass: String): Int = when (communityClass) {
    "adreno_elite" -> R.string.rift_device_gpu_class_adreno_elite
    "adreno_standard" -> R.string.rift_device_gpu_class_adreno_standard
    "other" -> R.string.rift_device_gpu_class_other
    else -> R.string.rift_device_gpu_class_unknown
}

private fun thermalLabel(status: Int): Int = when (status) {
    0 -> R.string.rift_device_thermal_0
    1 -> R.string.rift_device_thermal_1
    2 -> R.string.rift_device_thermal_2
    3 -> R.string.rift_device_thermal_3
    4 -> R.string.rift_device_thermal_4
    else -> R.string.rift_device_thermal_5
}

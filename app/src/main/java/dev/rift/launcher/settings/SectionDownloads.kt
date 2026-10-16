package dev.rift.launcher.settings

import android.os.Environment
import android.os.storage.StorageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.utils.StorageUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Download network and speed, where games are installed and which Steam server they come from. */
@Composable
internal fun DownloadsSettings() {
    val context = LocalContext.current

    var wifiOnly by rememberSaveable { mutableStateOf(PrefManager.downloadOnWifiOnly) }
    val speedValues = remember { listOf(8, 16, 24, 32) }
    val speedLabels = listOf(
        stringResource(R.string.settings_download_slow),
        stringResource(R.string.settings_download_medium),
        stringResource(R.string.settings_download_fast),
        stringResource(R.string.settings_download_blazing),
    )
    var speedIndex by rememberSaveable {
        mutableStateOf(speedValues.indexOf(PrefManager.downloadSpeed).takeIf { it >= 0 } ?: 2)
    }

    // Install targets: SD cards, USB drives and adopted storage. Enumerating them can touch the disk
    // (a USB drive's app folder may need creating), so it stays off the composition thread.
    val storageManager = remember { context.getSystemService(StorageManager::class.java) }
    val externalFallback = stringResource(R.string.storage_external)
    val volumes by produceState(initialValue = emptyList<File>(), context) {
        value = withContext(Dispatchers.IO) {
            StorageUtils.getAllExternalFilesDirs(context)
                .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
                .filter { StorageUtils.isExternalInstallTarget(storageManager, it) }
        }
    }
    val volumeLabels = remember(volumes) {
        volumes.map { dir -> storageManager?.getStorageVolume(dir)?.getDescription(context) ?: externalFallback }
    }
    var useExternal by rememberSaveable { mutableStateOf(PrefManager.useExternalStorage) }
    var volumeDialog by rememberSaveable { mutableStateOf(false) }
    var volumeIndex by rememberSaveable(volumes) {
        mutableStateOf(
            volumes.indexOfFirst { dir ->
                dir.absolutePath == PrefManager.externalStoragePath ||
                    StorageUtils.publicInstallRoot(dir)?.absolutePath == PrefManager.externalStoragePath
            }.takeIf { it >= 0 } ?: 0,
        )
    }

    // Steam download servers: "Automatic" (id 0) first, the rest alphabetically.
    val regions = remember {
        val json = context.assets.open("steam_regions.json").bufferedReader().use { it.readText() }
        val all = Json.decodeFromString<Map<String, String>>(json).mapKeys { it.key.toInt() }.toList()
        val (auto, others) = all.partition { it.first == 0 }
        auto + others.sortedBy { it.second }
    }
    var regionDialog by rememberSaveable { mutableStateOf(false) }
    var regionIndex by rememberSaveable {
        mutableStateOf(regions.indexOfFirst { it.first == PrefManager.cellId }.takeIf { it >= 0 } ?: 0)
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_network)) {
        SwitchRow(
            title = stringResource(R.string.settings_interface_wifi_only_title),
            subtitle = stringResource(R.string.settings_interface_wifi_only_subtitle),
            icon = Icons.Rounded.Wifi,
            checked = wifiOnly,
            onCheckedChange = {
                wifiOnly = it
                PrefManager.downloadOnWifiOnly = it
            },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.settings_download_speed),
            subtitle = stringResource(R.string.settings_download_heat_warning),
            icon = Icons.Rounded.Speed,
            options = speedLabels,
            selectedIndex = speedIndex,
            onSelect = {
                speedIndex = it
                PrefManager.downloadSpeed = speedValues[it]
            },
        )
        LinkRow(
            title = stringResource(R.string.settings_interface_download_server_title),
            icon = Icons.Rounded.Dns,
            value = regions.getOrNull(regionIndex)?.second ?: stringResource(R.string.settings_region_default),
            onClick = { regionDialog = true },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_storage)) {
        SwitchRow(
            title = stringResource(R.string.settings_interface_external_storage_title),
            subtitle = stringResource(
                if (volumes.isEmpty()) R.string.settings_interface_no_external_storage
                else R.string.settings_interface_external_storage_subtitle,
            ),
            icon = Icons.Rounded.SdStorage,
            enabled = volumes.isNotEmpty(),
            checked = useExternal,
            onCheckedChange = {
                useExternal = it
                PrefManager.useExternalStorage = it
                if (it && volumes.isNotEmpty()) {
                    PrefManager.externalStoragePath = StorageUtils.preferredInstallRoot(volumes[0])
                }
            },
        )
        if (useExternal && volumes.isNotEmpty()) {
            LinkRow(
                title = stringResource(R.string.settings_interface_storage_volume_title),
                icon = Icons.Rounded.Storage,
                value = volumeLabels.getOrNull(volumeIndex),
                onClick = { volumeDialog = true },
            )
        }
    }

    if (volumeDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.settings_interface_storage_volume_title),
            options = volumeLabels,
            selectedIndex = volumeIndex,
            onSelect = {
                volumeIndex = it
                PrefManager.externalStoragePath = StorageUtils.preferredInstallRoot(volumes[it])
            },
            onDismiss = { volumeDialog = false },
        )
    }
    if (regionDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.settings_interface_download_server_title),
            options = regions.map { it.second },
            selectedIndex = regionIndex,
            onSelect = { index ->
                regionIndex = index
                val id = regions[index].first
                PrefManager.cellId = id
                PrefManager.cellIdManuallySet = id != 0
            },
            onDismiss = { regionDialog = false },
        )
    }
}

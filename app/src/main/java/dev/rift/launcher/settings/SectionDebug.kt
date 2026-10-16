package dev.rift.launcher.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.CrashHandler
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.service.SteamService
import app.gamenative.ui.component.dialog.CrashLogDialog
import app.gamenative.ui.component.dialog.WineDebugChannelsDialog
import app.gamenative.ui.util.SnackbarManager
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.winlator.PrefManager as WinlatorPrefManager
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Logs, Wine debugging and the reset buttons. The destructive ones need a long press. */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun DebugSettings() {
    val context = LocalContext.current
    val longPressHint = stringResource(R.string.rift_settings_long_press)

    // Wine debug channels
    var allChannels by remember { mutableStateOf<List<String>>(emptyList()) }
    var channelsDialog by remember { mutableStateOf(false) }
    var selectedChannels by remember { mutableStateOf(PrefManager.wineDebugChannels.split(",")) }
    LaunchedEffect(Unit) {
        val json = context.assets.open("wine_debug_channels.json").bufferedReader().use { it.readText() }
        allChannels = Json.decodeFromString(json)
    }
    WineDebugChannelsDialog(
        openDialog = channelsDialog,
        allChannels = allChannels,
        currentSelection = selectedChannels,
        onSave = { selection ->
            selectedChannels = selection
            PrefManager.wineDebugChannels = selection.joinToString(",")
            channelsDialog = false
        },
        onDismiss = { channelsDialog = false },
    )

    var wineDebug by rememberSaveable { mutableStateOf(PrefManager.enableWineDebug) }
    var boxLogs by rememberSaveable { mutableStateOf(WinlatorPrefManager.getBoolean("enable_box86_64_logs", false)) }

    // Latest crash log and Wine log
    var crashFile by remember { mutableStateOf<File?>(null) }
    var wineLogFile by remember { mutableStateOf<File?>(null) }
    var crashDialog by rememberSaveable { mutableStateOf(false) }
    var wineLogDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        crashFile = File(context.getExternalFilesDir(null), "crash_logs").listFiles()
            ?.filter { it.name.startsWith("pluvia_crash_") }
            ?.maxByOrNull { it.lastModified() }
        val wineDir = File(context.getExternalFilesDir(null), "wine_logs").also { it.mkdirs() }
        wineLogFile = File(wineDir, "wine_debug.log").takeIf { it.exists() }
    }

    val saveCrash = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        copyTo(context, uri, crashFile, "Failed to save logcat to destination")
    }
    val saveWineLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        copyTo(context, uri, wineLogFile, "Failed to save Wine log to destination")
    }
    val saveLogcat = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        try {
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(CrashHandler.getAppLogs(1000).toByteArray())
                }
            }
        } catch (e: Exception) {
            SnackbarManager.show(context.getString(R.string.toast_failed_log_save))
        }
    }

    val crash = crashFile
    if (crashDialog && crash != null) {
        val text by produceState("Loading...", crash) { value = withContext(Dispatchers.IO) { readTail(crash) } }
        CrashLogDialog(
            visible = true,
            fileName = crash.name,
            fileText = text,
            onSave = { saveCrash.launch(crash.name) },
            onDismissRequest = { crashDialog = false },
        )
    }
    val wineLog = wineLogFile
    if (wineLogDialog && wineLog != null) {
        val text by produceState("Loading...", wineLog) { value = withContext(Dispatchers.IO) { readTail(wineLog) } }
        CrashLogDialog(
            visible = true,
            fileName = wineLog.name,
            fileText = text,
            onSave = { saveWineLog.launch(wineLog.name) },
            onDismissRequest = { wineLogDialog = false },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_logs)) {
        LinkRow(
            title = stringResource(R.string.settings_save_logcat_title),
            subtitle = stringResource(R.string.settings_save_logcat_subtitle),
            icon = Icons.Rounded.Save,
            onClick = { saveLogcat.launch("app_logs_${CrashHandler.timestamp}.txt") },
        )
        LinkRow(
            title = stringResource(R.string.settings_debug_view_crash_title),
            subtitle = stringResource(
                if (crash != null) R.string.settings_debug_view_crash_subtitle else R.string.settings_debug_no_crash_logs,
            ),
            icon = Icons.Rounded.Error,
            enabled = crash != null,
            onClick = { crashDialog = true },
        )
        LinkRow(
            title = stringResource(R.string.settings_debug_view_log_title),
            subtitle = stringResource(
                if (wineLog != null) R.string.settings_debug_view_log_subtitle else R.string.settings_debug_no_wine_logs,
            ),
            icon = Icons.Rounded.Description,
            enabled = wineLog != null,
            onClick = { wineLogDialog = true },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_wine_debug)) {
        SwitchRow(
            title = stringResource(R.string.settings_debug_wine_logs_title),
            subtitle = stringResource(R.string.settings_debug_wine_logs_subtitle),
            icon = Icons.Rounded.BugReport,
            checked = wineDebug,
            onCheckedChange = {
                wineDebug = it
                PrefManager.enableWineDebug = it
            },
        )
        LinkRow(
            title = stringResource(R.string.settings_debug_wine_channels_title),
            subtitle = selectedChannels.filter { it.isNotBlank() }.joinToString(",")
                .ifEmpty { stringResource(R.string.settings_debug_no_channels_selected) },
            icon = Icons.Rounded.FilterList,
            onClick = { channelsDialog = true },
        )
        SwitchRow(
            title = stringResource(R.string.settings_debug_box_logs_title),
            subtitle = stringResource(R.string.settings_debug_box_logs_subtitle),
            icon = Icons.Rounded.Terminal,
            checked = boxLogs,
            onCheckedChange = {
                boxLogs = it
                WinlatorPrefManager.putBoolean("enable_box86_64_logs", it)
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_reset)) {
        LinkRow(
            title = stringResource(R.string.settings_debug_clear_cache_title),
            subtitle = stringResource(R.string.settings_debug_clear_cache_subtitle),
            icon = Icons.Rounded.CleaningServices,
            danger = true,
            onClick = { SnackbarManager.show(longPressHint) },
            onLongClick = {
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
            },
        )
        LinkRow(
            title = stringResource(R.string.settings_debug_clear_db_title),
            subtitle = stringResource(R.string.settings_debug_clear_db_subtitle),
            icon = Icons.Rounded.Storage,
            danger = true,
            onClick = { SnackbarManager.show(longPressHint) },
            onLongClick = {
                SteamService.stop()
                SteamService.clearDatabase()
                (context as ComponentActivity).finishAffinity()
            },
        )
        LinkRow(
            title = stringResource(R.string.settings_debug_clear_prefs_title),
            subtitle = stringResource(R.string.settings_debug_clear_prefs_subtitle),
            icon = Icons.Rounded.DeleteForever,
            danger = true,
            onClick = { SnackbarManager.show(longPressHint) },
            onLongClick = {
                SteamService.logOut()
                (context as ComponentActivity).finishAffinity()
            },
        )
    }
}

private fun copyTo(context: android.content.Context, uri: android.net.Uri?, file: File?, failure: String) {
    try {
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                file?.inputStream()?.use { input -> input.copyTo(out) }
            }
        }
    } catch (e: Exception) {
        SnackbarManager.show(failure)
    }
}

private const val MAX_LOG_DISPLAY_BYTES = 256 * 1024L

/** The last 256 KB of a log, from a whole line. */
private fun readTail(file: File?): String {
    if (file == null || !file.exists()) return "File not found: ${file?.name ?: "null"}"
    return try {
        val length = file.length()
        if (length <= MAX_LOG_DISPLAY_BYTES) {
            file.readText(Charsets.UTF_8)
        } else {
            val start = maxOf(0L, length - MAX_LOG_DISPLAY_BYTES)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val bytes = ByteArray((length - start).toInt())
                raf.readFully(bytes)
                val text = bytes.toString(Charsets.UTF_8)
                val newline = text.indexOf('\n')
                val trimmed = if (newline >= 0) text.substring(newline + 1) else text
                "... (${(length + 1023) / 1024}KB, showing last ${MAX_LOG_DISPLAY_BYTES / 1024}KB) ...\n$trimmed"
            }
        }
    } catch (e: Exception) {
        "Failed to read file: ${e.message ?: e.javaClass.simpleName}"
    }
}

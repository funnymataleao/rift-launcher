package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.gamenative.R
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.utils.CustomGameScanner

/**
 * The folders Windows games are read from. The person adds a folder that holds game folders; Rift lists
 * every subfolder with an .exe in it on the PC tab, runs them in GameNative's container like its own
 * "custom games", and looks at the folders again each time it starts and from here.
 */
@Composable
internal fun PcGamesSettings() {
    val context = LocalContext.current
    var folders by remember { mutableStateOf(PcGameFolders.all()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Counting walks the folders, so it runs off the main thread, and again when coming back from the
    // system's all-files screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    androidx.compose.runtime.LaunchedEffect(folders, refresh) {
        counts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            folders.associateWith { PcGameFolders.gameCount(it) }
        }
    }

    val picker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            PcGameFolders.add(path)
            folders = PcGameFolders.all()
            if (!PcGameFolders.canRead(context, path)) CustomGameScanner.requestManageExternalStoragePermission(context)
        },
    )

    val missingAccess = folders.any { !PcGameFolders.canRead(context, it) }

    NoteText(
        if (folders.isEmpty()) {
            stringResource(R.string.rift_settings_pc_none)
        } else {
            stringResource(R.string.rift_settings_pc_note)
        },
    )

    if (missingAccess) {
        SettingsGroup {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_access),
                subtitle = stringResource(R.string.rift_settings_pc_access_summary),
                icon = Icons.Rounded.Security,
                onClick = { CustomGameScanner.requestManageExternalStoragePermission(context) },
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_pc_folders)) {
        folders.forEach { path ->
            val count = counts[path]
            LinkRow(
                title = path.substringAfterLast('/').ifEmpty { path },
                subtitle = path,
                icon = Icons.Rounded.Folder,
                value = count?.let { pluralStringResource(R.plurals.rift_settings_pc_games_count, it, it) },
                onClick = { confirmRemove = if (confirmRemove == path) null else path },
            )
            if (confirmRemove == path) {
                LinkRow(
                    title = stringResource(R.string.rift_settings_pc_remove),
                    subtitle = stringResource(R.string.rift_settings_pc_remove_summary),
                    icon = Icons.Rounded.FolderOff,
                    danger = true,
                    onClick = {
                        PcGameFolders.remove(path)
                        folders = PcGameFolders.all()
                        confirmRemove = null
                    },
                )
            }
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_pc_add),
            subtitle = stringResource(R.string.rift_settings_pc_add_summary),
            icon = Icons.Rounded.CreateNewFolder,
            onClick = { picker.launchPicker() },
        )
        if (folders.isNotEmpty()) {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_rescan),
                subtitle = stringResource(R.string.rift_settings_pc_rescan_summary),
                icon = Icons.Rounded.Refresh,
                onClick = {
                    PcGameFolders.rescan()
                    refresh++
                },
            )
        }
    }
}

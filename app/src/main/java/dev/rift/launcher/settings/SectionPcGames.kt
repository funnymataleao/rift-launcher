package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.gamenative.R
import app.gamenative.ui.components.rememberCustomGameFolderPicker
import app.gamenative.utils.CustomGameScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The folders Windows games are read from. Each folder is a plate with its game count and the delete button
 * inside it (the files are never touched); A on a folder looks at it again. Below them "Add games folder" and
 * "Scan again", which says on its own plate how many games were found and where they are. The cursor stays on
 * what was pressed. Rift looks at the folders again each time it starts.
 */
@Composable
internal fun PcGamesSettings() {
    val context = LocalContext.current
    var folders by remember { mutableStateOf(PcGameFolders.all()) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var refresh by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var lastFound by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val addFocus = remember { FocusRequester() }
    var removed by remember { mutableIntStateOf(0) }

    // Counting walks the folders, so it runs off the main thread, and again when coming back from the
    // system's all-files screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(folders, refresh) {
        counts = withContext(Dispatchers.IO) { folders.associateWith { PcGameFolders.gameCount(it) } }
    }
    // The plate that had the cursor is gone after a removal: keep the cursor in this list, not in the rail.
    LaunchedEffect(removed) {
        if (removed > 0) runCatching { addFocus.requestFocus() }
    }

    // Scans every folder again and tells the total on the "Scan again" plate. The plates stay enabled while it
    // runs (a disabled plate drops the cursor into the rail); a press during a scan does nothing.
    fun scan() {
        if (scanning) return
        scanning = true
        scope.launch {
            val now = withContext(Dispatchers.IO) {
                PcGameFolders.rescan()
                PcGameFolders.all().associateWith { PcGameFolders.gameCount(it) }
            }
            counts = now
            lastFound = now.values.sum()
            scanning = false
        }
    }

    val picker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            PcGameFolders.add(path)
            folders = PcGameFolders.all()
            if (!PcGameFolders.canRead(context, path)) CustomGameScanner.requestManageExternalStoragePermission(context)
            scan()
            runCatching { addFocus.requestFocus() }
        },
    )
    val missingAccess = folders.any { !PcGameFolders.canRead(context, it) }

    SettingsGroup(title = stringResource(R.string.rift_settings_pc_games)) {
        if (missingAccess) {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_access),
                subtitle = stringResource(R.string.rift_settings_pc_access_summary),
                icon = Icons.Rounded.Security,
                onClick = { CustomGameScanner.requestManageExternalStoragePermission(context) },
            )
        }
        folders.forEach { path ->
            LinkRow(
                title = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
                subtitle = path,
                value = counts[path]?.let { pluralStringResource(R.plurals.rift_settings_pc_games_count, it, it) }
                    ?: stringResource(R.string.rift_settings_pc_scanning),
                icon = Icons.Rounded.Folder,
                chevron = false,
                onClick = { scan() },
                actions = listOf(
                    RowAction(
                        icon = Icons.Rounded.Delete,
                        description = stringResource(R.string.rift_settings_pc_remove),
                        onClick = {
                            PcGameFolders.remove(path)
                            folders = PcGameFolders.all()
                            lastFound = null
                            removed++
                        },
                    ),
                ),
            )
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_pc_add),
            subtitle = if (folders.isEmpty()) stringResource(R.string.rift_settings_pc_add_summary) else null,
            icon = Icons.Rounded.CreateNewFolder,
            chevron = false,
            onClick = { picker.launchPicker() },
            modifier = Modifier.focusRequester(addFocus),
        )
        if (folders.isNotEmpty()) {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_rescan),
                subtitle = when (val found = lastFound) {
                    null -> stringResource(R.string.rift_settings_pc_rescan_summary)
                    0 -> stringResource(R.string.rift_settings_pc_found_none)
                    else -> pluralStringResource(R.plurals.rift_settings_pc_found_where, found, found)
                },
                icon = Icons.Rounded.Refresh,
                value = if (scanning) stringResource(R.string.rift_settings_pc_scanning) else null,
                chevron = false,
                onClick = { scan() },
            )
        }
    }
}

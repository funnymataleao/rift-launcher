package dev.rift.launcher.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
 * The folders Windows games are read from. Each folder is a plate with its game count and a round delete
 * button beside it (the files are never touched); below them "Add folder" and "Scan again". Rift looks at
 * the folders again each time it starts.
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

    val picker = rememberCustomGameFolderPicker(
        onPathSelected = { path ->
            PcGameFolders.add(path)
            folders = PcGameFolders.all()
            if (!PcGameFolders.canRead(context, path)) CustomGameScanner.requestManageExternalStoragePermission(context)
        },
    )
    val missingAccess = folders.any { !PcGameFolders.canRead(context, it) }

    SettingsGroup {
        if (missingAccess) {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_access),
                subtitle = stringResource(R.string.rift_settings_pc_access_summary),
                icon = Icons.Rounded.Security,
                onClick = { CustomGameScanner.requestManageExternalStoragePermission(context) },
            )
        }
        folders.forEach { path ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InfoRow(
                    title = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
                    value = counts[path]?.let { pluralStringResource(R.plurals.rift_settings_pc_games_count, it, it) }
                        ?: stringResource(R.string.rift_settings_pc_scanning),
                    icon = Icons.Rounded.Folder,
                    modifier = Modifier.weight(1f),
                )
                RiftRoundButton(
                    icon = Icons.Rounded.Delete,
                    description = stringResource(R.string.rift_settings_pc_remove),
                    size = RowMinHeight,
                    onClick = {
                        PcGameFolders.remove(path)
                        folders = PcGameFolders.all()
                        removed++
                    },
                )
            }
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_pc_add),
            subtitle = if (folders.isEmpty()) stringResource(R.string.rift_settings_pc_add_summary) else null,
            icon = Icons.Rounded.CreateNewFolder,
            onClick = { picker.launchPicker() },
            modifier = Modifier.focusRequester(addFocus),
        )
        if (folders.isNotEmpty()) {
            LinkRow(
                title = stringResource(R.string.rift_settings_pc_rescan),
                icon = Icons.Rounded.Refresh,
                value = when {
                    scanning -> stringResource(R.string.rift_settings_pc_scanning)
                    else -> lastFound?.let { pluralStringResource(R.plurals.rift_settings_pc_found, it, it) }
                },
                enabled = !scanning,
                onClick = {
                    scanning = true
                    scope.launch {
                        val found = withContext(Dispatchers.IO) {
                            PcGameFolders.rescan()
                            val now = PcGameFolders.all().associateWith { PcGameFolders.gameCount(it) }
                            now to now.values.sum()
                        }
                        counts = found.first
                        lastFound = found.second
                        scanning = false
                    }
                },
            )
        }
    }
}

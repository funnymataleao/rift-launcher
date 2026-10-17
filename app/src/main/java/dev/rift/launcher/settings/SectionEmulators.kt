package dev.rift.launcher.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager

private sealed interface EmulatorDialog {
    data object PickPlatform : EmulatorDialog
    data class PickEmulator(val platformId: String) : EmulatorDialog
    data class PickCore(val platformId: String, val emulatorPackage: String) : EmulatorDialog
    data class Manage(val platformId: String) : EmulatorDialog
}

/**
 * Platforms, the way Beacon does it: pick a system, pick the emulator app that plays it, pick the folder
 * with its games. Each added platform gets its own tab on the home screen; with none added there is none.
 */
@Composable
internal fun EmulatorsSettings() {
    val context = LocalContext.current
    val prefs = rememberRiftPrefs()
    var platforms by remember { mutableStateOf(prefs.emulatorPlatforms) }
    var biosUri by remember { mutableStateOf(prefs.biosFolderUri) }
    var esDeUri by remember { mutableStateOf(prefs.esDeFolderUri) }
    var dialog by remember { mutableStateOf<EmulatorDialog?>(null) }
    var onFolderPicked by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun save(list: List<EmulatorPlatform>) {
        platforms = list
        prefs.emulatorPlatforms = list
    }

    val picker = rememberFolderPicker { uri -> onFolderPicked?.invoke(uri) }

    /** The emulator (and core) for a system is chosen: save it, asking for the games folder first when the system is new. */
    fun chooseEmulator(platformId: String, emulatorPackage: String, core: String) {
        val existing = platforms.firstOrNull { it.platformId == platformId }
        if (existing != null) {
            save(platforms.map { if (it.platformId == platformId) it.copy(emulatorPackage = emulatorPackage, core = core) else it })
        } else {
            onFolderPicked = { uri ->
                save(platforms + EmulatorPlatform(platformId, emulatorPackage, uri, core))
                onFolderPicked = null
            }
            picker()
        }
    }

    /** After an app is picked: RetroArch also needs the core that runs the system, everything else is done. */
    fun emulatorPicked(platformId: String, emulatorPackage: String) {
        val def = EmulatorCatalog.platform(platformId)
        if (EmulatorCatalog.launchKind(def, emulatorPackage) == LaunchKind.RETROARCH && def != null && def.cores.size > 1) {
            dialog = EmulatorDialog.PickCore(platformId, emulatorPackage)
        } else {
            chooseEmulator(platformId, emulatorPackage, "")
        }
    }

    NoteText(stringResource(R.string.rift_settings_platforms_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_group_platforms)) {
        platforms.forEach { entry ->
            val def = EmulatorCatalog.platform(entry.platformId) ?: return@forEach
            LinkRow(
                title = stringResource(def.nameRes),
                subtitle = listOfNotNull(
                    EmulatorCatalog.labelFor(context, entry.emulatorPackage, def).takeIf { entry.emulatorPackage.isNotBlank() },
                    folderLabel(entry.folderUri),
                ).joinToString(" · "),
                icon = Icons.Rounded.SportsEsports,
                onClick = { dialog = EmulatorDialog.Manage(entry.platformId) },
            )
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_add_platform),
            subtitle = stringResource(R.string.rift_settings_add_platform_summary),
            icon = Icons.Rounded.Add,
            onClick = {
                if (EmulatorCatalog.platforms.none { p -> platforms.none { it.platformId == p.id } }) {
                    SnackbarManager.show(context.getString(R.string.rift_settings_all_platforms_added))
                } else {
                    dialog = EmulatorDialog.PickPlatform
                }
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_folders)) {
        FolderRow(
            title = stringResource(R.string.rift_settings_bios_folder),
            subtitle = stringResource(R.string.rift_settings_bios_folder_summary),
            icon = Icons.Rounded.Memory,
            uri = biosUri,
            onPicked = {
                biosUri = it
                prefs.biosFolderUri = it
            },
        )
        FolderRow(
            title = stringResource(R.string.rift_settings_esde_folder),
            subtitle = stringResource(R.string.rift_settings_esde_folder_summary),
            icon = Icons.Rounded.Folder,
            uri = esDeUri,
            onPicked = {
                esDeUri = it
                prefs.esDeFolderUri = it
            },
        )
        NoteText(stringResource(R.string.rift_settings_bios_note))
    }

    when (val current = dialog) {
        null -> Unit
        EmulatorDialog.PickPlatform -> {
            val free = EmulatorCatalog.platforms.filter { p -> platforms.none { it.platformId == p.id } }
            RiftChoiceDialog(
                title = stringResource(R.string.rift_settings_pick_platform),
                options = free.map { stringResource(it.nameRes) },
                selectedIndex = -1,
                onSelect = { index -> dialog = EmulatorDialog.PickEmulator(free[index].id) },
                onDismiss = { if (dialog == current) dialog = null },
            )
        }
        is EmulatorDialog.PickEmulator -> {
            val def = EmulatorCatalog.platform(current.platformId)
            if (def != null) {
                EmulatorPickerDialog(
                    platform = def,
                    onPick = { emulatorPicked(current.platformId, it) },
                    onDismiss = { if (dialog == current) dialog = null },
                )
            }
        }
        is EmulatorDialog.PickCore -> {
            val def = EmulatorCatalog.platform(current.platformId)
            val cores = def?.cores.orEmpty()
            RiftChoiceDialog(
                title = stringResource(R.string.rift_settings_pick_core),
                options = cores.map { it.label },
                selectedIndex = cores.indexOfFirst { it.file == platforms.firstOrNull { p -> p.platformId == current.platformId }?.core }.coerceAtLeast(0),
                onSelect = { chooseEmulator(current.platformId, current.emulatorPackage, cores[it].file) },
                onDismiss = { if (dialog == current) dialog = null },
            )
        }
        is EmulatorDialog.Manage -> {
            val def = EmulatorCatalog.platform(current.platformId)
            val entry = platforms.firstOrNull { it.platformId == current.platformId }
            val hasCore = entry != null && def != null && def.cores.size > 1 &&
                EmulatorCatalog.launchKind(def, entry.emulatorPackage) == LaunchKind.RETROARCH
            val options = buildList {
                add(stringResource(R.string.rift_settings_change_emulator))
                if (hasCore) add(stringResource(R.string.rift_settings_change_core))
                add(stringResource(R.string.rift_settings_change_folder))
                add(stringResource(R.string.rift_settings_remove_platform))
            }
            RiftChoiceDialog(
                title = def?.let { stringResource(it.nameRes) }.orEmpty(),
                options = options,
                selectedIndex = -1,
                onSelect = { index ->
                    val action = index - (if (hasCore && index > 0) 1 else 0)
                    when {
                        index == 0 -> dialog = EmulatorDialog.PickEmulator(current.platformId)
                        hasCore && index == 1 -> entry?.let { dialog = EmulatorDialog.PickCore(current.platformId, it.emulatorPackage) }
                        action == 1 -> {
                            onFolderPicked = { uri ->
                                save(platforms.map { if (it.platformId == current.platformId) it.copy(folderUri = uri) else it })
                                onFolderPicked = null
                            }
                            picker()
                        }
                        else -> save(platforms.filterNot { it.platformId == current.platformId })
                    }
                },
                onDismiss = { if (dialog == current) dialog = null },
            )
        }
    }
}

/** Opens the system folder picker and keeps access to the chosen folder. Returns the function that opens it. */
@Composable
internal fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { picked ->
        if (picked != null) {
            keepAccess(context, picked)
            onPicked(picked.toString())
        }
    }
    return { launcher.launch(null) }
}

private fun keepAccess(context: Context, uri: android.net.Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

/** A row that opens the system folder picker and stores the chosen folder. */
@Composable
private fun FolderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    uri: String,
    onPicked: (String) -> Unit,
) {
    val open = rememberFolderPicker(onPicked)
    LinkRow(
        title = title,
        subtitle = folderLabel(uri) ?: subtitle,
        icon = icon,
        value = if (uri.isBlank()) stringResource(R.string.rift_settings_choose) else stringResource(R.string.rift_settings_change),
        onClick = open,
    )
}

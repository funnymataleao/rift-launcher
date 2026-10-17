package dev.rift.launcher.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager

/**
 * Platforms, the way Beacon does it: pick a system, pick the emulator app that plays it, pick the folder
 * with its games. Each added platform gets its own tab on the home screen; with none added there is none.
 * Choosing a system, an emulator or a core opens a page of this section, not a pop-up; Back goes one page up.
 *
 * The page is a string: null is the list, "add" the systems still free, "emu:<system>" the emulator choice,
 * "core:<system>:<package>" the RetroArch core choice and "manage:<system>" a system that is already added.
 */
@Composable
internal fun EmulatorsSettings() {
    val context = LocalContext.current
    val prefs = rememberRiftPrefs()
    var platforms by remember { mutableStateOf(prefs.emulatorPlatforms) }
    var biosUri by remember { mutableStateOf(prefs.biosFolderUri) }
    var esDeUri by remember { mutableStateOf(prefs.esDeFolderUri) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var focusKey by rememberSaveable { mutableStateOf(KEY_ADD) }
    var onFolderPicked by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }

    fun save(list: List<EmulatorPlatform>) {
        platforms = list
        prefs.emulatorPlatforms = list
    }

    /** One page up: from a choice back to where it was opened from. */
    fun back() {
        val current = page ?: return
        val parts = current.split(':')
        page = when (parts[0]) {
            "core" -> "emu:${parts[1]}"
            "emu" -> if (platforms.any { it.platformId == parts[1] }) "manage:${parts[1]}" else "add"
            else -> null
        }
    }
    BackHandler(enabled = page != null) { back() }
    // Back from a page returns focus to the row that opened it. Not when the section has just been opened:
    // then the person is still moving through the section list, and focus must stay there.
    var wasOnPage by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        if (page != null) {
            wasOnPage = true
        } else if (wasOnPage) {
            wasOnPage = false
            runCatching { requester(focusKey).requestFocus() }
        }
    }

    val picker = rememberFolderPicker { uri -> onFolderPicked?.invoke(uri) }

    /** The emulator (and core) for a system is chosen: save it, asking for the games folder first when the system is new. */
    fun chooseEmulator(platformId: String, emulatorPackage: String, core: String) {
        val existing = platforms.firstOrNull { it.platformId == platformId }
        if (existing != null) {
            save(platforms.map { if (it.platformId == platformId) it.copy(emulatorPackage = emulatorPackage, core = core) else it })
            page = "manage:$platformId"
        } else {
            onFolderPicked = { uri ->
                save(platforms + EmulatorPlatform(platformId, emulatorPackage, uri, core))
                onFolderPicked = null
                focusKey = platformId
                page = null
            }
            picker()
        }
    }

    /** After an app is picked: RetroArch also needs the core that runs the system, everything else is done. */
    fun emulatorPicked(platformId: String, emulatorPackage: String) {
        val def = EmulatorCatalog.platform(platformId)
        if (EmulatorCatalog.launchKind(def, emulatorPackage) == LaunchKind.RETROARCH && def != null && def.cores.size > 1) {
            page = "core:$platformId:$emulatorPackage"
        } else {
            chooseEmulator(platformId, emulatorPackage, "")
        }
    }

    page?.let { current ->
        val parts = current.split(':')
        val def = EmulatorCatalog.platform(parts.getOrNull(1).orEmpty())
        when {
            current == "add" -> {
                val free = EmulatorCatalog.platforms.filter { p -> platforms.none { it.platformId == p.id } }
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                SettingsGroup(title = stringResource(R.string.rift_settings_pick_platform)) {
                    free.forEachIndexed { index, platform ->
                        LinkRow(
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            title = stringResource(platform.nameRes),
                            subtitle = platform.emulators.joinToString(", ") { it.label },
                            icon = Icons.Rounded.SportsEsports,
                            onClick = { page = "emu:${platform.id}" },
                        )
                    }
                }
            }
            parts[0] == "emu" && def != null -> {
                SettingsGroup(title = stringResource(R.string.rift_settings_pick_emulator) + " · " + stringResource(def.nameRes)) {}
                EmulatorPickerPage(platform = def, onPick = { emulatorPicked(def.id, it) })
            }
            parts[0] == "core" && def != null -> {
                val first = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                val chosen = platforms.firstOrNull { it.platformId == def.id }?.core
                SettingsGroup(title = stringResource(R.string.rift_settings_pick_core)) {
                    def.cores.forEachIndexed { index, core ->
                        LinkRow(
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            title = core.label,
                            value = if (core.file == chosen) stringResource(R.string.rift_settings_packs_selected) else null,
                            onClick = { chooseEmulator(def.id, parts[2], core.file) },
                        )
                    }
                }
            }
            parts[0] == "manage" && def != null -> {
                val entry = platforms.firstOrNull { it.platformId == def.id }
                if (entry == null) {
                    page = null
                } else {
                    val hasCore = def.cores.size > 1 && EmulatorCatalog.launchKind(def, entry.emulatorPackage) == LaunchKind.RETROARCH
                    val first = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                    SettingsGroup(title = stringResource(def.nameRes)) {
                        LinkRow(
                            modifier = Modifier.focusRequester(first),
                            title = stringResource(R.string.rift_settings_change_emulator),
                            icon = Icons.Rounded.SportsEsports,
                            value = EmulatorCatalog.labelFor(context, entry.emulatorPackage, def).takeIf { entry.emulatorPackage.isNotBlank() },
                            onClick = { page = "emu:${def.id}" },
                        )
                        if (hasCore) {
                            LinkRow(
                                title = stringResource(R.string.rift_settings_change_core),
                                icon = Icons.Rounded.Memory,
                                value = def.cores.firstOrNull { it.file == entry.core }?.label ?: def.cores.first().label,
                                onClick = { page = "core:${def.id}:${entry.emulatorPackage}" },
                            )
                        }
                        LinkRow(
                            title = stringResource(R.string.rift_settings_change_folder),
                            icon = Icons.Rounded.Folder,
                            value = folderLabel(entry.folderUri),
                            onClick = {
                                onFolderPicked = { uri ->
                                    save(platforms.map { if (it.platformId == def.id) it.copy(folderUri = uri) else it })
                                    onFolderPicked = null
                                }
                                picker()
                            },
                        )
                        LinkRow(
                            title = stringResource(R.string.rift_settings_remove_platform),
                            icon = Icons.Rounded.Delete,
                            danger = true,
                            onClick = {
                                save(platforms.filterNot { it.platformId == def.id })
                                focusKey = KEY_ADD
                                page = null
                            },
                        )
                    }
                    if (def.noteRes != 0) NoteText(stringResource(def.noteRes))
                }
            }
            else -> page = null
        }
        return
    }

    NoteText(stringResource(R.string.rift_settings_platforms_note))

    SettingsGroup(title = stringResource(R.string.rift_settings_group_platforms)) {
        platforms.forEach { entry ->
            val def = EmulatorCatalog.platform(entry.platformId) ?: return@forEach
            LinkRow(
                modifier = Modifier.focusRequester(requester(entry.platformId)),
                title = stringResource(def.nameRes),
                subtitle = listOfNotNull(
                    EmulatorCatalog.labelFor(context, entry.emulatorPackage, def).takeIf { entry.emulatorPackage.isNotBlank() },
                    folderLabel(entry.folderUri),
                ).joinToString(" · "),
                icon = Icons.Rounded.SportsEsports,
                onClick = {
                    focusKey = entry.platformId
                    page = "manage:${entry.platformId}"
                },
            )
        }
        LinkRow(
            modifier = Modifier.focusRequester(requester(KEY_ADD)),
            title = stringResource(R.string.rift_settings_add_platform),
            subtitle = stringResource(R.string.rift_settings_add_platform_summary),
            icon = Icons.Rounded.Add,
            onClick = {
                if (EmulatorCatalog.platforms.none { p -> platforms.none { it.platformId == p.id } }) {
                    SnackbarManager.show(context.getString(R.string.rift_settings_all_platforms_added))
                } else {
                    focusKey = KEY_ADD
                    page = "add"
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
}

private const val KEY_ADD = "add"

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

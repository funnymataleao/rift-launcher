package dev.rift.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R

/**
 * Picks the emulator (and RetroArch's core) of an existing system, from a game's page. The same
 * pickers as in settings; the system keeps its games folder.
 */
@Composable
internal fun EmulatorChoiceDialog(platformId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val def = EmulatorCatalog.platform(platformId)
    if (def == null) {
        onDismiss()
        return
    }
    var picked by remember { mutableStateOf<String?>(null) }

    fun save(emulatorPackage: String, core: String) {
        val prefs = RiftPrefs.of(context)
        prefs.emulatorPlatforms = prefs.emulatorPlatforms.map {
            if (it.platformId == platformId) it.copy(emulatorPackage = emulatorPackage, core = core) else it
        }
        onDismiss()
    }

    val pkg = picked
    if (pkg == null) {
        EmulatorPickerDialog(
            platform = def,
            onPick = {
                if (EmulatorCatalog.launchKind(def, it) == LaunchKind.RETROARCH && def.cores.size > 1) picked = it else save(it, "")
            },
            onDismiss = onDismiss,
        )
    } else {
        RiftChoiceDialog(
            title = stringResource(R.string.rift_settings_pick_core),
            options = def.cores.map { it.label },
            selectedIndex = 0,
            onSelect = { save(pkg, def.cores[it].file) },
            onDismiss = onDismiss,
        )
    }
}

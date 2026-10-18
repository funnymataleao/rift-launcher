package dev.rift.launcher.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.gamenative.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String, val isGame: Boolean)

/**
 * Picks the installed Android games that get their own tab on the home screen. Apps Android marks as
 * games are listed first; nothing is selected until the person chooses it. The tab appears only while
 * at least one game is chosen.
 */
@Composable
internal fun AndroidGamesSettings() {
    val context = LocalContext.current
    val prefs = rememberRiftPrefs()
    var selected by remember { mutableStateOf(prefs.androidGamePackages) }
    var showOthers by rememberSaveable { mutableStateOf(false) }

    val apps by produceState<List<InstalledApp>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    fun toggle(packageName: String, on: Boolean) {
        selected = if (on) selected + packageName else selected - packageName
        prefs.androidGamePackages = selected
    }

    val all = apps
    if (all == null) {
        SettingsGroup(title = stringResource(R.string.rift_settings_android_group_games)) {
            MessageRow(stringResource(R.string.rift_settings_android_loading))
        }
        return
    }
    val games = all.filter { it.isGame }
    val others = all.filterNot { it.isGame }

    SettingsGroup(title = stringResource(R.string.rift_settings_android_group_games)) {
        games.forEach { app -> AppRow(app, app.packageName in selected) { toggle(app.packageName, it) } }
        // What is added and where it shows, on a plate of the group, not as loose text.
        MessageRow(
            if (selected.isEmpty()) {
                stringResource(R.string.rift_settings_android_none)
            } else {
                stringResource(R.string.rift_settings_android_selected, selected.size)
            },
        )
    }

    if (others.isNotEmpty()) {
        SettingsGroup(title = stringResource(R.string.rift_settings_android_group_others)) {
            LinkRow(
                title = stringResource(
                    if (showOthers) R.string.rift_settings_android_hide_others else R.string.rift_settings_android_show_others,
                ),
                subtitle = stringResource(R.string.rift_settings_android_others_summary),
                icon = if (showOthers) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                value = others.size.toString(),
                chevron = false,
                onClick = { showOthers = !showOthers },
            )
            if (showOthers) {
                others.forEach { app -> AppRow(app, app.packageName in selected) { toggle(app.packageName, it) } }
            }
        }
    }

    if (games.isEmpty() && others.isEmpty()) {
        SettingsGroup {
            InfoRow(
                title = stringResource(R.string.rift_settings_android_empty),
                value = "0",
                icon = Icons.Rounded.Apps,
            )
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap() }
                .getOrNull()
        }
    }
    SwitchRow(
        title = app.label,
        subtitle = app.packageName,
        checked = checked,
        onCheckedChange = onCheckedChange,
        leading = {
            icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            } ?: androidx.compose.foundation.layout.Spacer(Modifier.size(44.dp))
        },
    )
}

/** Apps with a launcher icon, except Rift itself, games first and then by name. */
@Suppress("DEPRECATION")
private fun loadLaunchableApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .asSequence()
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { info ->
            InstalledApp(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                isGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                    (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0,
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}

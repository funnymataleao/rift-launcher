package dev.rift.launcher.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager

/** Rift as an app, or as the Android home screen that opens when the Home button is pressed. */
@Composable
internal fun HomeSettings() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val isHome = remember(refresh) { isRiftHomeApp(context) }

    // Back from Android's own picker: the status is read again.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsGroup {
        InfoRow(
            title = stringResource(R.string.rift_settings_home_status),
            value = stringResource(
                if (isHome) R.string.rift_settings_home_status_yes else R.string.rift_settings_home_status_no,
            ),
            icon = Icons.Rounded.Home,
        )
        LinkRow(
            title = stringResource(R.string.rift_settings_home_pick),
            subtitle = stringResource(R.string.rift_settings_home_pick_summary),
            icon = Icons.Rounded.Home,
            onClick = {
                val roleIntent = roleRequestIntent(context)
                val opened = roleIntent != null && runCatching { picker.launch(roleIntent) }.isSuccess
                if (!opened) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                        .onFailure { SnackbarManager.show(context.getString(R.string.rift_settings_home_failed)) }
                }
            },
        )
    }
    NoteText(stringResource(R.string.rift_settings_home_note))
}

/** The system's own "use Rift as the home app?" dialog, where the device has it (Android 10 and up). */
private fun roleRequestIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val roles = context.getSystemService(RoleManager::class.java) ?: return null
    if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return null
    return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
}

/** Whether the home button already opens Rift. */
private fun isRiftHomeApp(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val current = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
    return current?.activityInfo?.packageName == context.packageName
}

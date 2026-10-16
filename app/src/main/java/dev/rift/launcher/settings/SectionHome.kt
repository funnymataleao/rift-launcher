package dev.rift.launcher.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager

/** Rift as an app, or as the Android home screen that opens when the Home button is pressed. */
@Composable
internal fun HomeSettings() {
    val context = LocalContext.current
    SettingsGroup {
        LinkRow(
            title = stringResource(R.string.rift_settings_home_pick),
            subtitle = stringResource(R.string.rift_settings_home_pick_summary),
            icon = Icons.Rounded.Home,
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    .onFailure { SnackbarManager.show(context.getString(R.string.rift_settings_home_failed)) }
            },
        )
    }
    NoteText(stringResource(R.string.rift_settings_home_note))
}

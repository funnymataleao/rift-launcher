package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import dev.rift.launcher.cloud.CloudLibrary
import dev.rift.launcher.cloud.XcloudAccount
import dev.rift.launcher.cloud.XcloudActivity

/**
 * Cloud streaming: signing in to Xbox Cloud Gaming, which is what makes the home's cloud tab appear,
 * and how it works. The sign-in is xbox.com itself, in Rift's browser.
 */
@Composable
internal fun CloudSettings() {
    val context = LocalContext.current
    val signedIn by XcloudAccount.signedIn(context).collectAsStateWithLifecycle()
    val catalog by CloudLibrary.catalog.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val service = stringResource(R.string.rift_cloud_service_xcloud)

    // Signed in: the catalog loads now, so the home tab is ready when the person goes back.
    LaunchedEffect(signedIn) { if (signedIn == true) CloudLibrary.load(context) }

    if (signedIn == true) {
        SettingsGroup(title = service) {
            InfoRow(
                title = stringResource(R.string.rift_cloud_account),
                value = stringResource(R.string.rift_cloud_signed_in),
                icon = Icons.Rounded.AccountCircle,
            )
            InfoRow(
                title = stringResource(R.string.rift_cloud_games_count),
                value = catalog?.games?.size?.toString() ?: stringResource(R.string.rift_cloud_catalog_loading),
                icon = Icons.Rounded.SportsEsports,
            )
            LinkRow(
                title = stringResource(R.string.rift_cloud_sign_out),
                subtitle = stringResource(R.string.rift_cloud_sign_out_summary),
                icon = Icons.AutoMirrored.Rounded.Logout,
                danger = true,
                onClick = { confirmSignOut = true },
            )
        }
    } else {
        SettingsGroup {
            LinkRow(
                title = stringResource(R.string.rift_cloud_sign_in),
                subtitle = stringResource(R.string.rift_cloud_sign_in_summary),
                icon = Icons.Rounded.Cloud,
                onClick = { XcloudActivity.signIn(context) },
            )
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_cloud_how_title)) {
        NoteText(stringResource(R.string.rift_cloud_how))
        InfoRow(
            title = stringResource(R.string.rift_cloud_better_xcloud),
            value = "6.7.12",
            icon = Icons.Rounded.Tune,
        )
        NoteText(stringResource(R.string.rift_cloud_better_xcloud_summary))
    }

    if (confirmSignOut) {
        RiftConfirmDialog(
            title = stringResource(R.string.rift_settings_sign_out_title, service),
            message = stringResource(R.string.rift_settings_sign_out_message),
            confirmText = stringResource(R.string.rift_settings_sign_out),
            dismissText = stringResource(R.string.rift_settings_cancel),
            onConfirm = {
                XcloudAccount.signOut(context)
                confirmSignOut = false
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

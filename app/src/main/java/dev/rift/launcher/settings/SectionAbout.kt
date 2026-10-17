package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.gamenative.BuildConfig
import app.gamenative.Constants
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.component.dialog.LibrariesDialog

/** Version, source, libraries, privacy and support. */
@Composable
internal fun AboutSettings() {
    val uriHandler = LocalUriHandler.current
    val riftPrefs = RiftPrefs.of(LocalContext.current)
    var shareRatings by rememberSaveable { mutableStateOf(riftPrefs.communityShare) }
    var askForTip by rememberSaveable { mutableStateOf(!PrefManager.tipped) }
    var analytics by rememberSaveable { mutableStateOf(PrefManager.usageAnalyticsEnabled) }
    var librariesDialog by rememberSaveable { mutableStateOf(false) }

    SettingsGroup {
        InfoRow(
            title = stringResource(R.string.rift_settings_version),
            value = BuildConfig.VERSION_NAME,
            icon = Icons.Rounded.Info,
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_project)) {
        LinkRow(
            title = stringResource(R.string.settings_info_source_title),
            subtitle = stringResource(R.string.settings_info_source_subtitle),
            icon = Icons.Rounded.Code,
            onClick = { uriHandler.openUri(Constants.Misc.RIFT_SOURCE_LINK) },
        )
        LinkRow(
            title = stringResource(R.string.settings_info_libraries_title),
            subtitle = stringResource(R.string.settings_info_libraries_subtitle),
            icon = Icons.Rounded.Layers,
            onClick = { librariesDialog = true },
        )
        LinkRow(
            title = stringResource(R.string.settings_info_send_tip_title),
            subtitle = stringResource(R.string.settings_info_send_tip_subtitle),
            icon = Icons.Rounded.Favorite,
            onClick = { uriHandler.openUri(Constants.Misc.KO_FI_LINK) },
        )
        SwitchRow(
            title = stringResource(R.string.settings_info_ask_tip_title),
            subtitle = stringResource(R.string.settings_info_ask_tip_subtitle),
            icon = Icons.Rounded.Savings,
            checked = askForTip,
            onCheckedChange = {
                askForTip = it
                PrefManager.tipped = !it
            },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_privacy)) {
        LinkRow(
            title = stringResource(R.string.settings_info_privacy_title),
            subtitle = stringResource(R.string.settings_info_privacy_subtitle),
            icon = Icons.Rounded.PrivacyTip,
            onClick = { uriHandler.openUri(Constants.Misc.PRIVACY_LINK) },
        )
        SwitchRow(
            title = stringResource(R.string.settings_info_usage_analytics_title),
            subtitle = stringResource(R.string.settings_info_usage_analytics_subtitle),
            icon = Icons.Rounded.Analytics,
            checked = analytics,
            onCheckedChange = {
                analytics = it
                PrefManager.usageAnalyticsEnabled = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.rift_community_share_title),
            subtitle = stringResource(R.string.rift_community_share_subtitle),
            icon = Icons.Rounded.Stars,
            checked = shareRatings,
            onCheckedChange = {
                shareRatings = it
                riftPrefs.communityShare = it
            },
        )
    }

    LibrariesDialog(visible = librariesDialog, onDismissRequest = { librariesDialog = false })
}

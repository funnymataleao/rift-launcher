package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Savings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
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

/** Version, licence, source, open-source licences, privacy and support. */
@Composable
internal fun AboutSettings() {
    val uriHandler = LocalUriHandler.current
    val riftPrefs = RiftPrefs.of(LocalContext.current)
    var shareRatings by rememberSaveable { mutableStateOf(riftPrefs.communityShare) }
    var analytics by rememberSaveable { mutableStateOf(PrefManager.usageAnalyticsEnabled) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val licensesRow = remember { FocusRequester() }

    BackHandler(enabled = page != null) { page = page?.let(::licensePageBack) }
    // Back from the licences returns to the row that opened them; opening the section itself must not grab focus.
    var wasOnPage by remember { mutableStateOf(false) }
    LaunchedEffect(page) {
        if (page != null) {
            wasOnPage = true
        } else if (wasOnPage) {
            wasOnPage = false
            runCatching { licensesRow.requestFocus() }
        }
    }
    page?.let { current ->
        if (isLicensePage(current)) {
            LicensesPages(page = current, onOpen = { page = it })
            return
        }
        page = null
    }

    SettingsGroup {
        InfoRow(
            title = stringResource(R.string.rift_settings_version),
            value = BuildConfig.VERSION_NAME,
            icon = Icons.Rounded.Info,
        )
        InfoRow(
            title = stringResource(R.string.rift_about_license_title),
            value = stringResource(R.string.rift_about_license_value),
            icon = Icons.Rounded.Gavel,
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_project)) {
        LinkRow(
            title = stringResource(R.string.settings_info_source_title),
            subtitle = Constants.Misc.RIFT_SOURCE_LINK.removePrefix("https://"),
            icon = Icons.Rounded.Code,
            onClick = { uriHandler.openUri(Constants.Misc.RIFT_SOURCE_LINK) },
        )
        LinkRow(
            modifier = Modifier.focusRequester(licensesRow),
            title = stringResource(R.string.rift_about_licenses_title),
            subtitle = stringResource(R.string.rift_about_licenses_summary),
            icon = Icons.Rounded.Layers,
            onClick = { page = LICENSES_PAGE },
        )
        LinkRow(
            title = stringResource(R.string.settings_info_send_tip_title),
            subtitle = stringResource(R.string.settings_info_send_tip_subtitle),
            icon = Icons.Rounded.Favorite,
            onClick = { uriHandler.openUri(Constants.Misc.KO_FI_LINK) },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_privacy)) {
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
}

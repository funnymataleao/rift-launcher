package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import dev.rift.launcher.cloud.CloudGroup
import dev.rift.launcher.cloud.CloudLibrary
import dev.rift.launcher.cloud.CloudPlaySettings
import dev.rift.launcher.cloud.CloudSetting
import dev.rift.launcher.cloud.XcloudAccount
import dev.rift.launcher.cloud.XcloudActivity

/**
 * Cloud streaming in three tabs: the account (signing in to Xbox Cloud Gaming is what makes the home's
 * cloud tab appear; the sign-in is xbox.com itself, in Rift's browser), the stream's settings and
 * xbox.com's interface. The settings are [CloudPlaySettings] and apply from the next game; the
 * gamepad's Home changes the main ones while playing.
 */
@Composable
internal fun CloudSettings() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SectionTabs(
        tabs = listOf(
            stringResource(R.string.rift_cloud_tab_account),
            stringResource(R.string.rift_cloud_tab_stream),
            stringResource(R.string.rift_cloud_tab_interface),
        ),
        selectedIndex = tab,
        onSelect = { tab = it },
    )
    when (tab) {
        0 -> AccountTab()
        1 -> SettingsTab(CloudPlaySettings.streamGroups)
        else -> SettingsTab(CloudPlaySettings.pageGroups)
    }
}

@Composable
private fun AccountTab() {
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

/** The groups of one settings tab, each row saving to [CloudPlaySettings] as it changes. */
@Composable
private fun SettingsTab(groups: List<CloudGroup>) {
    val context = LocalContext.current
    var values by remember { mutableStateOf(CloudPlaySettings.values(context)) }
    NoteText(stringResource(R.string.rift_cloud_settings_note))
    groups.forEach { group ->
        SettingsGroup(title = stringResource(group.title)) {
            group.settings.forEach { setting ->
                CloudSettingRow(setting, values[setting.key]) { value ->
                    CloudPlaySettings.set(context, setting, value)
                    values = values + (setting.key to value)
                }
            }
        }
    }
}

@Composable
private fun CloudSettingRow(setting: CloudSetting, value: Any?, onChange: (Any) -> Unit) {
    val title = stringResource(setting.title)
    val subtitle = if (setting.summary != 0) stringResource(setting.summary) else null
    when (setting) {
        is CloudSetting.Toggle -> SwitchRow(
            title = title,
            subtitle = subtitle,
            icon = setting.icon,
            checked = value as? Boolean ?: setting.default,
            onCheckedChange = onChange,
        )
        is CloudSetting.Choice -> {
            val labels = setting.options.map { it.label() }
            val index = setting.options.indexOfFirst { it.value == (value ?: setting.default) }
            if (setting.slider) {
                StepSliderRow(
                    title = title,
                    subtitle = subtitle,
                    icon = setting.icon,
                    options = labels,
                    selectedIndex = index,
                    onSelect = { onChange(setting.options[it].value) },
                )
            } else if (setting.chips) {
                ChoiceChipsRow(
                    title = title,
                    subtitle = subtitle,
                    icon = setting.icon,
                    options = labels,
                    selectedIndex = index,
                    onSelect = { onChange(setting.options[it].value) },
                )
            } else {
                var open by remember { mutableStateOf(false) }
                LinkRow(
                    title = title,
                    subtitle = subtitle,
                    icon = setting.icon,
                    value = labels.getOrNull(index),
                    onClick = { open = true },
                )
                if (open) {
                    RiftChoiceDialog(
                        title = title,
                        options = labels,
                        selectedIndex = index,
                        onSelect = { onChange(setting.options[it].value) },
                        onDismiss = { open = false },
                    )
                }
            }
        }
        is CloudSetting.Multi -> {
            val labels = setting.options.map { it.label() }
            @Suppress("UNCHECKED_CAST")
            val chosen = (value as? List<String> ?: setting.default).toSet()
            val picked = setting.options.indices.filter { setting.options[it].value in chosen }.toSet()
            var open by remember { mutableStateOf(false) }
            LinkRow(
                title = title,
                subtitle = subtitle,
                icon = setting.icon,
                value = if (picked.isEmpty()) stringResource(R.string.rift_cloud_none) else picked.joinToString { labels[it] },
                onClick = { open = true },
            )
            if (open) {
                RiftMultiChoiceDialog(
                    title = title,
                    options = labels,
                    selected = picked,
                    onToggle = { index ->
                        val item = setting.options[index].value as String
                        // Kept in the options' order, as the page lists them.
                        val next = if (item in chosen) chosen - item else chosen + item
                        onChange(setting.options.map { it.value as String }.filter { it in next })
                    },
                    doneText = stringResource(R.string.rift_settings_done),
                    onDismiss = { open = false },
                )
            }
        }
    }
}

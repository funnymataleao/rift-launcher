package dev.rift.launcher.settings

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.steamcontroller.TritonBle
import app.gamenative.ui.util.SnackbarManager
import dev.rift.launcher.overlay.ExternalGameSession
import timber.log.Timber

/** The gamepad itself, and how new games treat controllers. */
@Composable
internal fun ControllerSettings() {
    val context = LocalContext.current
    val defaults = rememberContainerDefaults()

    var swapFaceButtons by rememberSaveable { mutableStateOf(PrefManager.swapFaceButtons) }
    var showHints by rememberSaveable { mutableStateOf(PrefManager.showGamepadHints) }

    // Steam Controller (BLE) is opt-in and needs the Nearby devices permission. The switch only reads
    // "on" while the permission is still granted, because the launch path refuses to start the
    // transport without it; a cancelled request must not switch it on.
    var steamController by rememberSaveable {
        mutableStateOf(PrefManager.steamControllerEnabled && TritonBle.hasPermissions(context))
    }
    val setSteamController: (Boolean) -> Unit = { enabled ->
        steamController = enabled
        PrefManager.steamControllerEnabled = enabled
    }
    val bluetoothPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = TritonBle.hasPermissions(context)
        setSteamController(granted)
        if (!granted) {
            val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
            val canAskAgain = activity != null && TritonBle.REQUIRED_PERMISSIONS.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            if (canAskAgain) {
                SnackbarManager.show(context.getString(R.string.settings_interface_steam_controller_denied))
            } else {
                SnackbarManager.show(context.getString(R.string.settings_interface_steam_controller_denied_forever))
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }.onFailure { Timber.w(it, "Could not open app settings for the Bluetooth permission") }
            }
        }
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_gamepad)) {
        QuickAccessRow()
        OverlayServiceRow()
        SwitchRow(
            title = stringResource(R.string.settings_interface_swap_face_buttons_title),
            subtitle = stringResource(R.string.settings_interface_swap_face_buttons_subtitle),
            icon = Icons.Rounded.SwapHoriz,
            checked = swapFaceButtons,
            onCheckedChange = {
                swapFaceButtons = it
                PrefManager.swapFaceButtons = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_show_gamepad_hints_title),
            subtitle = stringResource(R.string.settings_interface_show_gamepad_hints_subtitle),
            icon = Icons.Rounded.Lightbulb,
            checked = showHints,
            onCheckedChange = {
                showHints = it
                PrefManager.showGamepadHints = it
            },
        )
        SwitchRow(
            title = stringResource(R.string.settings_interface_steam_controller_title),
            subtitle = stringResource(R.string.settings_interface_steam_controller_subtitle),
            icon = Icons.Rounded.Bluetooth,
            checked = steamController,
            onCheckedChange = { enabled ->
                if (!enabled || TritonBle.hasPermissions(context)) {
                    setSteamController(enabled)
                } else {
                    bluetoothPermissions.launch(TritonBle.REQUIRED_PERMISSIONS)
                }
            },
        )
        val vibrationValues = listOf(0, 25, 50, 75, 100)
        ChoiceChipsRow(
            title = stringResource(R.string.vibration_intensity),
            icon = Icons.Rounded.Vibration,
            options = vibrationValues.map { if (it == 0) stringResource(R.string.rift_settings_off) else "$it%" },
            selectedIndex = vibrationValues.indexOfFirst { it >= defaults.data.vibrationIntensity }
                .let { if (it < 0) vibrationValues.lastIndex else it },
            onSelect = { index -> defaults.update { it.copy(vibrationIntensity = vibrationValues[index]) } },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_new_games)) {
        SwitchRow(
            title = stringResource(R.string.use_steam_input),
            icon = Icons.Rounded.SportsEsports,
            checked = defaults.data.useSteamInput,
            onCheckedChange = { value -> defaults.update { it.copy(useSteamInput = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_xinput),
            subtitle = stringResource(R.string.rift_settings_xinput_summary),
            icon = Icons.Rounded.Gamepad,
            checked = defaults.data.enableXInput,
            onCheckedChange = { value -> defaults.update { it.copy(enableXInput = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_dinput),
            subtitle = stringResource(R.string.rift_settings_dinput_summary),
            icon = Icons.Rounded.Keyboard,
            checked = defaults.data.enableDInput,
            onCheckedChange = { value -> defaults.update { it.copy(enableDInput = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_disable_mouse),
            subtitle = stringResource(R.string.rift_settings_disable_mouse_summary),
            icon = Icons.Rounded.Mouse,
            checked = defaults.data.disableMouseInput,
            onCheckedChange = { value -> defaults.update { it.copy(disableMouseInput = value) } },
        )
    }
}

/** Rift's panel over Android games works through Rift's accessibility service: shows whether it is on and opens its page. */
@Composable
private fun OverlayServiceRow() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ExternalGameSession.isServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = ExternalGameSession.isServiceEnabled(context)
        onPauseOrDispose { }
    }
    LinkRow(
        title = stringResource(R.string.rift_settings_overlay),
        subtitle = stringResource(R.string.rift_settings_overlay_summary),
        icon = Icons.Rounded.Layers,
        value = stringResource(if (enabled) R.string.rift_settings_on else R.string.rift_settings_off),
        onClick = { ExternalGameSession.openServiceSettings(context) },
    )
}

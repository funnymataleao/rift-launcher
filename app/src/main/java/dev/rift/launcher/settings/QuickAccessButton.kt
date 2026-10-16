package dev.rift.launcher.settings

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.home.RiftControls
import dev.rift.launcher.ui.theme.RiftColors

/**
 * The gamepad button that opens the right-hand panel on the home screen. Pressing the row asks to press
 * the button on the gamepad itself, so it works for any controller.
 */
@Composable
internal fun QuickAccessRow() {
    val prefs = rememberRiftPrefs()
    var keyCode by remember { mutableStateOf(prefs.quickAccessKeyCode) }
    var listening by remember { mutableStateOf(false) }

    LinkRow(
        title = stringResource(R.string.rift_settings_quick_access),
        subtitle = stringResource(R.string.rift_settings_quick_access_summary),
        icon = Icons.Rounded.SportsEsports,
        value = buttonName(keyCode),
        onClick = { listening = true },
    )

    if (listening) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        RiftDialog(
            title = stringResource(R.string.rift_settings_quick_access),
            onDismiss = { listening = false },
            actions = {
                RiftPillButton(
                    text = stringResource(R.string.rift_settings_quick_access_reset),
                    onClick = {
                        keyCode = RiftControls.DEFAULT_QUICK_ACCESS
                        prefs.quickAccessKeyCode = keyCode
                        listening = false
                    },
                )
                RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = { listening = false })
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        val code = event.key.nativeKeyCode
                        if (!KeyEvent.isGamepadButton(code)) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) {
                            keyCode = code
                            prefs.quickAccessKeyCode = code
                            listening = false
                        }
                        true
                    }
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.rift_settings_quick_access_press),
                    color = RiftColors.OnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

private fun buttonName(code: Int): String = when (code) {
    KeyEvent.KEYCODE_BUTTON_MODE -> "Guide"
    KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
    KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
    else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_BUTTON_").removePrefix("KEYCODE_")
        .lowercase().replaceFirstChar { it.uppercase() }
}

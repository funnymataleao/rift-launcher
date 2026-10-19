package dev.rift.launcher.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent

/**
 * A page that a controller can always drive. A touch drops the focus of whatever was selected (and a page
 * opened by a tap never got any), and with nothing focused no key reaches the page: A, LB and RB did
 * nothing. The first controller press then puts the focus back where it was, or on [fallback], or on the
 * first control, and is not passed on. B is left alone: every page closes on it by itself.
 */
@Composable
fun RiftFocusNet(
    modifier: Modifier = Modifier,
    fallback: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val group = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    val latestFallback by rememberUpdatedState(fallback)
    val view = LocalView.current
    DisposableEffect(view) {
        val listener: (AndroidEvent.KeyEvent) -> Boolean = listener@{ e ->
            val ev = e.event
            if (hasFocus || ev.action != KeyEvent.ACTION_DOWN || !view.hasWindowFocus()) return@listener false
            if (ev.keyCode == KeyEvent.KEYCODE_BUTTON_B || ev.keyCode == KeyEvent.KEYCODE_BACK || ev.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                return@listener false
            }
            val fromController = KeyEvent.isGamepadButton(ev.keyCode) ||
                ev.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                ev.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                ev.isFromSource(InputDevice.SOURCE_DPAD)
            if (!fromController) return@listener false
            if (view.isInTouchMode) view.requestFocusFromTouch() else if (!view.hasFocus()) view.requestFocus()
            val target = latestFallback
            if (target == null || runCatching { target.requestFocus() }.isFailure) runCatching { group.requestFocus() }
            true
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(listener)
        onDispose { PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(listener) }
    }
    Box(
        modifier = modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .focusRequester(group)
            .focusRestorer()
            .focusGroup(),
        content = content,
    )
}

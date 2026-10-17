package dev.rift.launcher.home

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import kotlin.math.abs

/** Right stick position (x right, y down, -1..1) for the Apple TV style tilt of the focused cover. */
val LocalRightStick = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

private const val DEAD_ZONE = 0.12f

/** Listens to gamepad motion through GameNative's event bus; never consumes the events. */
@Composable
fun rememberRightStick(): State<Offset> {
    val stick = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val listener: (AndroidEvent.MotionEvent) -> Boolean = { androidEvent ->
            val event = androidEvent.event
            if (event != null && event.actionMasked == MotionEvent.ACTION_MOVE &&
                event.isFromSource(InputDevice.SOURCE_JOYSTICK)
            ) {
                // Most Android pads report the right stick on Z/RZ, some on RX/RY.
                val x = event.getAxisValue(MotionEvent.AXIS_Z).takeIf { abs(it) > DEAD_ZONE }
                    ?: event.getAxisValue(MotionEvent.AXIS_RX).takeIf { abs(it) > DEAD_ZONE } ?: 0f
                val y = event.getAxisValue(MotionEvent.AXIS_RZ).takeIf { abs(it) > DEAD_ZONE }
                    ?: event.getAxisValue(MotionEvent.AXIS_RY).takeIf { abs(it) > DEAD_ZONE } ?: 0f
                stick.value = Offset(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
            }
            false
        }
        PluviaApp.events.on<AndroidEvent.MotionEvent, Boolean>(listener)
        onDispose { PluviaApp.events.off<AndroidEvent.MotionEvent, Boolean>(listener) }
    }
    return stick
}

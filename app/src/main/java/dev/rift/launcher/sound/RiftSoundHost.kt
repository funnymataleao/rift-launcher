package dev.rift.launcher.sound

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable

/** Handle for the screens: `val sounds = rememberRiftSoundPlayer()` then `sounds.play(RiftSound.Launch)`. */
class RiftSoundPlayer(private val context: android.content.Context) {
    fun play(sound: RiftSound) = RiftSounds.play(context, sound)
}

@Composable
fun rememberRiftSoundPlayer(): RiftSoundPlayer {
    val context = LocalContext.current.applicationContext
    return androidx.compose.runtime.remember(context) { RiftSoundPlayer(context) }
}

/**
 * One modifier for a whole screen: gamepad and keyboard presses make sound without touching the screen's own code.
 * D-pad moves tick, A/Enter selects, B backs out, bumpers switch the tab, Select/Start open or close the side panel.
 * Pass [sidebarOpen] so Select/Start know which of the two sounds to play. Launch and touch are played explicitly.
 */
@Composable
fun Modifier.riftKeySounds(sidebarOpen: Boolean = false): Modifier {
    val player = rememberRiftSoundPlayer()
    return onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (event.type == KeyEventType.KeyDown && (key.repeatCount == 0 || isDpad(key.keyCode))) {
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> player.play(RiftSound.Move)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> player.play(RiftSound.Select)
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> player.play(RiftSound.Back)
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> player.play(RiftSound.Tab)
                KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU ->
                    player.play(if (sidebarOpen) RiftSound.SidebarClose else RiftSound.SidebarOpen)
            }
        }
        false
    }
}

private fun isDpad(code: Int) = code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT

package dev.rift.launcher.home

import android.content.Context
import android.view.KeyEvent

/**
 * Gamepad layout, Steam Deck style: Select opens the left filter panel, the quick access button
 * opens the right panel. The quick access button is remappable in settings ([KEY_QUICK_ACCESS]
 * holds a KeyEvent key code in the same prefs file as the other Rift settings). The G8+ has no
 * "..." button, so it defaults to Home/Guide; a long press on Select always works as well, in case
 * the system keeps Guide for itself.
 */
object RiftControls {
    const val KEY_QUICK_ACCESS = "quick_access_keycode"
    const val DEFAULT_QUICK_ACCESS = KeyEvent.KEYCODE_BUTTON_MODE

    fun quickAccessKey(context: Context): Int =
        AndroidGames.prefs(context).getInt(KEY_QUICK_ACCESS, DEFAULT_QUICK_ACCESS)
}

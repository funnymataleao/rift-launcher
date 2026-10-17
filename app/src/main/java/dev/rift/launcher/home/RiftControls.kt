package dev.rift.launcher.home

import android.content.Context
import android.view.KeyEvent

/**
 * Gamepad layout, Steam Deck style: Select opens the left filter panel, holding Select the right
 * (quick access) panel. A dedicated button can be set in settings ([KEY_QUICK_ACCESS] holds a
 * KeyEvent key code in the same prefs file as the other Rift settings). There is none by default:
 * the G8+ has no "..." button, and Android keeps Home/Guide for itself (it leaves the app).
 */
object RiftControls {
    const val KEY_QUICK_ACCESS = "quick_access_keycode"
    const val DEFAULT_QUICK_ACCESS = KeyEvent.KEYCODE_UNKNOWN

    fun quickAccessKey(context: Context): Int =
        AndroidGames.prefs(context).getInt(KEY_QUICK_ACCESS, DEFAULT_QUICK_ACCESS)

    /** KeyEvent key code of a button that opens the stream's side panel besides Select + Start; none by default. */
    const val KEY_STREAM_MENU = "stream_menu_keycode"

    fun streamMenuKey(context: Context): Int =
        AndroidGames.prefs(context).getInt(KEY_STREAM_MENU, KeyEvent.KEYCODE_UNKNOWN)
}

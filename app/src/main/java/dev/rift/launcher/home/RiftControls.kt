package dev.rift.launcher.home

import android.content.Context
import android.view.KeyEvent

/**
 * Gamepad layout (Denis 2026-10-17): Home always opens the right panel, on Rift's screens and in every game
 * (dev.rift.launcher.ui.RiftHomeKey). Select opens the left filter panel on Rift's screens only; in a game it is
 * the game's (a map, say). Another button for the right panel can be set in settings ([KEY_QUICK_ACCESS] holds
 * a KeyEvent key code in the same prefs file as the other Rift settings); none by default.
 */
object RiftControls {
    const val KEY_QUICK_ACCESS = "quick_access_keycode"
    const val DEFAULT_QUICK_ACCESS = KeyEvent.KEYCODE_UNKNOWN

    fun quickAccessKey(context: Context): Int =
        AndroidGames.prefs(context).getInt(KEY_QUICK_ACCESS, DEFAULT_QUICK_ACCESS)

    /** KeyEvent key code of a button that opens the stream's side panel besides Home; none by default. */
    const val KEY_STREAM_MENU = "stream_menu_keycode"

    fun streamMenuKey(context: Context): Int =
        AndroidGames.prefs(context).getInt(KEY_STREAM_MENU, KeyEvent.KEYCODE_UNKNOWN)
}

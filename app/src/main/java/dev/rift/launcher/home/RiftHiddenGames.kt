package dev.rift.launcher.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Games the person hid from Rift (Start menu, "Hide game"): system packages and other things that
 * showed up in a tab and are not wanted. A hidden game is gone from every tab and library until it
 * is brought back in settings. The ids are the home's own ids (a store's app id, EMU_..., ANDROID_...);
 * the name is kept to list the game in settings.
 */
object RiftHiddenGames {
    private const val PREFS = "rift_hidden_games"

    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    private var loaded = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App id to name of every hidden game. */
    @Synchronized
    fun hidden(context: Context): StateFlow<Map<String, String>> {
        if (!loaded) {
            loaded = true
            state.value = prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        }
        return state.asStateFlow()
    }

    fun hide(context: Context, appId: String, name: String) {
        hidden(context)
        prefs(context).edit { putString(appId, name) }
        state.value = state.value + (appId to name)
    }

    fun show(context: Context, appId: String) {
        hidden(context)
        prefs(context).edit { remove(appId) }
        state.value = state.value - appId
    }
}

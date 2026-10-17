package dev.rift.launcher.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.rift.launcher.art.GameArtStore
import dev.rift.launcher.art.RiftAutoArt
import dev.rift.launcher.meta.RiftAutoMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The names the person gave to games of the Emulators and Android tabs (a game file called "SLUS 213 51"
 * is not found by any scraper). The name replaces the game's own wherever Rift shows or searches it. The ids
 * are the home's own (EMU_..., ANDROID_...). Renaming also throws away what was found under the old name,
 * so covers, banner, logo and game info are searched again under the new one.
 */
object RiftGameNames {
    private const val PREFS = "rift_game_names"

    private val state = MutableStateFlow<Map<String, String>>(emptyMap())
    @Volatile
    private var loaded = false

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** App id to the name the person gave it. */
    @Synchronized
    fun names(context: Context): StateFlow<Map<String, String>> {
        if (!loaded) {
            state.value = prefs(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
            loaded = true
        }
        return state.asStateFlow()
    }

    /** [original] unless the person renamed the game. Reads what [names] loaded, so the home loads it first. */
    fun display(appId: String, original: String): String = state.value[appId] ?: original

    /** Gives [appId] a new name (blank, or the game's own, goes back to the own name) and searches it all again. */
    fun rename(context: Context, appId: String, name: String, original: String) {
        names(context)
        val clean = name.trim()
        val keep = clean.isNotEmpty() && clean != original
        if ((state.value[appId] ?: original) == (if (keep) clean else original)) return
        prefs(context).edit { if (keep) putString(appId, clean) else remove(appId) }
        state.value = if (keep) state.value + (appId to clean) else state.value - appId
        RiftAutoArt.forget(context, appId)
        GameArtStore.forgetAuto(context, appId)
        RiftAutoMeta.forget(context, appId)
    }
}

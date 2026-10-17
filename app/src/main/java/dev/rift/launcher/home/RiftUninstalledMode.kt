package dev.rift.launcher.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/** How the PC games tab shows games that are not installed: see-through (default), left out, or like the others. */
enum class UninstalledMode(val key: String) {
    DIM("dim"),
    HIDE("hide"),
    SHOW("show"),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: DIM
    }
}

object RiftUninstalledMode {
    private const val FILE = "rift_prefs"
    const val KEY = "uninstalled_mode"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun sectionKey(section: RiftSection) = "${KEY}_${section.key}"

    /** The default set in settings; a tab uses it until its own choice is made in the filters panel. */
    fun get(context: Context): UninstalledMode = UninstalledMode.fromKey(prefs(context).getString(KEY, null))

    fun get(context: Context, section: RiftSection): UninstalledMode =
        prefs(context).getString(sectionKey(section), null)?.let(UninstalledMode::fromKey) ?: get(context)

    fun set(context: Context, mode: UninstalledMode) {
        prefs(context).edit { putString(KEY, mode.key) }
    }

    fun set(context: Context, section: RiftSection, mode: UninstalledMode) {
        prefs(context).edit { putString(sectionKey(section), mode.key) }
    }

    fun isSectionKey(key: String?) = key == KEY || key?.startsWith("${KEY}_") == true
}

/** The tab's current mode, following changes made in settings and in the filters panel. */
@Composable
fun rememberUninstalledMode(section: RiftSection): State<UninstalledMode> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(RiftUninstalledMode.get(context, section)) }
    DisposableEffect(context) {
        val prefs = context.applicationContext.getSharedPreferences("rift_prefs", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (RiftUninstalledMode.isSectionKey(key)) state.value = RiftUninstalledMode.get(context, section)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

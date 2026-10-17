package dev.rift.launcher.settings

import android.content.Context

/**
 * Remembers which settings section was open when Rift went to the background (a browser opened for an
 * API key, say), so that if the system dropped the app meanwhile, Rift comes back to that section and
 * not to the home screen. The mark lives in preferences because the process may be gone by then.
 */
internal object SettingsReturn {
    private const val PREFS = "rift_prefs"
    private const val KEY_SECTION = "settings_return_section"
    private const val KEY_AT = "settings_return_at"

    /** Longer than this away and Rift opens on the home screen as usual. */
    private const val WINDOW_MS = 30 * 60 * 1000L

    /** Set when Settings is opened again after a restart: the focus goes into the page, not the list. */
    @Volatile
    var focusContentOnOpen = false

    fun mark(context: Context, section: RiftSettingsSection) {
        prefs(context).edit()
            .putString(KEY_SECTION, section.name)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_SECTION).remove(KEY_AT).apply()
    }

    /** The section to go back to, once; null when there is none or it is too old. */
    fun take(context: Context): RiftSettingsSection? {
        val prefs = prefs(context)
        val name = prefs.getString(KEY_SECTION, null) ?: return null
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_AT, 0L) <= WINDOW_MS
        clear(context)
        if (!fresh) return null
        return RiftSettingsSection.entries.firstOrNull { it.name == name }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package dev.rift.launcher.home

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * How the person arranged the home screen's top tabs in Rift's settings: the order, custom names and
 * which tabs are hidden. A tab still only exists while its source is set up (see [RiftSections]); this
 * only changes how the existing ones are shown. Kept in the same file as the other home settings,
 * "rift_prefs".
 */
object RiftTabPrefs {
    private const val FILE = "rift_prefs"
    private const val ORDER = "tab_order"
    private const val HIDDEN = "tab_hidden"
    private const val NAMES = "tab_names"

    const val MAX_NAME_LENGTH = 24

    /** Every tab Rift can show, in the order used until the person changes it. */
    val known: List<RiftSection> = listOf(RiftSection.PcGames, RiftSection.AndroidGames, RiftSection.PcStreaming)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Saved order first, then any tab the person has not placed yet in its default position. */
    fun order(context: Context): List<RiftSection> {
        val saved = prefs(context).getString(ORDER, null).orEmpty().split(',').filter { it.isNotBlank() }
        val placed = saved.mapNotNull { key -> known.firstOrNull { it.key == key } }.distinct()
        return placed + known.filterNot { it in placed }
    }

    fun setOrder(context: Context, order: List<RiftSection>) {
        prefs(context).edit { putString(ORDER, order.joinToString(",") { it.key }) }
    }

    fun hidden(context: Context): Set<String> =
        prefs(context).getStringSet(HIDDEN, emptySet()).orEmpty().toSet()

    fun setHidden(context: Context, hidden: Set<String>) {
        prefs(context).edit { putStringSet(HIDDEN, hidden) }
    }

    /** Custom names by tab key; a tab without an entry keeps its own name. */
    fun names(context: Context): Map<String, String> {
        val raw = prefs(context).getString(NAMES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }.filterValues { it.isNotBlank() }
        }.getOrDefault(emptyMap())
    }

    /** A blank [name] puts the tab's own name back. */
    fun setName(context: Context, section: RiftSection, name: String) {
        val updated = names(context).toMutableMap()
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) updated.remove(section.key) else updated[section.key] = trimmed
        prefs(context).edit { putString(NAMES, JSONObject(updated as Map<*, *>).toString()) }
    }

    /**
     * The tabs that exist right now, in the person's order without the hidden ones. Hiding never empties
     * the home screen: if every existing tab were hidden they are all shown.
     */
    fun arrange(context: Context, sections: List<RiftSection>): List<RiftSection> {
        val order = order(context)
        val sorted = sections.sortedBy { order.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        val hidden = hidden(context)
        return sorted.filterNot { it.key in hidden }.ifEmpty { sorted }
    }
}

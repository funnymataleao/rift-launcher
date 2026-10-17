package dev.rift.launcher.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit
import app.gamenative.R

/** How a tab other than PC games orders its covers. */
enum class TabSort(val key: String, @StringRes val titleRes: Int) {
    NAME_ASC("name_asc", R.string.rift_sort_name_asc),
    NAME_DESC("name_desc", R.string.rift_sort_name_desc),
    DATE_ADDED("date_added", R.string.rift_sort_date_added),
    LAST_PLAYED("last_played", R.string.rift_sort_last_played),
    ;

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NAME_ASC
    }
}

/** A tab's own sort and scope; [showAll] is the PC streaming "all games, not only the ones on the PC". */
data class TabView(val sort: TabSort = TabSort.NAME_ASC, val showAll: Boolean = false)

/**
 * Every tab remembers its own sort and filter (the filters panel works on the tab that is open).
 * PC games keeps using GameNative's own library filters, which it stores itself.
 */
object RiftTabFilters {
    private const val FILE = "rift_prefs"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, section: RiftSection, defaultSort: TabSort = TabSort.NAME_ASC): TabView {
        val prefs = prefs(context)
        return TabView(
            sort = prefs.getString("tab_sort_${section.key}", null)?.let(TabSort::fromKey) ?: defaultSort,
            showAll = prefs.getBoolean("tab_show_all_${section.key}", false),
        )
    }

    fun set(context: Context, section: RiftSection, view: TabView) {
        prefs(context).edit {
            putString("tab_sort_${section.key}", view.sort.key)
            putBoolean("tab_show_all_${section.key}", view.showAll)
        }
    }
}

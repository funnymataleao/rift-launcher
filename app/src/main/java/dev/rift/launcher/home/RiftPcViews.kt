package dev.rift.launcher.home

import android.content.Context
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet

/**
 * The PC games' sort and filters, one set for the home's row and one for the library: GameNative keeps a single
 * set, so a sort picked in the library also reordered the home (Denis 18.10). The set in use is swapped when the
 * library opens and closes.
 */
internal object RiftPcViews {

    data class View(
        val sort: SortOption,
        val filters: EnumSet<AppFilter>,
        val collections: Set<String>,
        val curated: Set<String>,
    )

    const val HOME = "home"
    const val LIBRARY = "library"

    private fun prefs(context: Context) = context.getSharedPreferences("rift_pc_views", Context.MODE_PRIVATE)

    fun of(state: LibraryState) = View(
        state.currentSortOption,
        EnumSet.copyOf(state.appInfoSortType),
        state.selectedSteamCollectionIds,
        state.selectedCuratedListIds,
    )

    fun save(context: Context, key: String, view: View) {
        prefs(context).edit()
            .putString("$key.sort", view.sort.name)
            .putStringSet("$key.filters", view.filters.map { it.name }.toSet())
            .putStringSet("$key.collections", view.collections)
            .putStringSet("$key.curated", view.curated)
            .putBoolean("$key.saved", true)
            .apply()
    }

    /** The saved set, or null when [key] was never saved (it then starts from the set in use). */
    fun load(context: Context, key: String): View? {
        val p = prefs(context)
        if (!p.getBoolean("$key.saved", false)) return null
        val sort = runCatching { SortOption.valueOf(p.getString("$key.sort", null).orEmpty()) }.getOrNull() ?: return null
        val filters = EnumSet.noneOf(AppFilter::class.java).apply {
            p.getStringSet("$key.filters", emptySet()).orEmpty().forEach { name -> runCatching { add(AppFilter.valueOf(name)) } }
        }
        return View(sort, filters, p.getStringSet("$key.collections", emptySet()).orEmpty(), p.getStringSet("$key.curated", emptySet()).orEmpty())
    }
}

package dev.rift.launcher.cloud

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import app.gamenative.data.LibraryItem
import dev.rift.launcher.home.TabSort
import dev.rift.launcher.home.TabView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Game Pass cloud catalog for the whole app: loaded once from the saved copy, refreshed in the
 * background when it is a day old. The home's cloud tab and its game page read it from here.
 */
object CloudLibrary {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogFlow = MutableStateFlow<CloudCatalog?>(null)
    private var loading: Job? = null

    val catalog: StateFlow<CloudCatalog?> = catalogFlow.asStateFlow()

    /** Loads the saved catalog, then downloads a new one if it is missing or old. */
    fun load(context: Context) {
        if (loading?.isActive == true) return
        val appContext = context.applicationContext
        loading = scope.launch {
            if (catalogFlow.value == null) catalogFlow.value = GamePassCatalog.cached(appContext)
            if (GamePassCatalog.isStale(catalogFlow.value)) GamePassCatalog.refresh(appContext)?.let { catalogFlow.value = it }
        }
    }

    fun game(appId: String): CloudGame? =
        appId.takeIf { it.startsWith(APP_ID_PREFIX) }?.removePrefix(APP_ID_PREFIX)
            ?.let { id -> catalogFlow.value?.games?.firstOrNull { it.productId == id } }

    fun isCloud(appId: String) = appId.startsWith(APP_ID_PREFIX)

    internal const val APP_ID_PREFIX = "XCLOUD_"
}

/** A cloud game as a library entry, so the home's covers, art and game page work as for any game. */
fun CloudGame.toLibraryItem() = LibraryItem(
    appId = CloudLibrary.APP_ID_PREFIX + productId,
    name = title,
    capsuleImageUrl = poster,
    headerImageUrl = titledHero,
    heroImageUrl = hero,
    isInstalled = true,
)

/**
 * The cloud streaming tab's data: Game Pass cloud games, the ones started from Rift first (the latest
 * first), then Game Pass's most popular, then the rest of the catalog in its own order.
 */
class CloudStreamingTab internal constructor(private val catalog: CloudCatalog?, private val recent: Map<String, Long>) {
    val games: List<CloudGame> = run {
        val all = catalog?.games.orEmpty()
        val popular = catalog?.popular.orEmpty().withIndex().associate { (index, id) -> id to index }
        all.withIndex().sortedWith(
            compareByDescending<IndexedValue<CloudGame>> { recent[it.value.productId] ?: 0L }
                .thenBy { popular[it.value.productId] ?: Int.MAX_VALUE }
                .thenBy { it.index },
        ).map { it.value }
    }

    val items: List<LibraryItem> = games.map { it.toLibraryItem() }

    /**
     * The tab's covers in the person's chosen order. "Last played" is the tab's own order above; "date
     * added" is the order Game Pass lists its cloud games in.
     */
    fun arrange(view: TabView): List<LibraryItem> = when (view.sort) {
        TabSort.LAST_PLAYED -> items
        TabSort.DATE_ADDED -> catalog?.games.orEmpty().map { it.toLibraryItem() }
        TabSort.NAME_ASC -> items.sortedBy { it.name.lowercase() }
        TabSort.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
    }

    /** Starts [item] in the cloud. */
    fun launch(context: Context, item: LibraryItem): Boolean {
        val game = CloudLibrary.game(item.appId) ?: return false
        XcloudActivity.play(context, game)
        return true
    }
}

/** The cloud tab while the screen using it is shown; empty until signed in and the catalog has loaded. */
@Composable
fun rememberCloudStreamingTab(): CloudStreamingTab {
    val context = LocalContext.current
    val catalog by CloudLibrary.catalog.collectAsStateWithLifecycle()
    val recent by XcloudAccount.recent(context).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { CloudLibrary.load(context) }
    return remember(catalog, recent) { CloudStreamingTab(catalog, recent.orEmpty()) }
}

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
fun CloudGame.toLibraryItem(logo: String = "") = LibraryItem(
    appId = CloudLibrary.APP_ID_PREFIX + productId,
    name = dev.rift.launcher.home.RiftGameNames.display(CloudLibrary.APP_ID_PREFIX + productId, title),
    capsuleImageUrl = poster,
    headerImageUrl = titledHero,
    heroImageUrl = hero,
    isInstalled = true,
    customLogoUrl = logo,
)

/**
 * The cloud streaming tab's data: Game Pass cloud games with the logos found for them so far, and when
 * each was last started from Rift.
 */
class CloudStreamingTab internal constructor(
    private val catalog: CloudCatalog?,
    private val recent: Map<String, Long>,
    private val logos: Map<String, String>,
) {
    val games: List<CloudGame> = catalog?.games.orEmpty()

    val items: List<LibraryItem> = games.map { it.item() }

    private fun CloudGame.item() = toLibraryItem(logos[productId].orEmpty())

    /**
     * The tab's covers in the person's chosen order. "Last played" puts the games started from Rift first,
     * the latest first, and the rest by name; "date added" puts Game Pass's "Recently added" list first,
     * in its order, then the rest by release date, the newest first.
     */
    fun arrange(view: TabView): List<LibraryItem> {
        val byName = compareBy<CloudGame> { it.title.lowercase() }
        val sorted = when (view.sort) {
            TabSort.LAST_PLAYED -> games.sortedWith(compareByDescending<CloudGame> { recent[it.productId] ?: 0L }.then(byName))
            TabSort.DATE_ADDED -> {
                val added = catalog?.recentlyAdded.orEmpty().withIndex().associate { (index, id) -> id to index }
                games.sortedWith(
                    compareBy<CloudGame> { added[it.productId] ?: Int.MAX_VALUE }
                        .thenByDescending { it.releaseDate }
                        .then(byName),
                )
            }
            TabSort.NAME_ASC -> games.sortedWith(byName)
            TabSort.NAME_DESC -> games.sortedWith(byName.reversed())
        }
        return sorted.map { it.item() }
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
    val logos by CloudLogos.logos(context).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { CloudLibrary.load(context) }
    return remember(catalog, recent, logos) { CloudStreamingTab(catalog, recent.orEmpty(), logos) }
}

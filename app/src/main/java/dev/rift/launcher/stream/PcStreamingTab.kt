package dev.rift.launcher.stream

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.data.SteamApp
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.db.dao.SteamLicenseDao
import app.gamenative.enums.AppType
import dev.rift.launcher.home.TabSort
import dev.rift.launcher.home.TabView
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A game on a paired PC: the host app that starts it, shown with Rift's Steam library entry for its art. */
class StreamGame(val item: LibraryItem, val pc: ComputerDetails, val app: NvApp)

/** Any other app a PC's host offers (Desktop, Steam, Playnite, Game Pass...), listed in settings. */
class StreamService(val pc: ComputerDetails, val app: NvApp)

/**
 * The PC streaming tab's data: the paired PCs' host apps, split into games (host apps that are Steam
 * games, which Vibepollo adds when its Steam library sync is on) and everything else.
 *
 * Games are matched to the Steam library Rift already has from the GameNative Steam sign-in, by name,
 * so the tab shows the same covers, art and logos as the PC games tab.
 */
class PcStreamingTab internal constructor(internal val hosts: PcHosts) {
    /** Each PC's apps: the saved list first, then the live one once the PC is online. */
    internal val apps = mutableStateMapOf<String, List<NvApp>>()

    /** PCs whose apps were reloaded live while this screen was shown. */
    internal val refreshed = mutableSetOf<String>()
    internal var steamByName by mutableStateOf<Map<String, SteamApp>>(emptyMap())

    /** When each Steam game was added to the account (license date), by library app id. */
    internal var addedAt by mutableStateOf<Map<String, Long>>(emptyMap())

    internal val paired: List<ComputerDetails>
        get() = hosts.hosts.values.filter { PcStreaming.isPaired(it) }.sortedBy { it.name }

    private val split by derivedStateOf {
        val games = mutableListOf<StreamGame>()
        val services = mutableListOf<StreamService>()
        // Asleep or unreachable PCs keep their games from the saved list; streaming wakes nothing.
        paired.forEach { pc ->
            apps[pc.uuid].orEmpty().forEach { app ->
                val steam = steamByName[normalizedTitle(app.appName)]
                if (steam != null) {
                    if (games.none { it.item.appId == steamAppId(steam) }) games += StreamGame(steam.toLibraryItem(), pc, app)
                } else {
                    services += StreamService(pc, app)
                }
            }
        }
        games.sortedBy { it.item.name.lowercase() } to services.toList()
    }

    /** The tab's covers, in the library's name order. */
    val games: List<StreamGame> get() = split.first

    /** Host apps that are not games Rift knows: Desktop, launchers, input-only apps. */
    val services: List<StreamService> get() = split.second

    val items: List<LibraryItem> get() = games.map { it.item }

    /**
     * The tab's covers for the person's [view]: only the games on the paired PCs, or the whole Steam
     * library with the ones the PC does not have marked not installed (shown dimmed), in the chosen order.
     */
    fun arrange(context: Context, view: TabView): List<LibraryItem> {
        val hosted = games.map { it.item.copy(isInstalled = true) }
        val shown = if (view.showAll) {
            val ids = hosted.map { it.appId }.toSet()
            hosted + steamByName.values
                .filter { it.type == AppType.game && steamAppId(it) !in ids }
                .distinctBy { it.id }
                .map { it.toLibraryItem() }
        } else {
            hosted
        }
        val played = lastPlayed(context)
        return when (view.sort) {
            TabSort.NAME_ASC -> shown.sortedBy { it.name.lowercase() }
            TabSort.NAME_DESC -> shown.sortedByDescending { it.name.lowercase() }
            TabSort.DATE_ADDED -> shown.sortedWith(compareByDescending<LibraryItem> { addedAt[it.appId] ?: 0L }.thenBy { it.name.lowercase() })
            // Streamed from Rift first, newest first; then the rest by when they came to the PC.
            TabSort.LAST_PLAYED -> shown.sortedWith(
                compareByDescending<LibraryItem> { played[it.appId] ?: 0L }
                    .thenByDescending { addedAt[it.appId] ?: 0L }
                    .thenBy { it.name.lowercase() },
            )
        }
    }

    private fun usage(context: Context) = context.applicationContext.getSharedPreferences("rift_stream_usage", Context.MODE_PRIVATE)

    private fun lastPlayed(context: Context): Map<String, Long> =
        usage(context).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /** Starts the stream of the game shown by [item]. */
    fun launch(context: Context, item: LibraryItem): Boolean {
        val game = games.firstOrNull { it.item.appId == item.appId } ?: return false
        usage(context).edit().putLong(item.appId, System.currentTimeMillis()).apply()
        return start(context, game.pc, game.app)
    }

    /** Starts the stream of a host app listed in settings. */
    fun launch(context: Context, service: StreamService): Boolean = start(context, service.pc, service.app)

    private fun start(context: Context, pc: ComputerDetails, app: NvApp): Boolean {
        val uniqueId = hosts.uniqueId ?: return false
        PcStreaming.startStream(context, pc, app, uniqueId)
        return true
    }
}

/** Live state of the paired PCs and their apps, while the screen using it is shown. */
@Composable
fun rememberPcStreamingTab(): PcStreamingTab {
    val context = LocalContext.current
    val hosts = rememberPcHosts()
    val tab = remember(hosts) { PcStreamingTab(hosts) }

    LaunchedEffect(tab) {
        tab.addedAt = withContext(Dispatchers.IO) {
            runCatching {
                val entry = EntryPointAccessors.fromApplication(context.applicationContext, StreamLibraryEntryPoint::class.java)
                val created = entry.licenseDao().getAllLicenses().associate { it.packageId to it.timeCreated.time }
                entry.steamAppDao().getAllOwnedAppsAsList().mapNotNull { app ->
                    created[app.packageId]?.let { steamAppId(app) to it }
                }.toMap()
            }.getOrDefault(emptyMap())
        }
        tab.steamByName = withContext(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, StreamLibraryEntryPoint::class.java)
                    .steamAppDao()
                    .getAllOwnedAppsAsList()
                    .associateBy { normalizedTitle(it.name) }
            }.getOrDefault(emptyMap())
        }
    }

    LaunchedEffect(tab) {
        val cached = withContext(Dispatchers.IO) { runCatching { PcStreaming.cachedApps(context) }.getOrDefault(emptyMap()) }
        cached.forEach { (uuid, apps) -> if (tab.apps[uuid] == null) tab.apps[uuid] = apps }
    }

    // Apps reload once per PC when it comes online, and are saved for when it is not.
    val uniqueId = tab.hosts.uniqueId
    tab.paired.forEach { pc ->
        val online = PcStreaming.isOnline(pc)
        LaunchedEffect(pc.uuid, online, uniqueId) {
            val uuid = pc.uuid ?: return@LaunchedEffect
            if (online && uniqueId != null && uuid !in tab.refreshed) {
                runCatching { PcStreaming.loadApps(context, pc, uniqueId) }.onSuccess {
                    tab.apps[uuid] = it
                    tab.refreshed += uuid
                }
            }
        }
    }
    return tab
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamLibraryEntryPoint {
    fun steamAppDao(): SteamAppDao
    fun licenseDao(): SteamLicenseDao
}

private fun steamAppId(app: SteamApp) = "${GameSource.STEAM.name}_${app.id}"

/**
 * The same fields the library builds for a Steam game, so the home's covers, art and logo work as is.
 * Games Rift has no library art for yet fall back to Steam's standard 600x900 cover and hero, so the
 * shelf never stretches the wide header into a cover.
 */
private fun SteamApp.toLibraryItem() = LibraryItem(
    appId = steamAppId(this),
    name = name,
    iconHash = clientIconHash,
    capsuleImageUrl = getCapsuleUrl().ifEmpty { steamAsset(id, "library_600x900.jpg") },
    headerImageUrl = headerUrl,
    heroImageUrl = getHeroUrl().ifEmpty { steamAsset(id, "library_hero.jpg") },
    gameSource = GameSource.STEAM,
)

private fun steamAsset(appId: Int, file: String) = "https://shared.steamstatic.com/store_item_assets/steam/apps/$appId/$file"

/** Title key shared by Steam and the host: case, accents, ™/® and punctuation do not matter. */
private fun normalizedTitle(title: String?): String =
    Normalizer.normalize(title.orEmpty().replace(Regex("[™®©]"), ""), Normalizer.Form.NFKD)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

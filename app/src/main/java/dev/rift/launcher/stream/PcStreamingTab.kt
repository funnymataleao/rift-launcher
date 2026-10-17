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
    internal val apps = mutableStateMapOf<String, List<NvApp>>()
    internal var steamByName by mutableStateOf<Map<String, SteamApp>>(emptyMap())

    internal val paired: List<ComputerDetails>
        get() = hosts.hosts.values.filter { PcStreaming.isPaired(it) }.sortedBy { it.name }

    private val split by derivedStateOf {
        val games = mutableListOf<StreamGame>()
        val services = mutableListOf<StreamService>()
        paired.filter { PcStreaming.isOnline(it) }.forEach { pc ->
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

    /** Starts the stream of the game shown by [item]. */
    fun launch(context: Context, item: LibraryItem): Boolean {
        val game = games.firstOrNull { it.item.appId == item.appId } ?: return false
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
        tab.steamByName = withContext(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(context.applicationContext, StreamLibraryEntryPoint::class.java)
                    .steamAppDao()
                    .getAllOwnedAppsAsList()
                    .associateBy { normalizedTitle(it.name) }
            }.getOrDefault(emptyMap())
        }
    }

    // Apps load once per PC when it comes online.
    val uniqueId = tab.hosts.uniqueId
    tab.paired.forEach { pc ->
        val online = PcStreaming.isOnline(pc)
        LaunchedEffect(pc.uuid, online, uniqueId) {
            if (online && uniqueId != null && tab.apps[pc.uuid] == null) {
                runCatching { PcStreaming.loadApps(context, pc, uniqueId) }.onSuccess { tab.apps[pc.uuid] = it }
            }
        }
    }
    return tab
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamLibraryEntryPoint {
    fun steamAppDao(): SteamAppDao
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

package dev.rift.launcher.xbox

import android.content.Context
import app.gamenative.data.LibraryItem
import dev.rift.launcher.cloud.CloudGame
import dev.rift.launcher.cloud.GamePassCatalog
import dev.rift.launcher.cloud.GamePassCatalog.toJson
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** A game on one of the account's consoles, with the store's title, texts and art when it has them. */
data class XboxGame(val installed: XboxInstalledGame, val details: CloudGame?) {
    val title: String get() = details?.title ?: installed.name
}

/** The consoles and their games as last seen; kept in a file so the home shows them before the network answers. */
data class XboxSnapshot(val consoles: List<XboxConsole>, val games: List<XboxGame>, val fetchedAt: Long)

/**
 * The Xbox consoles of the signed-in account and the games installed on them, for the whole app: the
 * home streaming tab shows the games next to the PC's, settings show the consoles.
 *
 * Loaded from the saved copy first, then from Xbox Live when it is older than [MAX_AGE_MS] (or on [refresh]).
 * Art and texts come from the Microsoft Store catalog, like the cloud tab's.
 */
object XboxLibrary {
    internal const val APP_ID_PREFIX = "XHOME_"
    private const val MAX_AGE_MS = 10L * 60 * 1000
    private const val CATALOG_BATCH = 20

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshotFlow = MutableStateFlow<XboxSnapshot?>(null)
    private val loadingFlow = MutableStateFlow(false)
    private var job: Job? = null

    val snapshot: StateFlow<XboxSnapshot?> = snapshotFlow.asStateFlow()

    /** True while the consoles are being read from Xbox Live. */
    val loading: StateFlow<Boolean> = loadingFlow.asStateFlow()

    private fun cacheFile(context: Context) = File(context.filesDir, "xbox/library.json")

    /** Shows the saved consoles and games, then reloads them when they are old. Does nothing when signed out. */
    fun load(context: Context, force: Boolean = false) {
        if (!XboxAuth.isSignedIn(context)) return
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch {
            if (snapshotFlow.value == null) snapshotFlow.value = cached(appContext)
            val current = snapshotFlow.value
            if (force || current == null || System.currentTimeMillis() - current.fetchedAt > MAX_AGE_MS) {
                loadingFlow.value = true
                try {
                    fetch(appContext)?.let { snapshotFlow.value = it }
                } finally {
                    loadingFlow.value = false
                }
            }
        }
    }

    /** Forgets everything when the account signs out. */
    fun clear(context: Context) {
        job?.cancel()
        snapshotFlow.value = null
        runCatching { cacheFile(context).delete() }
    }

    fun isXbox(appId: String) = appId.startsWith(APP_ID_PREFIX)

    fun game(appId: String): XboxGame? =
        appId.takeIf(::isXbox)?.removePrefix(APP_ID_PREFIX)
            ?.let { id -> snapshotFlow.value?.games?.firstOrNull { it.installed.productId == id } }

    fun console(id: String): XboxConsole? = snapshotFlow.value?.consoles?.firstOrNull { it.id == id }

    private suspend fun fetch(context: Context): XboxSnapshot? = runCatching {
        val consoles = XboxConsoles.list(context)
        // A console that does not answer (remote features off) keeps the games it had last time.
        val previous = snapshotFlow.value?.games.orEmpty().groupBy { it.installed.consoleId }
        val installed = consoles.flatMap { console ->
            runCatching { XboxConsoles.installedGames(context, console.id) }
                .onFailure { Timber.w(it, "Xbox: no game list from ${console.name}") }
                .getOrElse { previous[console.id].orEmpty().map(XboxGame::installed) }
        }.distinctBy { it.productId }
        val known = snapshotFlow.value?.games.orEmpty().mapNotNull { it.details }.associateBy { it.productId }
        val missing = installed.map { it.productId }.filter { it !in known }
        val details = known + withContext(Dispatchers.IO) {
            missing.chunked(CATALOG_BATCH).flatMap { batch ->
                runCatching { GamePassCatalog.products(batch) }.getOrDefault(emptyList())
            }
        }.associateBy { it.productId }
        XboxSnapshot(consoles, installed.map { XboxGame(it, details[it.productId]) }, System.currentTimeMillis())
            .also { save(context, it) }
    }.onFailure { Timber.w(it, "Xbox: consoles not loaded") }.getOrNull()

    private suspend fun cached(context: Context): XboxSnapshot? = withContext(Dispatchers.IO) {
        runCatching { cacheFile(context).takeIf { it.exists() }?.readText()?.let(::parse) }
            .onFailure { Timber.w(it, "Xbox library cache unreadable") }
            .getOrNull()
    }

    private fun save(context: Context, snapshot: XboxSnapshot) {
        val json = JSONObject()
            .put("fetchedAt", snapshot.fetchedAt)
            .put(
                "consoles",
                JSONArray(
                    snapshot.consoles.map {
                        JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("power", it.powerState)
                    },
                ),
            )
            .put(
                "games",
                JSONArray(
                    snapshot.games.map { game ->
                        JSONObject()
                            .put("console", game.installed.consoleId)
                            .put("product", game.installed.productId)
                            .put("title", game.installed.titleId)
                            .put("name", game.installed.name)
                            .put("lastActive", game.installed.lastActive)
                            .apply { game.details?.let { put("details", it.toJson()) } }
                    },
                ),
            )
        val file = cacheFile(context)
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }

    private fun parse(raw: String): XboxSnapshot {
        val json = JSONObject(raw)
        val consoles = json.optJSONArray("consoles") ?: JSONArray()
        val games = json.optJSONArray("games") ?: JSONArray()
        return XboxSnapshot(
            consoles = (0 until consoles.length()).map { i ->
                val item = consoles.getJSONObject(i)
                XboxConsole(item.getString("id"), item.optString("name"), item.optString("type"), item.optString("power"))
            },
            games = (0 until games.length()).map { i ->
                val item = games.getJSONObject(i)
                XboxGame(
                    installed = XboxInstalledGame(
                        consoleId = item.getString("console"),
                        productId = item.getString("product"),
                        titleId = item.optLong("title"),
                        name = item.optString("name"),
                        lastActive = item.optLong("lastActive"),
                    ),
                    details = item.optJSONObject("details")?.let(GamePassCatalog::cloudGame),
                )
            },
            fetchedAt = json.optLong("fetchedAt"),
        )
    }
}

/** A console game as a library entry, so the home's covers, art and game page work as for any game. */
fun XboxGame.toLibraryItem() = LibraryItem(
    appId = XboxLibrary.APP_ID_PREFIX + installed.productId,
    name = title,
    capsuleImageUrl = details?.poster.orEmpty(),
    headerImageUrl = details?.titledHero.orEmpty(),
    heroImageUrl = details?.hero.orEmpty(),
    isInstalled = true,
)

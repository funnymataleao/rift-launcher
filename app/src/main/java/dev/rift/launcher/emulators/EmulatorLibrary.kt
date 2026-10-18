package dev.rift.launcher.emulators

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.ui.util.SnackbarManager
import dev.rift.launcher.overlay.ExternalGameSession
import dev.rift.launcher.home.TabSort
import dev.rift.launcher.home.TabView
import dev.rift.launcher.settings.EmulatorCatalog
import dev.rift.launcher.settings.EmulatorPlatform
import dev.rift.launcher.settings.LaunchKind
import dev.rift.launcher.settings.PlatformDef
import dev.rift.launcher.settings.RiftPrefs
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** One game file found in a system's games folder. */
data class EmulatorGame(
    val appId: String,
    val platformId: String,
    val name: String,
    val documentUri: String,
    val modified: Long,
    /** The game is a folder (a PS3 or PS4 dump), not a file. */
    val isFolder: Boolean = false,
)

/** What the last scan of a system's folder found: how many games, or that the folder could not be read. */
data class ScanStatus(val count: Int, val failed: Boolean = false)

/** An emulator game as a library entry, so the home's covers, art and library grid work as for any game. */
fun EmulatorGame.toLibraryItem() = LibraryItem(
    appId = appId,
    name = dev.rift.launcher.home.RiftGameNames.display(appId, name),
    isInstalled = true,
)

/**
 * The games of every system the user added in settings, for the home's Emulators tab. The folders are
 * scanned in the background whenever the systems change and whenever Rift comes back to the front, so a
 * new game, a new system or a newly installed emulator shows up at once, without restarting Rift. The
 * last result is kept on disk so the tab is there on the first frame of the next start.
 */
object EmulatorLibrary {
    const val APP_ID_PREFIX = "EMU_"

    private const val TAG = "RiftEmulators"
    private val NOT_GAMES = setOf("dlc", "dlcs", "update", "updates", "patch", "patches")
    private const val USAGE_PREFS = "rift_emulator_usage"
    private const val CACHE_FILE = "emulator_games.json"
    private const val MAX_DEPTH = 5
    private const val MAX_GAMES_PER_SYSTEM = 4000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gamesFlow = MutableStateFlow<List<EmulatorGame>>(emptyList())
    private var job: Job? = null
    private var cacheRead = false

    // SharedPreferences keeps its listeners weakly, so this one is held here.
    private var settingsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val games: StateFlow<List<EmulatorGame>> = gamesFlow.asStateFlow()

    private val statusFlow = MutableStateFlow<Map<String, ScanStatus>>(emptyMap())

    /** By system id: what the last scan of its folder found, so settings can say when a folder holds no games. */
    val scanStatus: StateFlow<Map<String, ScanStatus>> = statusFlow.asStateFlow()

    fun isEmulatorGame(appId: String) = appId.startsWith(APP_ID_PREFIX)

    fun game(appId: String): EmulatorGame? = gamesFlow.value.firstOrNull { it.appId == appId }

    /** Scans the systems' folders again in the background; the first call also starts watching the settings. */
    @Synchronized
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (settingsListener == null) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == RiftPrefs.EMULATOR_PLATFORMS) refresh(app)
            }
            settingsListener = listener
            app.getSharedPreferences(RiftPrefs.FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        }
        job?.cancel()
        job = scope.launch {
            val platforms = RiftPrefs.of(app).emulatorPlatforms.filter { it.folderUri.isNotBlank() }
            if (!cacheRead) {
                cacheRead = true
                val ids = platforms.map { it.platformId }.toSet()
                readCache(app).filter { it.platformId in ids }.takeIf { it.isNotEmpty() }?.let { gamesFlow.value = it }
            }
            val status = HashMap<String, ScanStatus>()
            val found = platforms.flatMap { entry ->
                val def = EmulatorCatalog.platform(entry.platformId) ?: return@flatMap emptyList()
                ensureActive()
                val result = runCatching { scan(app, entry, def) }
                result.exceptionOrNull()?.let { Log.w(TAG, "Scanning ${entry.platformId} in ${entry.folderUri} failed", it) }
                val games = result.getOrDefault(emptyList())
                Log.i(TAG, "Scanned ${entry.platformId} in ${entry.folderUri}: ${games.size} games" + if (result.isFailure) " (folder could not be read)" else "")
                status[entry.platformId] = ScanStatus(games.size, failed = result.isFailure)
                games
            }
            ensureActive()
            statusFlow.value = status
            gamesFlow.value = found
            writeCache(app, found)
        }
    }

    // --- Scanning ---

    private fun scan(context: Context, entry: EmulatorPlatform, def: PlatformDef): List<EmulatorGame> {
        val tree = Uri.parse(entry.folderUri)
        val root = DocumentsContract.getTreeDocumentId(tree)
        val out = ArrayList<EmulatorGame>()
        walk(context, tree, root, 0, def, out)
        return out
    }

    /** One entry of a folder listing. */
    private class Child(val id: String, val name: String, val modified: Long, val size: Long)

    private fun listChildren(context: Context, tree: Uri, parentId: String): Pair<List<Child>, List<Child>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val folders = ArrayList<Child>()
        val files = ArrayList<Child>()
        val cursor = context.contentResolver.query(children, columns, null, null, null)
            ?: throw IllegalStateException("The folder could not be listed: $parentId")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val child = Child(id, name, it.getLong(3), it.getLong(4))
                if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) folders += child else files += child
            }
        }
        return folders to files
    }

    private fun walk(
        context: Context,
        tree: Uri,
        parentId: String,
        depth: Int,
        def: PlatformDef,
        out: MutableList<EmulatorGame>,
        self: Pair<String, Long>? = null,
    ) {
        val (folders, files) = listChildren(context, tree, parentId)
        // Systems whose games are folders: a folder that holds one of the marker names is a game in itself.
        if (self != null && def.gameFolders.isNotEmpty() &&
            (files + folders).any { it.name.lowercase() in def.gameFolders }
        ) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId).toString()
            val title = gameTitle(context, tree, files, folders) ?: displayName(self.first)
            out += EmulatorGame(appIdFor(def.id, uri), def.id, title, uri, self.second, isFolder = true)
            return
        }
        // A disc image split in a .cue and a .bin is one game: the .cue is listed, not the .bin beside it.
        val cues = files.map { it.name.lowercase() }.filter { it.endsWith(".cue") }.map { it.removeSuffix(".cue") }.toSet()
        var listed = files.filter { file ->
            val dot = file.name.lastIndexOf('.')
            dot > 0 && file.name.substring(dot + 1).lowercase() in def.extensions &&
                !((file.name.substring(dot + 1).lowercase() == "bin" || file.name.substring(dot + 1).lowercase() == "img") &&
                    file.name.substring(0, dot).lowercase() in cues)
        }
        // PS4 packages: a folder with several is the game and its updates; the biggest is the game.
        if (def.id == "ps4" && listed.size > 1) listed = listOf(listed.maxBy { it.size })
        for (file in listed) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id).toString()
            out += EmulatorGame(appIdFor(def.id, uri), def.id, displayName(file.name.substringBeforeLast('.')), uri, file.modified)
            if (out.size >= MAX_GAMES_PER_SYSTEM) return
        }
        if (depth >= MAX_DEPTH) return
        for (folder in folders) {
            val lower = folder.name.lowercase()
            // Add-ons and updates are not games of their own.
            if (def.gameFolders.isNotEmpty() && lower in NOT_GAMES) continue
            // A folder named like a game file ("Name.ps3") is the game, not a place to look in.
            val dot = folder.name.lastIndexOf('.')
            if (dot > 0 && folder.name.substring(dot + 1).lowercase() in def.extensions && def.gameFolders.isNotEmpty()) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, folder.id).toString()
                out += EmulatorGame(appIdFor(def.id, uri), def.id, displayName(folder.name.substring(0, dot)), uri, folder.modified, isFolder = true)
            } else {
                walk(context, tree, folder.id, depth + 1, def, out, folder.name to folder.modified)
            }
            if (out.size >= MAX_GAMES_PER_SYSTEM) return
        }
    }

    /** The game's title from its PARAM.SFO (next to the game, or in PS3_GAME / sce_sys), or null when there is none. */
    private fun gameTitle(context: Context, tree: Uri, files: List<Child>, folders: List<Child>): String? = runCatching {
        var sfo = files.firstOrNull { it.name.equals("param.sfo", ignoreCase = true) }
        if (sfo == null) {
            val inner = folders.firstOrNull { it.name.equals("ps3_game", ignoreCase = true) || it.name.equals("sce_sys", ignoreCase = true) }
            sfo = inner?.let { listChildren(context, tree, it.id).second.firstOrNull { f -> f.name.equals("param.sfo", ignoreCase = true) } }
        }
        sfo?.let { readSfoTitle(context, DocumentsContract.buildDocumentUriUsingTree(tree, it.id)) }
    }.getOrNull()

    /** The TITLE entry of a PARAM.SFO file (the format PlayStation systems keep a game's name in). */
    private fun readSfoTitle(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 20 || buffer.getInt(0) != 0x46535000) return null
        val keys = buffer.getInt(8)
        val data = buffer.getInt(12)
        val count = buffer.getInt(16)
        for (i in 0 until count) {
            val entry = 20 + i * 16
            if (entry + 16 > bytes.size) break
            val keyStart = keys + buffer.getShort(entry).toInt() and 0xFFFF
            var keyEnd = keyStart
            while (keyEnd < bytes.size && bytes[keyEnd] != 0.toByte()) keyEnd++
            if (String(bytes, keyStart, keyEnd - keyStart, Charsets.UTF_8) != "TITLE") continue
            val start = data + buffer.getInt(entry + 12)
            var end = start
            val limit = minOf(bytes.size, start + buffer.getInt(entry + 4))
            while (end < limit && bytes[end] != 0.toByte()) end++
            return String(bytes, start, end - start, Charsets.UTF_8).replace('\n', ' ').trim().ifEmpty { null }
        }
        return null
    }

    /** The file name without the "(USA)", "[!]" and similar tags dumps carry. */
    private fun displayName(base: String): String {
        val cleaned = base.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace('_', ' ').trim()
        return cleaned.ifEmpty { base }
    }

    private fun appIdFor(platformId: String, uri: String): String {
        val hash = MessageDigest.getInstance("MD5").digest(uri.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
        return "$APP_ID_PREFIX${platformId}_$hash"
    }

    // --- The saved copy of the last scan ---

    private fun readCache(context: Context): List<EmulatorGame> = runCatching {
        val array = JSONArray(File(context.filesDir, CACHE_FILE).readText())
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            EmulatorGame(o.getString("id"), o.getString("platform"), o.getString("name"), o.getString("uri"), o.optLong("modified"), o.optBoolean("dir"))
        }
    }.getOrDefault(emptyList())

    private fun writeCache(context: Context, games: List<EmulatorGame>) {
        runCatching {
            val array = JSONArray()
            games.forEach {
                array.put(
                    JSONObject().put("id", it.appId).put("platform", it.platformId).put("name", it.name)
                        .put("uri", it.documentUri).put("modified", it.modified).put("dir", it.isFolder),
                )
            }
            File(context.filesDir, CACHE_FILE).writeText(array.toString())
        }
    }

    /** The games as library entries in the order the tab is set to ("last played" puts the ones never played last, by name). */
    fun arrange(context: Context, games: List<EmulatorGame>, view: TabView): List<LibraryItem> {
        val sorted = when (view.sort) {
            TabSort.NAME_ASC -> games.sortedBy { it.name.lowercase() }
            TabSort.NAME_DESC -> games.sortedByDescending { it.name.lowercase() }
            TabSort.DATE_ADDED -> games.sortedWith(compareByDescending<EmulatorGame> { it.modified }.thenBy { it.name.lowercase() })
            TabSort.LAST_PLAYED -> {
                val played = lastPlayed(context)
                games.sortedWith(compareByDescending<EmulatorGame> { played[it.appId] ?: 0L }.thenBy { it.name.lowercase() })
            }
        }
        return sorted.map { it.toLibraryItem() }
    }

    // --- Playing ---

    /** When each game was last started from Rift, for the tab's "last played" order. */
    fun lastPlayed(context: Context): Map<String, Long> =
        context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /**
     * Starts [game] in the emulator its system is set up with. Returns false when nothing could be started;
     * the person is told why. When the emulator cannot open the game by itself, its own app is opened instead.
     */
    fun launch(context: Context, game: EmulatorGame): Boolean {
        val entry = RiftPrefs.of(context).emulatorPlatforms.firstOrNull { it.platformId == game.platformId }
        if (entry == null || entry.emulatorPackage.isBlank()) {
            SnackbarManager.show(context.getString(R.string.rift_emulator_not_set))
            return false
        }
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(entry.emulatorPackage) == null) {
            SnackbarManager.show(context.getString(R.string.rift_emulator_missing, EmulatorCatalog.labelFor(context, entry.emulatorPackage)))
            return false
        }
        val def = EmulatorCatalog.platform(game.platformId)
        val attempts = runCatching { intents(context, game, entry, def) }.getOrDefault(emptyList())
        ExternalGameSession.started(context, entry.emulatorPackage)
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ExternalGameSession.setResumeIntent(context, intent)
                context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).edit { putLong(game.appId, System.currentTimeMillis()) }
                return true
            } catch (e: ActivityNotFoundException) {
                // Not this one; try the next way to open the game.
            } catch (e: SecurityException) {
                // The same.
            }
        }
        // The emulator does not take games from outside: open it, so the person can pick the game there.
        val open = pm.getLaunchIntentForPackage(entry.emulatorPackage) ?: return false
        SnackbarManager.show(context.getString(R.string.rift_emulator_opened_only, EmulatorCatalog.labelFor(context, entry.emulatorPackage)))
        context.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ExternalGameSession.setResumeIntent(context, open)
        return true
    }

    /** The intents to try for [game], best first. */
    private fun intents(context: Context, game: EmulatorGame, entry: EmulatorPlatform, def: PlatformDef?): List<Intent> {
        val pkg = entry.emulatorPackage
        // An extracted Xbox 360 game is a folder; the emulator opens the game through its default.xex.
        val uri = Uri.parse(game.documentUri).let { folder ->
            if (game.isFolder && game.platformId == "xbox360") {
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(Uri.parse(entry.folderUri), DocumentsContract.getDocumentId(folder) + "/default.xex")
                }.getOrDefault(folder)
            } else {
                folder
            }
        }
        val known = EmulatorCatalog.known(def, pkg)
        fun base(action: String): Intent = Intent(action).apply {
            data = uri
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        fun explicit(intent: Intent, activity: String): Intent = intent.apply { component = ComponentName(pkg, activity) }
        val clearTask = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        return when (EmulatorCatalog.launchKind(def, pkg)) {
            LaunchKind.YUZU -> listOf(
                explicit(base("android.nfc.action.TECH_DISCOVERED"), "org.yuzu.yuzu_emu.activities.EmulationActivity"),
                base("android.nfc.action.TECH_DISCOVERED").setPackage(pkg),
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.CITRA -> listOf(
                explicit(base(Intent.ACTION_VIEW), "org.citra.citra_emu.activities.EmulationActivity").addFlags(clearTask),
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.DUCKSTATION -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "com.github.stenzek.duckstation.EmulationActivity")
                    putExtra("bootPath", uri.toString())
                    putExtra("resumeState", false)
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.AETHERSX2 -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "xyz.aethersx2.android.EmulationActivity")
                    putExtra("bootPath", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.MELONDS -> listOf(
                Intent("me.magnum.melonds.LAUNCH_ROM").apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    component = ComponentName(pkg, "me.magnum.melonds.ui.emulator.EmulatorActivity")
                    putExtra("uri", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.DOLPHIN -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    component = ComponentName(pkg, "org.dolphinemu.dolphinemu.ui.main.TvMainActivity")
                    putExtra("AutoStartFile", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.VITA3K -> {
                val titleId = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLine() }?.trim().orEmpty()
                if (titleId.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        Intent(Intent.ACTION_MAIN).apply {
                            addFlags(clearTask)
                            component = ComponentName(pkg, "org.vita3k.emulator.Emulator")
                            putExtra("AppStartParameters", arrayOf("-r", titleId))
                        },
                    )
                }
            }
            LaunchKind.RETROARCH -> {
                val path = rawPath(uri)
                val core = EmulatorCatalog.coreFile(def, entry.core)
                if (path == null || core == null) {
                    emptyList()
                } else {
                    listOf(
                        Intent(Intent.ACTION_MAIN).apply {
                            component = ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
                            putExtra("ROM", path)
                            putExtra("LIBRETRO", "/data/user/0/$pkg/cores/${core}_libretro_android.so")
                            putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg")
                        },
                    )
                }
            }
            LaunchKind.KENJINX -> listOf(
                Intent("org.kenjinx.android.LAUNCH_GAME").apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "org.kenjinx.android.MainActivity")
                    putExtra("bootPath", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.APS3E -> listOf(
                Intent("aenu.intent.action.APS3E").apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    component = ComponentName(pkg, "aenu.aps3e.EmulatorActivity")
                    // A disc image goes in as its URI, a game folder as its path.
                    if (game.isFolder) putExtra("game_dir", rawPath(uri) ?: uri.toString()) else putExtra("iso_uri", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.ARMSX3 -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "com.armsx2.Main")
                    putExtra("path", if (game.isFolder) rawPath(uri) ?: uri.toString() else uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.EMUCOREC -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "com.sbro.emucorec.core.ps3.Emulator")
                    putExtra("gamePath", rawPath(uri) ?: uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.BACHATAS4 -> {
                // Bachata S4 starts only games installed inside it, by their CUSA code; the code is read from the file's name.
                val serial = Regex("CUSA\\d{5}", RegexOption.IGNORE_CASE).find(Uri.decode(game.documentUri))?.value?.uppercase()
                if (serial == null) {
                    emptyList()
                } else {
                    listOf(
                        Intent(Intent.ACTION_MAIN).apply {
                            addFlags(clearTask)
                            component = ComponentName(pkg, "com.bachatas4.android.DirectLaunchActivity")
                            putExtra("game_id", serial)
                        },
                    )
                }
            }
            LaunchKind.VIEW -> listOfNotNull(
                known?.activity?.let { explicit(base(Intent.ACTION_VIEW), it) },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
        }
    }

    /** The real path of a game on shared storage ("primary:Roms/a.md" is /storage/emulated/0/Roms/a.md), which RetroArch needs. */
    private fun rawPath(documentUri: Uri): String? {
        val id = runCatching { DocumentsContract.getDocumentId(documentUri) }.getOrNull() ?: return null
        val colon = id.indexOf(':')
        if (colon <= 0) return null
        val volume = id.substring(0, colon)
        val path = id.substring(colon + 1)
        return if (volume == "primary") "/storage/emulated/0/$path" else "/storage/$volume/$path"
    }
}

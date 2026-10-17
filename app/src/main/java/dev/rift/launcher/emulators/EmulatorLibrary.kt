package dev.rift.launcher.emulators

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
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
)

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
            val found = platforms.flatMap { entry ->
                val def = EmulatorCatalog.platform(entry.platformId) ?: return@flatMap emptyList()
                ensureActive()
                runCatching { scan(app, entry, def) }.getOrDefault(emptyList())
            }
            ensureActive()
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

    private fun walk(context: Context, tree: Uri, parentId: String, depth: Int, def: PlatformDef, out: MutableList<EmulatorGame>) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val folders = ArrayList<String>()
        val files = ArrayList<Triple<String, String, Long>>()
        context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(".")) continue
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    folders += id
                } else {
                    files += Triple(id, name, cursor.getLong(3))
                }
            }
        }
        // A disc image split in a .cue and a .bin is one game: the .cue is listed, not the .bin beside it.
        val cues = files.map { it.second.lowercase() }.filter { it.endsWith(".cue") }.map { it.removeSuffix(".cue") }.toSet()
        for ((id, fileName, modified) in files) {
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0) continue
            val extension = fileName.substring(dot + 1).lowercase()
            if (extension !in def.extensions) continue
            if ((extension == "bin" || extension == "img") && fileName.substring(0, dot).lowercase() in cues) continue
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
            out += EmulatorGame(appIdFor(def.id, uri), def.id, displayName(fileName.substring(0, dot)), uri, modified)
            if (out.size >= MAX_GAMES_PER_SYSTEM) return
        }
        if (depth >= MAX_DEPTH) return
        for (folder in folders) {
            walk(context, tree, folder, depth + 1, def, out)
            if (out.size >= MAX_GAMES_PER_SYSTEM) return
        }
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
            EmulatorGame(o.getString("id"), o.getString("platform"), o.getString("name"), o.getString("uri"), o.optLong("modified"))
        }
    }.getOrDefault(emptyList())

    private fun writeCache(context: Context, games: List<EmulatorGame>) {
        runCatching {
            val array = JSONArray()
            games.forEach {
                array.put(
                    JSONObject().put("id", it.appId).put("platform", it.platformId).put("name", it.name)
                        .put("uri", it.documentUri).put("modified", it.modified),
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
        val uri = Uri.parse(game.documentUri)
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
            LaunchKind.APS3E -> listOf(
                Intent("aenu.intent.action.APS3E").apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    component = ComponentName(pkg, "aenu.aps3e.EmulatorActivity")
                    putExtra("iso_uri", uri.toString())
                },
                base(Intent.ACTION_VIEW).setPackage(pkg),
            )
            LaunchKind.ARMSX3 -> listOf(
                Intent(Intent.ACTION_MAIN).apply {
                    clipData = ClipData.newRawUri("", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or clearTask)
                    component = ComponentName(pkg, "com.armsx2.Main")
                    putExtra("path", uri.toString())
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

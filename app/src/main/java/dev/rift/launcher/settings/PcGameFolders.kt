package dev.rift.launcher.settings

import android.content.Context
import app.gamenative.PrefManager
import app.gamenative.utils.CustomGameScanner
import java.io.File

/**
 * The folders the person pointed Rift at for their Windows games, like "add a games folder" in
 * GameNative or Game Hub. Every subfolder with an .exe in it is a game; the games stay where they are.
 * The folders live in GameNative's `customGamePaths`, which its Custom Games scanner already reads.
 */
object PcGameFolders {
    fun all(): List<String> = PrefManager.customGamePaths.sorted()

    /** Whether Rift may read [path]: always inside its own storage, elsewhere only with all-files access. */
    fun canRead(context: Context, path: String): Boolean =
        CustomGameScanner.hasStoragePermission(context, path) && File(path).canRead()

    fun add(path: String) {
        val clean = File(path).absolutePath.trimEnd('/')
        if (clean.isEmpty()) return
        PrefManager.customGamePaths = PrefManager.customGamePaths + clean
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
    }

    fun remove(path: String) {
        PrefManager.customGamePaths = PrefManager.customGamePaths - path
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
    }

    /** Forgets what earlier scans learned, so the next one looks at every folder again. */
    fun rescan() {
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
    }

    /** How many games [path] holds right now. */
    fun gameCount(path: String): Int = CustomGameScanner.userRootGameFolders().count { File(it).parent == path }
}

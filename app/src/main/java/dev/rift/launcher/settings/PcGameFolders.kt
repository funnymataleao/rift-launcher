package dev.rift.launcher.settings

import android.content.Context
import app.gamenative.utils.CustomGameScanner
import java.io.File
import timber.log.Timber

/**
 * The folders the person pointed Rift at for their Windows games, like "add a games folder" in
 * GameNative or Game Hub. Every subfolder with an .exe in it is a game (a folder with an .exe right in it is one game itself); the games stay where they are.
 * The folders live in GameNative's `customGamePaths`, which its Custom Games scanner already reads.
 */
object PcGameFolders {
    private const val TAG = "RiftPcFolders"

    fun all(): List<String> = CustomGameScanner.userRoots.sorted()

    /** Whether Rift may read [path]: always inside its own storage, elsewhere only with all-files access. */
    fun canRead(context: Context, path: String): Boolean =
        CustomGameScanner.hasStoragePermission(context, path) && File(path).canRead()

    fun add(path: String) {
        val clean = File(path).absolutePath.trimEnd('/')
        if (clean.isEmpty()) return
        CustomGameScanner.userRoots = CustomGameScanner.userRoots + clean
        CustomGameScanner.restoreRemovedUnder(clean)
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
        Timber.tag(TAG).i("added $clean, folders now ${all()}")
    }

    fun remove(path: String) {
        CustomGameScanner.userRoots = CustomGameScanner.userRoots - path
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
        Timber.tag(TAG).i("removed $path, folders now ${all()}")
    }

    /** Forgets what earlier scans learned, so the next one looks at every folder again. */
    fun rescan() {
        CustomGameScanner.rescanUserRoots()
        CustomGameScanner.invalidateCache()
        Timber.tag(TAG).i("rescan: ${CustomGameScanner.userRootGameFolders()}")
    }

    /** How many games [path] holds right now. */
    fun gameCount(path: String): Int = CustomGameScanner.userRootGameFolders().count { it == path || File(it).parent == path }
}

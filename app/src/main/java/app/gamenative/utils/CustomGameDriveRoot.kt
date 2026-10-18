package app.gamenative.utils

import java.io.File

/**
 * Rift: games that refuse to run from the root of a drive.
 *
 * A custom game's folder is the A: drive, and Wine's A:\ is the symlink `dosdevices/a:`, which Wine reports
 * as a reparse point (a link). Azimuth (the Forza Horizon 2 wrapper) keeps its data next to azimuth.exe and
 * stops with "Runtime storage path is a link: A:\". For such games A: is the folder's parent instead, and
 * the game starts from A:\<folder>\, a plain directory. The container's drive list still names the game
 * folder, so everything that reads it (working directory, exe detection) is unchanged.
 */
object CustomGameDriveRoot {
    /** Executables, in the game folder's top level, of games that must not start from a drive root. */
    private val ROOT_SENSITIVE_EXES = setOf("azimuth.exe")

    /** Whether the game in [gameFolderPath] must start from a subfolder of A: rather than from A:\ itself. */
    fun needsSubfolder(gameFolderPath: String?): Boolean {
        if (gameFolderPath.isNullOrBlank()) return false
        val folder = File(gameFolderPath)
        if (folder.parentFile == null) return false
        val names = folder.list() ?: return false
        return names.any { it.lowercase() in ROOT_SENSITIVE_EXES }
    }

    /** What `dosdevices/a:` points at for [gameFolderPath]: its parent when [needsSubfolder], else the folder. */
    fun driveTarget(gameFolderPath: String): String =
        if (needsSubfolder(gameFolderPath)) File(gameFolderPath).parentFile!!.absolutePath else gameFolderPath

    /** The game folder as Wine sees it, with a trailing backslash: `A:\` or `A:\<folder>\`. */
    fun windowsRoot(gameFolderPath: String): String =
        if (needsSubfolder(gameFolderPath)) "A:\\${File(gameFolderPath).name}\\" else "A:\\"
}

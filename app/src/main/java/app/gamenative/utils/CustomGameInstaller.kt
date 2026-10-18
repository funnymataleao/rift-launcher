package app.gamenative.utils

import app.gamenative.data.GameSource
import com.winlator.container.Container
import java.io.File
import timber.log.Timber

/**
 * A folder in a games folder that holds an installer (setup.exe, install.exe, setup_game_1.0.exe, ...) and
 * nothing else to play. Rift shows it as "Install", runs the installer in the container's Wine, and when
 * the installer closes it looks for the game it put on drive C: (or in the folder itself) and points the
 * same entry at it, so from then on it is an ordinary game.
 */
object CustomGameInstaller {
    private const val TAG = "CustomGameInstaller"
    private const val RUN_START = ".rift-install-start"
    private const val INSTALLED = ".rift-installed"

    private val installerWord = Regex("(^|[^a-z])(setup|install|installer|installshield)([^a-z]|$)")

    /** Whether an exe file name (with or without its folders) is an installer, not a game. */
    fun isInstallerExe(path: String): Boolean {
        val name = path.substringAfterLast('/').substringAfterLast('\\').lowercase().removeSuffix(".exe")
        return name.startsWith("setup") || name.startsWith("install") || installerWord.containsMatchIn(name)
    }

    /** Folder whose every .exe is an installer, and which is not installed yet. */
    fun needsInstall(folder: File): Boolean {
        if (File(folder, INSTALLED).exists()) return false
        val exes = CustomGameScanner.findAllValidExeFiles(folder)
        return exes.isNotEmpty() && exes.all { isInstallerExe(it) }
    }

    /** [needsInstall] for the folder of the custom game with this app id; false for any other game. */
    fun needsInstall(appId: String): Boolean {
        if (!appId.startsWith("${GameSource.CUSTOM_GAME.name}_")) return false
        val path = CustomGameScanner.getFolderPathFromAppId(appId) ?: return false
        return needsInstall(File(path))
    }

    private fun gameFolder(container: Container): File? =
        ContainerUtils.getADrivePath(container.drives)?.let(::File)?.takeIf { it.isDirectory }

    /** Called just before a custom game starts: if it is an installer, notes the time so the result can be found. */
    fun markRunStart(container: Container) {
        val folder = gameFolder(container) ?: return
        if (!needsInstall(folder)) return
        runCatching { File(folder, RUN_START).writeText(System.currentTimeMillis().toString()) }
    }

    /**
     * Called when the game session ends. If an installer ran, finds the game it installed and switches the
     * entry to it. Does nothing when no installer ran or nothing new turned up (the installer was closed early).
     */
    fun afterRun(container: Container) {
        val folder = gameFolder(container) ?: return
        val startFile = File(folder, RUN_START)
        val start = runCatching { startFile.readText().trim().toLong() }.getOrNull() ?: return
        startFile.delete()
        val found = findInstalledExe(container, folder, start - 2000) ?: run {
            Timber.tag(TAG).i("No new game found after the installer in ${folder.name}")
            return
        }
        Timber.tag(TAG).i("Installer in ${folder.name} put the game at $found")
        container.executablePath = found
        container.saveData()
        runCatching { File(folder, INSTALLED).writeText(found) }
        CustomGameScanner.invalidateCache()
    }

    private val skipFolders = setOf(
        "common files", "windows nt", "internet explorer", "windows media player", "windows defender",
        "windows mail", "windows photo viewer", "windowspowershell", "uninstall information", "microsoft",
        "microsoft.net", "reference assemblies", "modifiablewindowsapps", "dotnet", "wine", "gamenative-xr",
    )
    private val skipExe = Regex(
        "(unins|uninst|crash|report|redist|vcredist|vc_redist|dxsetup|dotnet|ndp|directx|oalinst|helper|updater|" +
            "cleanup|notification|easyanticheat|eac|battleye|7z|dxwebsetup)",
    )

    private fun findInstalledExe(container: Container, folder: File, since: Long): String? {
        // 1. The installer put the game into the folder it came from (drive A:).
        newGameExe(folder, since)?.let { exe ->
            return exe.relativeTo(folder).path.replace(File.separatorChar, '/')
        }
        // 2. The usual place: a new folder under Program Files (or a games folder) on drive C:.
        val driveC = File(container.rootDir, ".wine/drive_c")
        val roots = listOf("Program Files", "Program Files (x86)", "GOG Games", "Games")
            .map { File(driveC, it) }.filter { it.isDirectory }
        val fresh = roots.flatMap { root ->
            root.listFiles { f -> f.isDirectory && f.name.lowercase() !in skipFolders && f.lastModified() >= since }
                .orEmpty().toList()
        }.sortedByDescending { it.lastModified() }
        for (dir in fresh) {
            val exe = newGameExe(dir, 0L) ?: continue
            return "C:\\" + exe.relativeTo(driveC).path.replace(File.separatorChar, '\\')
        }
        return null
    }

    /** The biggest .exe under [dir] (4 levels) that is not an installer, uninstaller or helper, changed since [since]. */
    private fun newGameExe(dir: File, since: Long): File? {
        var best: File? = null
        fun walk(d: File, depth: Int) {
            for (f in d.listFiles().orEmpty()) {
                if (f.isDirectory && depth < 4 && f.name.lowercase() !in CustomGameScanner.serviceFolders && !java.nio.file.Files.isSymbolicLink(f.toPath())) {
                    walk(f, depth + 1)
                } else if (f.isFile && f.name.endsWith(".exe", ignoreCase = true) && f.lastModified() >= since) {
                    val name = f.name.lowercase()
                    if (isInstallerExe(name) || skipExe.containsMatchIn(name)) continue
                    if (best == null || f.length() > best!!.length()) best = f
                }
            }
        }
        walk(dir, 0)
        return best
    }
}

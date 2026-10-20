package app.gamenative.utils

import app.gamenative.data.GameSource
import app.gamenative.enums.Marker
import com.winlator.container.Container
import java.io.File
import java.nio.file.Files
import timber.log.Timber

object UbisoftConnectStep : PreInstallStep {
    private const val TAG = "UbisoftConnectStep"
    private val INSTALLER_NAMES = listOf("UbisoftConnectInstaller.exe", "UplayInstaller.exe")
    private const val COMMON_REDIST_SUBDIR = "_CommonRedist/UbisoftConnect"
    private val KNOWN_INSTALLER_DIRS = listOf(
        "",
        COMMON_REDIST_SUBDIR,
        "_CommonRedist/Uplay",
        "Support",
        "Support/Ubisoft",
        "__Installer/Uplay",
        "__Installer/UbisoftConnect",
        "Installers",
    )

    override val marker: Marker = Marker.UBISOFT_CONNECT_INSTALLED

    /** Check the actual Wine prefix, not just a marker left behind by a failed setup. */
    fun isInstalled(container: Container): Boolean = runCatching {
        val root = container.rootDir ?: return@runCatching false
        File(
            root,
            ".wine/drive_c/Program Files (x86)/Ubisoft/Ubisoft Game Launcher/UbisoftConnect.exe",
        ).isFile
    }.getOrDefault(false)

    override fun appliesTo(
        container: Container,
        gameSource: GameSource,
        gameDirPath: String,
    ): Boolean {
        if (isInstalled(container)) return false
        // A previous setup may have left its marker even when the EXE was not installed.
        if (MarkerUtils.hasMarker(gameDirPath, Marker.UBISOFT_CONNECT_INSTALLED)) {
            MarkerUtils.removeMarker(gameDirPath, Marker.UBISOFT_CONNECT_INSTALLED)
        }
        // Ubisoft's setup only runs for games which actually ship its redistributable.
        // Otherwise an unrelated game's startup must never be marked Ubisoft-installed.
        return findInstaller(File(gameDirPath)) != null
    }

    override fun buildCommand(
        container: Container,
        appId: String,
        gameSource: GameSource,
        gameDir: File,
        gameDirPath: String,
    ): String? {
        val source = findInstaller(gameDir)
        val installerName = source?.name
        val commonRedistDir = File(gameDir, COMMON_REDIST_SUBDIR)

        if (installerName == null || source == null || !ensureInstallerAtCommonRedist(
                rootInstaller = source,
                commonRedistDir = commonRedistDir,
                commonRedistInstaller = File(commonRedistDir, installerName),
            )) {
            Timber.tag(TAG).i(
                "Ubisoft installer not present at expected _CommonRedist path for game at %s",
                gameDirPath,
            )
            return null
        }

        val wineInstallerPath = "A:\\_CommonRedist\\UbisoftConnect\\$installerName"
        val command = "$wineInstallerPath /S"
        Timber.tag(TAG).i("Using Ubisoft installer (silent): %s", command)

        return command
    }

    /** Only known official redistributable names/locations, never arbitrary executables. */
    internal fun findInstaller(gameDir: File): File? =
        KNOWN_INSTALLER_DIRS.asSequence().flatMap { dir ->
            INSTALLER_NAMES.asSequence().map { name -> File(gameDir, if (dir.isEmpty()) name else "$dir/$name") }
        }.firstOrNull { it.isFile }

    /**
     * IMPORTANT: Ubisoft Connect installer cannot be run from the game root directory.
     * Ensures the Ubisoft Connect installer is present at the expected _CommonRedist path.
     * If it's only present in the game root, creates a symlink there first.
     */
    private fun ensureInstallerAtCommonRedist(
        rootInstaller: File,
        commonRedistDir: File,
        commonRedistInstaller: File,
    ): Boolean {
        if (commonRedistInstaller.isFile) return true
        if (!rootInstaller.isFile) return false
        try {
            check(commonRedistDir.isDirectory || commonRedistDir.mkdirs())
            Files.createSymbolicLink(commonRedistInstaller.toPath(), rootInstaller.toPath())
            return commonRedistInstaller.isFile
        } catch (t: Exception) {
            Timber.tag(TAG).w(t, "Ubisoft installer symlink unavailable; using a local copy")
            // Some Android-mounted game libraries do not support symlinks.
            return runCatching {
                rootInstaller.copyTo(commonRedistInstaller, overwrite = false)
                commonRedistInstaller.isFile
            }.getOrElse {
                Timber.tag(TAG).e(it, "Unable to prepare Ubisoft installer")
                false
            }
        }
    }
}

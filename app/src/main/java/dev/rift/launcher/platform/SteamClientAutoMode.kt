package dev.rift.launcher.platform

import android.content.Context
import app.gamenative.BuildConfig
import app.gamenative.service.SteamService
import app.gamenative.service.ea.EaLaunchSupport
import app.gamenative.service.rockstar.RockstarLaunchSupport
import com.winlator.container.Container
import com.winlator.xenvironment.ImageFs
import java.io.File
import timber.log.Timber

/**
 * Clean build: games run on the emulated Steam API by default. A game that needs Valve's
 * own client (EA app, Rockstar Launcher, or a SteamStub error under the emulator)
 * gets "Launch Steam client" switched on before launch, so nobody has to toggle it.
 * Denuvo alone is not a reason: Hogwarts Legacy runs under the emulated API on
 * ARM64EC + FEX, and crashes on the x86_64 Wine the Steam client needs.
 */
object SteamClientAutoMode {
    private const val TAG = "SteamClientAutoMode"

    /** Left in the game folder when the emulated launch showed a Steam DRM error. */
    private const val MARKER = ".rift_needs_steam_client"

    /** Title of the SteamStub dialog ("Application load error 3:0000065432"). */
    const val STEAM_ERROR_WINDOW = "Steam Error"

    /** Switches the container to the Steam client when the game needs it. Call before launch dependencies. */
    fun applyBeforeLaunch(context: Context, container: Container, steamAppId: Int) {
        if (!BuildConfig.CLEAN_BUILD || steamAppId <= 0) return
        if (!container.isLaunchRealSteam) {
            val reason = reasonFor(steamAppId) ?: return
            Timber.tag(TAG).i("Using the Steam client for $steamAppId: $reason")
            container.setLaunchRealSteam(true)
        }
        useX64Wine(context, container, steamAppId)
        container.saveData()
    }

    /**
     * Valve's steamclient64.dll cannot reach the real Steam client from an ARM64EC
     * game process (the DLL briefly loads then unloads and SteamAPI_Init fails).
     * Prefer an installed x86_64 build matching the current Proton major version.
     * Never synthesize an identifier that the device cannot actually launch.
     */
    private fun useX64Wine(context: Context, container: Container, steamAppId: Int) {
        val wine = container.wineVersion ?: return
        if (!wine.contains("-arm64ec")) return
        val protonDir = ImageFs.getSharedProtonDir(context)
        val installed = protonDir.listFiles()
            ?.filter { it.isDirectory && File(it, "bin/wine").isFile }
            ?.map { it.name }
            .orEmpty()
        val x64 = selectX64Wine(wine, installed)
        if (x64 == null) {
            Timber.tag(TAG).w("No installed x86_64 Proton for Steam client game $steamAppId; staying on $wine")
            return
        }
        Timber.tag(TAG).i("Steam client game $steamAppId: Wine $wine -> $x64 (installed)")
        container.setWineVersion(x64)
    }

    /** Pure decision logic to keep the architecture fallback testable. */
    internal fun selectX64Wine(current: String, installed: Collection<String>): String? {
        if (!current.contains("-arm64ec")) return null
        val x64 = installed.filter { it.contains("-x86_64") }
        val exact = current.replace("-arm64ec", "-x86_64")
        if (exact in x64) return exact
        val major = Regex("^proton-([0-9]+)").find(current)?.groupValues?.getOrNull(1)
        val sameMajor = x64.filter { major != null && it.startsWith("proton-$major.") }
        if (sameMajor.isNotEmpty()) return sameMajor.sortedDescending().first()
        return x64.firstOrNull { it == "proton-9.0-x86_64" } ?: x64.sortedDescending().firstOrNull()
    }

    /** Remembers that the emulated launch failed on Steam DRM, so the next launch uses the Steam client. */
    fun onSteamDrmError(container: Container, steamAppId: Int): Boolean {
        if (!BuildConfig.CLEAN_BUILD || steamAppId <= 0 || container.isLaunchRealSteam) return false
        val gameDir = File(SteamService.getAppDirPath(steamAppId))
        runCatching { File(gameDir, MARKER).writeText("Steam Error under the emulated Steam API\n") }
            .onFailure { Timber.tag(TAG).w(it, "Could not mark $steamAppId") }
        container.setLaunchRealSteam(true)
        container.saveData()
        Timber.tag(TAG).i("Steam DRM error for $steamAppId; next launch uses the Steam client")
        return true
    }

    private fun reasonFor(steamAppId: Int): String? {
        val gameDir = File(SteamService.getAppDirPath(steamAppId))
        if (File(gameDir, MARKER).exists()) return "earlier Steam DRM error"
        if (runCatching { EaLaunchSupport.isEaTitle(steamAppId, gameDir) }.getOrDefault(false)) return "EA app"
        if (runCatching { RockstarLaunchSupport.isRockstarTitle(gameDir) }.getOrDefault(false)) return "Rockstar Launcher"
        return null
    }
}

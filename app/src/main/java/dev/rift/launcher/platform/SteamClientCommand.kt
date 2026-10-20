package dev.rift.launcher.platform

import com.winlator.xenvironment.components.GuestProgramLauncherComponent
import java.io.File
import timber.log.Timber

/**
 * Sends a command line to Valve's Steam client running in the container. The launcher splits commands on
 * spaces and does not keep quotes, so "C:\Program Files (x86)\Steam\steam.exe" is written into a batch
 * file in C:\windows\temp and run through cmd, whose own path has no spaces.
 */
internal object SteamClientCommand {
    private const val TAG = "SteamClientCommand"
    private const val STEAM_EXE = "C:\\Program Files (x86)\\Steam\\steam.exe"

    /** Asks the running client to launch [appId], as a second `steam.exe -applaunch` would on Windows. */
    fun applaunch(launcher: GuestProgramLauncherComponent, winePrefix: File, appId: Int): String {
        require(appId > 0)
        return run(launcher, winePrefix, "rift_steam_applaunch.bat", "-applaunch $appId")
    }

    /**
     * Asks the client to exit on its own, so it finishes the cloud sync of the session. Killing it with
     * the container leaves "incomplete sync after previous run", and the next start stops on a
     * Steam Controller Configs (241100) conflict dialog instead of launching the game.
     */
    fun shutdown(launcher: GuestProgramLauncherComponent, winePrefix: File): String =
        run(launcher, winePrefix, "rift_steam_shutdown.bat", "-shutdown")

    private fun run(launcher: GuestProgramLauncherComponent, winePrefix: File, name: String, args: String): String {
        val temp = File(winePrefix, "drive_c/windows/temp").apply { mkdirs() }
        File(temp, name).writeText("@echo off\r\n\"$STEAM_EXE\" $args\r\n")
        val output = launcher.execShellCommand("wine cmd /c C:\\windows\\temp\\$name")
        Timber.tag(TAG).i("steam.exe %s -> %s", args, output.take(200))
        return output
    }
}

package dev.rift.launcher.platform

import android.content.Context
import app.gamenative.BuildConfig
import app.gamenative.service.SteamService
import app.gamenative.service.ea.EaLaunchSupport
import app.gamenative.service.rockstar.RockstarLaunchSupport
import app.gamenative.utils.Net
import com.winlator.container.Container
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber

/**
 * Clean build: games run on the emulated Steam API by default. A game that needs Valve's
 * own client (Denuvo, EA app, Rockstar Launcher, or a SteamStub error under the emulator)
 * gets "Launch Steam client" switched on before launch, so nobody has to toggle it.
 */
object SteamClientAutoMode {
    private const val TAG = "SteamClientAutoMode"

    /** Left in the game folder when the emulated launch showed a Steam DRM error. */
    private const val MARKER = ".rift_needs_steam_client"

    /** Title of the SteamStub dialog ("Application load error 3:0000065432"). */
    const val STEAM_ERROR_WINDOW = "Steam Error"

    private val storeClient by lazy {
        Net.http.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    /** Switches the container to the Steam client when the game needs it. Call before launch dependencies. */
    fun applyBeforeLaunch(context: Context, container: Container, steamAppId: Int) {
        if (!BuildConfig.CLEAN_BUILD || steamAppId <= 0) return
        if (!container.isLaunchRealSteam) {
            val reason = reasonFor(context, steamAppId) ?: return
            Timber.tag(TAG).i("Using the Steam client for $steamAppId: $reason")
            container.setLaunchRealSteam(true)
        }
        useX64Wine(container, steamAppId)
        container.saveData()
    }

    /**
     * Valve's steamclient64.dll cannot reach the Steam client from an ARM64EC game process (it loads and
     * unloads at once, and the game reports "Steam Initialization Failed"); under x86_64 Wine it connects.
     */
    private fun useX64Wine(container: Container, steamAppId: Int) {
        val wine = container.wineVersion ?: return
        if (!wine.endsWith("-arm64ec")) return
        val x64 = wine.removeSuffix("-arm64ec") + "-x86_64"
        Timber.tag(TAG).i("Steam client game $steamAppId: Wine $wine -> $x64")
        container.setWineVersion(x64)
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

    private fun reasonFor(context: Context, steamAppId: Int): String? {
        val gameDir = File(SteamService.getAppDirPath(steamAppId))
        if (File(gameDir, MARKER).exists()) return "earlier Steam DRM error"
        if (runCatching { EaLaunchSupport.isEaTitle(steamAppId, gameDir) }.getOrDefault(false)) return "EA app"
        if (runCatching { RockstarLaunchSupport.isRockstarTitle(gameDir) }.getOrDefault(false)) return "Rockstar Launcher"
        val drm = drmNotice(context, steamAppId)
        if (drm.contains("denuvo", ignoreCase = true)) return "Denuvo ($drm)"
        return null
    }

    /** The store page's third-party DRM line, cached per app. Empty when unknown or offline. */
    private fun drmNotice(context: Context, steamAppId: Int): String {
        val cache = File(context.filesDir, "rift_drm_notice/$steamAppId.txt")
        if (cache.isFile) return cache.readText()
        return try {
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/appdetails?appids=$steamAppId&cc=us&l=english")
                .build()
            storeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                val data = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject(steamAppId.toString())?.optJSONObject("data") ?: return ""
                val notice = data.optString("drm_notice")
                cache.parentFile?.mkdirs()
                cache.writeText(notice)
                notice
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Store DRM lookup failed for $steamAppId")
            ""
        }
    }
}

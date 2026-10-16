package dev.rift.launcher.home

import android.util.LruCache
import app.gamenative.enums.Language
import app.gamenative.service.SteamService

/**
 * Where a game's transparent logo lives.
 *
 * Newer Steam games keep their library logo under a hashed path, so the plain logo.png URL misses
 * them. The path comes from the app info GameNative already stores (library_assets_full), with the
 * plain URL as a fallback for apps without it.
 */
object GameLogos {
    private const val STEAM_ASSETS = "https://shared.steamstatic.com/store_item_assets/steam/apps"
    private val cache = LruCache<Int, String>(512)

    /** Reads GameNative's app database, so call it off the main thread. */
    fun steamLogoUrl(appId: Int): String {
        cache.get(appId)?.let { return it }
        val logo = runCatching { SteamService.getAppInfoOf(appId)?.libraryAssets?.libraryLogo }.getOrNull()
        val path = logo?.let {
            it.image2x[Language.english] ?: it.image[Language.english] ?: it.image2x.values.firstOrNull() ?: it.image.values.firstOrNull()
        }
        val url = if (path.isNullOrEmpty()) "$STEAM_ASSETS/$appId/logo.png" else "$STEAM_ASSETS/$appId/$path"
        cache.put(appId, url)
        return url
    }
}

package dev.rift.launcher.art

import android.content.Context

/** What a game's [dev.rift.launcher.home.GameBackdrop] shows: its hero, its logo, and its Steam id for Steam's logo. */
data class LaunchArt(val heroUrl: String, val logoUrl: String, val steamAppId: Int) {
    companion object {
        /** The art picked (or found) in Rift for [appId] first, then the store's [storeHero] and [storeLogo]. */
        fun of(context: Context, appId: String, storeHero: String, storeLogo: String = ""): LaunchArt {
            if (appId.isEmpty()) return LaunchArt(storeHero, storeLogo, 0)
            val steamAppId = if (appId.startsWith("STEAM_")) {
                appId.removePrefix("STEAM_").substringBefore("(").toIntOrNull() ?: 0
            } else {
                0
            }
            return LaunchArt(
                heroUrl = GameArtStore.shown(context, appId, ArtKind.Hero).ifEmpty { storeHero },
                logoUrl = GameArtStore.shown(context, appId, ArtKind.Logo).ifEmpty { storeLogo },
                steamAppId = steamAppId,
            )
        }
    }
}

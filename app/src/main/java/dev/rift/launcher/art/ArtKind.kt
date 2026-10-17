package dev.rift.launcher.art

import androidx.annotation.StringRes
import app.gamenative.R

/**
 * The picture types a game can be given, in the order of the picker's tabs. [key] is what the store
 * saves, [endpoint] is SteamGridDB's name for the type, [ratio] is width / height of a tile in the picker
 * and [dimensions] and [mimes] narrow SteamGridDB's results to what Rift actually shows.
 */
enum class ArtKind(
    val key: String,
    @get:StringRes val titleRes: Int,
    val endpoint: String,
    val ratio: Float,
    val dimensions: String? = null,
    val mimes: String = "image/png,image/jpeg,image/webp",
) {
    /** Tall cover: the row on the home screen and the library grid. */
    Capsule("capsule", R.string.rift_art_capsule, "grids", 2f / 3f, "600x900,342x482,660x930"),

    /** Wide cover: the first, wide tile of the home row. */
    Wide("wide", R.string.rift_art_wide, "grids", 460f / 215f, "460x215,920x430"),

    /** Banner behind the home screen and the game page. */
    Hero("hero", R.string.rift_art_hero, "heroes", 1920f / 620f, "1920x620,3840x1240,1600x650"),

    /** Transparent title logo shown over the banner. */
    Logo("logo", R.string.rift_art_logo, "logos", 16f / 9f, null, "image/png,image/webp"),

    Icon("icon", R.string.rift_art_icon, "icons", 1f, null, "image/png,image/webp,image/jpeg"),
}

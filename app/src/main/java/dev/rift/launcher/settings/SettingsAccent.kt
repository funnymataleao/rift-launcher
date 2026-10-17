package dev.rift.launcher.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** An icon as macOS System Settings draws it: a saturated symbol on a light disc of the same hue. */
internal class IconColors(val symbol: Color, val disc: Color)

private fun iconColorsOfHue(hue: Float) = IconColors(
    symbol = Color(ColorUtils.HSLToColor(floatArrayOf(hue, 0.62f, 0.56f))),
    disc = Color(ColorUtils.HSLToColor(floatArrayOf(hue, 0.80f, 0.88f))),
)

/** The icon colours for any base colour, such as the error red of a destructive row. */
internal fun iconColorsOf(base: Color): IconColors {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    return iconColorsOfHue(hsl[0])
}

/**
 * Clean pastel hues for icons: blue, sky, mint, green, lemon, peach, rose and lilac. Each icon takes one
 * hue, a saturated symbol on a light disc of the same hue, kept off the lemon interface accent.
 */
internal enum class SettingsAccent(hue: Float) {
    Blue(215f),
    Sky(195f),
    Mint(165f),
    Green(135f),
    Lemon(50f),
    Peach(22f),
    Rose(340f),
    Lilac(265f);

    val icon: IconColors = iconColorsOfHue(hue)

    /** A soft mid tone of the hue, for faint tints behind a whole card. */
    val tint: Color = Color(ColorUtils.HSLToColor(floatArrayOf(hue, 0.70f, 0.70f)))
}

/** The accent of the section being shown; rows take their icon colour from it. */
internal val LocalSettingsAccent = compositionLocalOf { SettingsAccent.Blue }

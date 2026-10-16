// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Values from design/DESIGN_TOKENS.md ("Цвет"). Rift has one dark scheme for the whole app.
internal val RiftColorScheme = darkColorScheme(
    background = Color(0xFF06080C),
    onBackground = Color(0xFFF2F6FB),
    surface = Color(0xFF10161F),
    onSurface = Color(0xFFF2F6FB),
    onSurfaceVariant = Color(0xFF93A0B3),
    surfaceContainer = Color(0xFF18202C),
    surfaceContainerHigh = Color(0xFF232D3D),
    outline = Color(0xFF5D6A7D),
    outlineVariant = Color(0x17FFFFFF),
    primary = Color(0xFF36F2FF),
    onPrimary = Color(0xFF04121A),
    secondary = Color(0xFF7DFFA6),
    onSecondary = Color(0xFF04121A),
    tertiary = Color(0xFFFFC94D),
    error = Color(0xFFFF6B6B),
)

/** Tokens that have no slot in Material's ColorScheme. */
object RiftColors {
    val success = Color(0xFF4BE38A)

    /** Text on [RiftBrush.accent]. */
    val onAccent = Color(0xFF04121A)

    /** Glow when the cover art gives no dominant color. */
    val fallbackGlow = Color(0xFF36F2FF)
}

object RiftBrush {
    /** Play button, active segments, sliders, "new" badges. */
    val accent = Brush.horizontalGradient(listOf(Color(0xFF7DFFA6), Color(0xFF36F2FF)))
}

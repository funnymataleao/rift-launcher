package dev.rift.launcher.settings

import androidx.compose.ui.graphics.Color
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors

/** A settings icon: the symbol and the disc behind it. */
internal class IconColors(val symbol: Color, val disc: Color)

/**
 * Calm icons: a light symbol on a faint disc. On a plate filled with the accent colour ([focused]) both
 * turn dark, so the accent shows only on the plate that is selected.
 */
internal fun iconColors(focused: Boolean, danger: Boolean = false): IconColors {
    if (focused) {
        val dark = RiftAccent.current.onAccent
        return IconColors(symbol = dark, disc = dark.copy(alpha = 0.12f))
    }
    return IconColors(
        symbol = if (danger) RiftColors.Error else RiftColors.OnSurface.copy(alpha = 0.92f),
        disc = Color.White.copy(alpha = 0.09f),
    )
}

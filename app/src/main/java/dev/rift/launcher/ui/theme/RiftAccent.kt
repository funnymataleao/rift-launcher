package dev.rift.launcher.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import app.gamenative.R

/**
 * The colour of everything that was lemon: the user picks one solid colour, a gradient or a colour of
 * their own on the Appearance page. [RiftColors.Primary] and the Material colour scheme read it, so the
 * whole app follows at once.
 */
object RiftAccent {
    /** One pick. One stop is a solid colour; two or more are a gradient. [nameRes] is 0 for a custom colour. */
    class Choice(val id: String, val nameRes: Int, val stops: List<Color>) {
        val isGradient: Boolean get() = stops.size > 1

        /** The one colour used where a gradient cannot go: text, thin lines, switches. */
        val main: Color = if (stops.size == 1) stops[0] else stops.reduce { a, b -> Color(
            red = (a.red + b.red) / 2f, green = (a.green + b.green) / 2f, blue = (a.blue + b.blue) / 2f,
        ) }

        /** Text and icons on a plate filled with this accent: dark, unless the colour itself is dark. */
        val onAccent: Color = if (main.luminance() > 0.35f) Color(0xFF0A0A0A) else Color.White

        val brush: Brush = if (stops.size == 1) SolidColor(stops[0]) else Brush.linearGradient(stops)
    }

    val Lemon = Choice("lemon", R.string.rift_accent_lemon, listOf(Color(0xFFFFF44F)))

    val solids = listOf(
        Lemon,
        Choice("white", R.string.rift_accent_white, listOf(Color(0xFFF2F2EC))),
        Choice("sky", R.string.rift_accent_sky, listOf(Color(0xFF7CC8FF))),
        Choice("mint", R.string.rift_accent_mint, listOf(Color(0xFF6EF0B8))),
        Choice("lime", R.string.rift_accent_lime, listOf(Color(0xFFB6F25E))),
        Choice("peach", R.string.rift_accent_peach, listOf(Color(0xFFFFB38A))),
        Choice("coral", R.string.rift_accent_coral, listOf(Color(0xFFFF8A80))),
        Choice("aqua", R.string.rift_accent_aqua, listOf(Color(0xFF5CE6E6))),
    )

    // Each gradient moves between clearly different hues and lightness, never pink or violet.
    val gradients = listOf(
        Choice("pearl", R.string.rift_accent_pearl, listOf(Color(0xFFF4FAFA), Color(0xFFB7F0DC), Color(0xFF8CCBFF))),
        Choice("lemonlime", R.string.rift_accent_lemonlime, listOf(Color(0xFFFFF44F), Color(0xFFB6F25E))),
        Choice("tropic", R.string.rift_accent_tropic, listOf(Color(0xFFFFF44F), Color(0xFF4EE58A), Color(0xFF22C7E0))),
        Choice("aurora", R.string.rift_accent_aurora, listOf(Color(0xFF9CFF7A), Color(0xFF2EE6B6), Color(0xFF2EA8FF))),
        Choice("sunset", R.string.rift_accent_sunset, listOf(Color(0xFFFFE44D), Color(0xFFFF9A3C), Color(0xFFFF5C3A))),
        Choice("ocean", R.string.rift_accent_ocean, listOf(Color(0xFF6CF2F2), Color(0xFF3DB4FF))),
    )

    private const val PREFS = "rift_prefs"
    private const val KEY = "accent"
    private const val CUSTOM = "custom:"

    private var loaded = false

    /** Read in composition, so a change recomposes everything that draws with it. */
    var current by mutableStateOf(Lemon)
        private set

    fun custom(color: Color): Choice {
        val rgb = (color.red * 255f + 0.5f).toInt().shl(16) or
            (color.green * 255f + 0.5f).toInt().shl(8) or (color.blue * 255f + 0.5f).toInt()
        return Choice(CUSTOM + "%06X".format(rgb), 0, listOf(Color(0xFF000000.toInt() or rgb)))
    }

    /** Loads the saved pick once. Safe to call from any composition. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        current = find(id) ?: Lemon
    }

    fun select(context: Context, choice: Choice) {
        loaded = true
        current = choice
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.id).apply()
    }

    private fun find(id: String): Choice? {
        if (id.startsWith(CUSTOM)) {
            val rgb = id.removePrefix(CUSTOM).toIntOrNull(16) ?: return null
            return Choice(id, 0, listOf(Color(0xFF000000.toInt() or rgb)))
        }
        return (solids + gradients).firstOrNull { it.id == id }
    }
}

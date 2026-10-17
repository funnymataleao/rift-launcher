package dev.rift.launcher.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import app.gamenative.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

        /** Fills a shape of any size: a gradient is stretched over the whole shape, so a wide button shows all of it. */
        val brush: Brush = if (stops.size == 1) SolidColor(stops[0]) else FitGradient(stops)
    }

    /**
     * A linear gradient that always runs across the whole shape. The plain diagonal gradient of a wide, low
     * plate is almost flat along the short side and its ends fall outside the corners, so on a button only
     * the middle colours showed. This one tilts between 20 and 45 degrees (a square gets the usual 45) and
     * is sized so the first stop sits in one corner and the last in the opposite one, at any width.
     */
    private class FitGradient(private val colors: List<Color>) : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val w = size.width
            val h = size.height
            val angle = atan2(h, w).coerceIn(MIN_TILT, MAX_TILT)
            val dx = cos(angle)
            val dy = sin(angle)
            val half = (w * dx + h * dy) / 2f
            val cx = w / 2f
            val cy = h / 2f
            return LinearGradientShader(
                from = Offset(cx - dx * half, cy - dy * half),
                to = Offset(cx + dx * half, cy + dy * half),
                colors = colors,
            )
        }

        override fun equals(other: Any?) = other is FitGradient && other.colors == colors
        override fun hashCode() = colors.hashCode()
    }

    private const val MIN_TILT = 0.35f // about 20 degrees
    private const val MAX_TILT = 0.785f // 45 degrees

    val Lemon = Choice("lemon", R.string.rift_accent_lemon, listOf(Color(0xFFFFF44F)))

    // Nine solid colours and nine gradients. Every one is bright enough for dark text (checked by luminance),
    // none is pink or violet. Each gradient moves between neighbouring hues so the blend stays clean.
    val solids = listOf(
        Lemon,
        Choice("white", R.string.rift_accent_white, listOf(Color(0xFFF2F2EC))),
        Choice("sky", R.string.rift_accent_sky, listOf(Color(0xFF7CC8FF))),
        Choice("mint", R.string.rift_accent_mint, listOf(Color(0xFF6EF0B8))),
        Choice("lime", R.string.rift_accent_lime, listOf(Color(0xFFB6F25E))),
        Choice("peach", R.string.rift_accent_peach, listOf(Color(0xFFFFB38A))),
        Choice("coral", R.string.rift_accent_coral, listOf(Color(0xFFFF8A80))),
        Choice("aqua", R.string.rift_accent_aqua, listOf(Color(0xFF5CE6E6))),
        Choice("amber", R.string.rift_accent_amber, listOf(Color(0xFFFFC24D))),
    )

    val gradients = listOf(
        Choice("pearl", R.string.rift_accent_pearl, listOf(Color(0xFFF4FAFA), Color(0xFFB7F0DC), Color(0xFF8CCBFF))),
        Choice("lemonlime", R.string.rift_accent_lemonlime, listOf(Color(0xFFFFF44F), Color(0xFFB6F25E))),
        Choice("tropic", R.string.rift_accent_tropic, listOf(Color(0xFFFFF44F), Color(0xFF4EE58A), Color(0xFF2FD3E8))),
        Choice("aurora", R.string.rift_accent_aurora, listOf(Color(0xFF9CFF7A), Color(0xFF2EE6B6), Color(0xFF3DB0FF))),
        Choice("lagoon", R.string.rift_accent_lagoon, listOf(Color(0xFFB6F25E), Color(0xFF6FD8FF))),
        Choice("glacier", R.string.rift_accent_glacier, listOf(Color(0xFFA8E9FF), Color(0xFF8DF2D2), Color(0xFFD4FF8F))),
        Choice("ocean", R.string.rift_accent_ocean, listOf(Color(0xFF6CF2F2), Color(0xFF3DB4FF))),
        Choice("sunset", R.string.rift_accent_sunset, listOf(Color(0xFFFFE44D), Color(0xFFFF9A3C), Color(0xFFFF7A45))),
        Choice("dawn", R.string.rift_accent_dawn, listOf(Color(0xFFFFE08A), Color(0xFFFFB38A), Color(0xFFFF9E7A))),
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

package dev.rift.launcher.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Rift design tokens (see the project's DESIGN_TOKENS.md).
 *
 * Kept in its own package so GameNative's ui/ tree stays untouched and upstream merges stay clean.
 */
/** Black and lemon (Denis 2026-10-16): pure black for OLED, one lemon accent, black text on lemon. */
object RiftColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceContainer = Color(0xFF171717)
    val SurfaceContainerHigh = Color(0xFF242424)
    val OnSurface = Color(0xFFF5F5F0)
    val OnSurfaceVariant = Color(0xFFA3A39A)
    val Outline = Color(0xFF6B6B63)
    val OutlineVariant = Color(0x17FFFFFF)
    val Primary = Color(0xFFFFF44F)
    val OnPrimary = Color(0xFF000000)
    val Secondary = Color(0xFFF5E50A)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB84D)
    val Success = Color(0xFF4BE38A)

    /** Fallback glow when a cover's own colour is not known yet. */
    val GlowFallback = Primary
}

object RiftBrush {
    val accent = Brush.horizontalGradient(listOf(RiftColors.Primary, RiftColors.Secondary))
}

object RiftShapes {
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(19.dp)
    val sheet = RoundedCornerShape(26.dp)
    val full = RoundedCornerShape(50)
}

object RiftSizes {
    /** Every Rift button is a pill (or a circle when square) of this height. */
    val button = 52.dp
    val leftPanel = 336.dp
}

object RiftMotion {
    /** Cover lift and scale on focus, Play button shape change. */
    fun <T> focusSpring() = spring<T>(dampingRatio = 0.6f, stiffness = 400f)

    /** Side panels and larger surfaces. */
    fun <T> panelSpring() = spring<T>(dampingRatio = 0.8f, stiffness = 300f)

    fun <T> noBounce() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    const val HERO_CROSSFADE_MS = 600
}

private val RiftColorScheme = darkColorScheme(
    primary = RiftColors.Primary,
    onPrimary = RiftColors.OnPrimary,
    secondary = RiftColors.Secondary,
    onSecondary = RiftColors.OnPrimary,
    tertiary = RiftColors.Warning,
    error = RiftColors.Error,
    background = RiftColors.Background,
    onBackground = RiftColors.OnSurface,
    surface = RiftColors.Surface,
    onSurface = RiftColors.OnSurface,
    surfaceVariant = RiftColors.SurfaceContainer,
    onSurfaceVariant = RiftColors.OnSurfaceVariant,
    surfaceContainer = RiftColors.SurfaceContainer,
    surfaceContainerHigh = RiftColors.SurfaceContainerHigh,
    outline = RiftColors.Outline,
    outlineVariant = RiftColors.OutlineVariant,
)

@Composable
fun RiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RiftColorScheme,
        shapes = Shapes(
            small = RiftShapes.control,
            medium = RiftShapes.card,
            large = RiftShapes.sheet,
        ),
        content = content,
    )
}

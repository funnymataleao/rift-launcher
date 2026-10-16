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
object RiftColors {
    val Background = Color(0xFF06080C)
    val Surface = Color(0xFF10161F)
    val SurfaceContainer = Color(0xFF18202C)
    val SurfaceContainerHigh = Color(0xFF232D3D)
    val OnSurface = Color(0xFFF2F6FB)
    val OnSurfaceVariant = Color(0xFF93A0B3)
    val Outline = Color(0xFF5D6A7D)
    val OutlineVariant = Color(0x17FFFFFF)
    val Primary = Color(0xFF36F2FF)
    val OnPrimary = Color(0xFF04121A)
    val Secondary = Color(0xFF7DFFA6)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFC94D)
    val Success = Color(0xFF4BE38A)

    /** Fallback glow when a cover's own colour is not known yet. */
    val GlowFallback = Primary
}

object RiftBrush {
    val accent = Brush.horizontalGradient(listOf(RiftColors.Secondary, RiftColors.Primary))
}

object RiftShapes {
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(19.dp)
    val sheet = RoundedCornerShape(26.dp)
    val full = RoundedCornerShape(50)
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

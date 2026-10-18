package dev.rift.launcher.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Rift design tokens (see the project's DESIGN_TOKENS.md).
 *
 * Kept in its own package so GameNative's ui/ tree stays untouched and upstream merges stay clean.
 */
/**
 * Black first, lemon as a rare accent (Denis 2026-10-16): pure black for OLED, white and grey text,
 * lemon only for focus details and the active tab mark, never for large fills.
 */
object RiftColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E0E)
    val SurfaceContainer = Color(0xFF171717)
    val SurfaceContainerHigh = Color(0xFF242424)
    val OnSurface = Color(0xFFF5F5F0)
    val OnSurfaceVariant = Color(0xFFA3A39A)
    val Outline = Color(0xFF6B6B63)
    val OutlineVariant = Color(0x17FFFFFF)
    /** The user's accent (lemon until they pick another), see [RiftAccent]. */
    val Primary: Color get() = RiftAccent.current.main
    val OnPrimary: Color get() = RiftAccent.current.onAccent
    val Secondary: Color get() = RiftAccent.current.main
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB84D)
    val Success = Color(0xFF4BE38A)

    /** Fallback glow when a cover's own colour is not known yet. */
    val GlowFallback: Color get() = Primary
}

object RiftBrush {
    /** Primary action fill (Play): white, so lemon stays an accent. */
    val action = Brush.linearGradient(listOf(Color.White, Color.White))
}

object RiftShapes {
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(19.dp)
    val sheet = RoundedCornerShape(26.dp)
    val full = RoundedCornerShape(50)

    /**
     * Denis 2026-10-16: nothing square anywhere (side panels themselves aside). One rounded family
     * for every Material component, in Rift's own screens and GameNative's alike.
     */
    val material = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = control,
        medium = card,
        large = sheet,
        extraLarge = RoundedCornerShape(32.dp),
    )
}

object RiftSizes {
    /** Every Rift button is a pill (or a circle when square) of this height. */
    val button = 52.dp
    val leftPanel = 336.dp
}

object RiftMotion {
    /** Cover lift and scale on focus, Play button shape change: quick and without a bounce. */
    fun <T> focusSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)

    /** Side panels and larger surfaces. */
    fun <T> panelSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

    fun <T> noBounce() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    const val HERO_CROSSFADE_MS = 600
}

private fun riftColorScheme() = darkColorScheme(
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
    surfaceTint = Color.Transparent,
    // Every container role set explicitly: Material's dark defaults are purple (GameNative's panels
    // use primaryContainer for focused rows).
    primaryContainer = Color.White.copy(alpha = 0.14f),
    onPrimaryContainer = RiftColors.OnSurface,
    secondaryContainer = Color.White.copy(alpha = 0.10f),
    onSecondaryContainer = RiftColors.OnSurface,
    tertiaryContainer = Color.White.copy(alpha = 0.10f),
    onTertiaryContainer = RiftColors.OnSurface,
    onTertiary = RiftColors.OnPrimary,
    inversePrimary = RiftColors.Primary,
    inverseSurface = RiftColors.OnSurface,
    inverseOnSurface = RiftColors.Background,
    surfaceBright = RiftColors.SurfaceContainerHigh,
    surfaceDim = RiftColors.Background,
    surfaceContainerLowest = RiftColors.Background,
    surfaceContainerLow = RiftColors.Surface,
    surfaceContainerHighest = RiftColors.SurfaceContainerHigh,
)

/** Side panels: translucent black over a blurred home screen, like frosted glass. */
private fun riftGlassColorScheme() = riftColorScheme().copy(
    surface = Color.Black.copy(alpha = 0.7f),
    surfaceContainer = Color.Black.copy(alpha = 0.7f),
    // Cards inside the panels stay see-through too, just a little lighter than the glass.
    surfaceVariant = Color.White.copy(alpha = 0.06f),
    surfaceContainerLow = Color.White.copy(alpha = 0.04f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.07f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.10f),
    surfaceTint = Color.Transparent,
    scrim = Color.Black.copy(alpha = 0.3f),
)

@Composable
fun RiftTheme(content: @Composable () -> Unit) = RiftMaterialTheme(glass = false, content)

/** For GameNative's side panels shown over Rift; pair it with a blur on what is behind them. */
@Composable
fun RiftGlassTheme(content: @Composable () -> Unit) = RiftMaterialTheme(glass = true, content)

/**
 * Material 3 Expressive (Denis 2026-10-16): expressive motion scheme (springs) and shapes, in Rift's
 * black palette. material3 1.4.0 is the newest the fork's AGP 8.8 can build; there these APIs are
 * still marked experimental.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RiftMaterialTheme(glass: Boolean, content: @Composable () -> Unit) {
    RiftAccent.ensureLoaded(LocalContext.current)
    val accent = RiftAccent.current
    val colorScheme = remember(accent, glass) { if (glass) riftGlassColorScheme() else riftColorScheme() }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = RiftShapes.material,
    ) {
        RiftFocusMargin(content)
    }
}

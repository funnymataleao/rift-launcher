package dev.rift.launcher.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FrameWidth = 1.dp
private const val FRAME_SWEEP_MS = 1400

/** The animated gradient frame on the focused cover is switched off for now (Denis); set to true to bring it back. */
private const val COVER_FRAME_ENABLED = false
private const val SHEEN_SWEEP_MS = 900
private const val MAX_TILT_DEGREES = 9f

/** How far a focused cover grows on each side; less than the gap between covers. */
private val FocusGrowth = 7.dp
private const val REFLECTION_SHARE = 0.26f

/** Neutral frame until the cover's own colours are known. */
private val FallbackPalette = listOf(Color.White, Color(0xFF9E9E9E))

/**
 * A 2:3 cover with Rift's focus treatment (Denis 2026-10-16): the cover lifts, a frame in the
 * cover's own two colours turns its diagonal gradient 90 degrees once, slowly, a glow in the
 * cover colour shows through the shadow, and a soft reflection with the same rounded corners sits under it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CoverCard(
    height: Dp,
    aspectRatio: Float = 2f / 3f,
    paletteKey: String,
    loadPalette: suspend () -> List<Color>?,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    cover: @Composable BoxScope.() -> Unit,
    badge: @Composable BoxScope.() -> Unit = {},
    reflection: Boolean = true,
    surface: Color = RiftColors.SurfaceContainer,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    // Expressive spatial springs from the theme's motion scheme.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Grow by a fixed amount, not a share, so a wide tile never spills over its neighbours.
    val focusScale = 1f + FocusGrowth.value * 2 / (height.value * aspectRatio)
    val scale by animateFloatAsState(if (focused) focusScale else 1f, spatial, label = "cardScale")
    val lift by animateDpAsState(if (focused) (-6).dp else 0.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cardLift")

    var loaded by remember(paletteKey) { mutableStateOf<List<Color>?>(null) }
    // Palette extraction is cosmetic and used only by the focused frame. Do not make every card that
    // flashes through a fast touch fling start a second image request + bitmap decode + Palette job.
    // Loading it on focus keeps scrolling cheap; CoverColors caches it for subsequent visits.
    LaunchedEffect(focused, paletteKey) {
        if (focused && loaded == null) {
            loaded = withContext(Dispatchers.IO) { runCatching { loadPalette() }.getOrNull() }
        }
    }
    val palette = loaded ?: FallbackPalette

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusRequested()
        }
    }

    // Apple TV style: the right stick tilts the focused cover a little in 3D, the sheen follows.
    val stick by LocalRightStick.current
    val tiltSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val tiltX by animateFloatAsState(if (focused) stick.x else 0f, tiltSpec, label = "tiltX")
    val tiltY by animateFloatAsState(if (focused) stick.y else 0f, tiltSpec, label = "tiltY")

    val width = height * aspectRatio
    // The card's picture, recorded as it is drawn and reused flipped for the reflection, so every
    // card gets the very same reflection as soon as it shows anything.
    val picture = rememberGraphicsLayer()
    Column(modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift.toPx()
                    rotationY = tiltX * MAX_TILT_DEGREES
                    rotationX = -tiltY * MAX_TILT_DEGREES
                    cameraDistance = 14f * density
                }
                .shadow(
                    elevation = if (focused) 20.dp else 0.dp,
                    shape = RiftShapes.card,
                    ambientColor = palette.first().copy(alpha = 0.22f),
                    spotColor = palette.first().copy(alpha = 0.22f),
                )
                .clip(RiftShapes.card)
                .background(surface)
                .coverSheen(focused, tiltX, tiltY)
                .coverFrame(focused, palette, RiftShapes.card)
                .drawWithContent {
                    picture.record { this@drawWithContent.drawContent() }
                    drawLayer(picture)
                }
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            cover()
            badge()
        }
        if (reflection) {
            Spacer(modifier = Modifier.height(10.dp))
            CoverReflection(
                picture = picture,
                coverHeight = height,
                height = height * REFLECTION_SHARE,
                focused = focused,
                scale = scale,
                surface = surface,
            )
        }
    }
}

/**
 * Gradient frame in the cover colours. On focus the first colour starts as a small patch in the
 * top right corner, spreads along the edge while the gradient turns 90 degrees, then stays.
 */
@Composable
private fun Modifier.coverFrame(focused: Boolean, colors: List<Color>, shape: Shape): Modifier {
    if (!COVER_FRAME_ENABLED) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(focused, colors) {
        progress.snapTo(0f)
        if (focused) progress.animateTo(1f, tween(FRAME_SWEEP_MS, easing = FastOutSlowInEasing))
    }
    val alpha by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "frameAlpha")
    return drawWithContent {
        drawContent()
        if (alpha <= 0f) return@drawWithContent
        val p = progress.value
        // 135 degrees puts the gradient's start in the top right corner; it turns to 225.
        val radians = Math.toRadians(135.0 + 90.0 * p)
        val half = Offset((cos(radians) * size.width / 2).toFloat(), (sin(radians) * size.height / 2).toFloat())
        val bandEnd = 0.08f + 0.37f * p
        val brush = Brush.linearGradient(
            0f to colors[0],
            bandEnd to colors[0],
            (bandEnd + 0.45f).coerceAtMost(1f) to colors[1],
            1f to colors[1],
            start = center - half,
            end = center + half,
        )
        // The card is clipped to its shape, so only the inner half of this stroke shows.
        drawOutline(shape.createOutline(size, layoutDirection, this), brush, alpha = alpha, style = Stroke(FrameWidth.toPx() * 2))
    }
}

/**
 * A light sheen over the cover: on focus it sweeps across once, corner to corner; afterwards it
 * shows only while the cover is tilted, sliding with the tilt.
 */
@Composable
private fun Modifier.coverSheen(focused: Boolean, tiltX: Float, tiltY: Float): Modifier {
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(focused) {
        if (focused) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SHEEN_SWEEP_MS, easing = FastOutSlowInEasing))
        }
    }
    return drawWithContent {
        drawContent()
        val sweeping = focused && sweep.value < 1f
        val tilt = (abs(tiltX) + abs(tiltY)).coerceAtMost(1f)
        val strength = if (sweeping) 0.32f * sin(Math.PI * sweep.value).toFloat() else 0.22f * tilt
        if (strength <= 0.01f) return@drawWithContent
        // Position along the diagonal: the sweep runs past both corners; a tilt moves it off centre.
        val t = if (sweeping) -0.3f + 1.6f * sweep.value else 0.5f + 0.5f * (tiltX + tiltY) / 2f
        val band = size.minDimension * 0.6f
        val c = Offset(size.width * t, size.height * t)
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = strength),
                1f to Color.Transparent,
                start = c - Offset(band, band) / 2f,
                end = c + Offset(band, band) / 2f,
            ),
        )
    }
}

@Composable
private fun CoverReflection(
    picture: GraphicsLayer,
    coverHeight: Dp,
    height: Dp,
    focused: Boolean,
    scale: Float,
    surface: Color,
) {
    val alpha by animateFloatAsState(if (focused) 0.42f else 0.3f, tween(300), label = "reflectionAlpha")
    // One mask for everything: the card's own rounded shape, so the reflection has the same corners.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = scale
                this.alpha = alpha
                shape = RiftShapes.card
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // Same base as the card, then its picture flipped: its bottom edge at the top here.
                drawRect(surface)
                val coverPx = coverHeight.toPx()
                translate(top = coverPx) {
                    scale(scaleX = 1f, scaleY = -1f, pivot = Offset.Zero) { drawLayer(picture) }
                }
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.45f), 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

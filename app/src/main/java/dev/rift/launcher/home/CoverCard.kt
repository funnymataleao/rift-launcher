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
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FrameWidth = 2.dp
private const val FRAME_SWEEP_MS = 1400
private const val REFLECTION_SHARE = 0.14f

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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    // Expressive spatial springs from the theme's motion scheme.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spatial, label = "cardScale")
    val lift by animateDpAsState(if (focused) (-8).dp else 0.dp, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cardLift")

    var loaded by remember(paletteKey) { mutableStateOf<List<Color>?>(null) }
    // Loaded up front (cached per image), so the frame already has the right colours when focus lands.
    LaunchedEffect(paletteKey) {
        if (loaded == null) loaded = withContext(Dispatchers.IO) { runCatching { loadPalette() }.getOrNull() }
    }
    val palette = loaded ?: FallbackPalette

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusRequested()
        }
    }

    val width = height * aspectRatio
    Column(modifier = Modifier.width(width)) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = lift.toPx()
                }
                .shadow(
                    elevation = if (focused) 29.dp else 0.dp,
                    shape = RiftShapes.card,
                    ambientColor = palette.first().copy(alpha = 0.34f),
                    spotColor = palette.first().copy(alpha = 0.34f),
                )
                .clip(RiftShapes.card)
                .background(RiftColors.SurfaceContainer)
                .coverFrame(focused, palette, RiftShapes.card)
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        ) {
            cover()
            badge()
        }
        Spacer(modifier = Modifier.height(10.dp))
        CoverReflection(
            coverHeight = height,
            height = height * REFLECTION_SHARE,
            focused = focused,
            scale = scale,
            cover = cover,
        )
    }
}

/**
 * Gradient frame in the cover colours. On focus the first colour starts as a small patch in the
 * top right corner, spreads along the edge while the gradient turns 90 degrees, then stays.
 */
@Composable
private fun Modifier.coverFrame(focused: Boolean, colors: List<Color>, shape: Shape): Modifier {
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

@Composable
private fun CoverReflection(
    coverHeight: Dp,
    height: Dp,
    focused: Boolean,
    scale: Float,
    cover: @Composable BoxScope.() -> Unit,
) {
    val alpha by animateFloatAsState(if (focused) 0.36f else 0.14f, tween(300), label = "reflectionAlpha")
    // One mask for everything: the card's own rounded shape, so the reflection has the same corners.
    // The coloured glow comes from the card's shadow; a separate glow layer here showed square edges.
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
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), blendMode = BlendMode.DstIn)
            },
    ) {
        // The whole cover, flipped, with its bottom edge at the top of the reflection.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(coverHeight)
                .graphicsLayer { scaleY = -1f },
            content = cover,
        )
    }
}

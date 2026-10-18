package dev.rift.launcher.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors

/**
 * The loading bar of game launches and installs: the accent the user picked on a dark grey track, no
 * outline. [progress] from 0 to 1 fills it, eased so each step of real progress glides instead of jumping;
 * null or below 0 means there is nothing to count, and a short accent segment runs across the track.
 * A gradient accent spans the whole track, so the fill and the segment show the part of it they are over.
 * The caller sets the size (a height of 4 dp, say).
 */
@Composable
fun RiftProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    val brush = RiftAccent.current.brush
    val determinate = progress != null && progress >= 0f
    val fill by animateFloatAsState(
        targetValue = if (determinate) progress!!.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(FILL_MS, easing = FastOutSlowInEasing),
        label = "riftProgressFill",
    )
    val sweep = if (determinate) {
        null
    } else {
        rememberInfiniteTransition(label = "riftProgressSweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SWEEP_MS, easing = SweepEasing)),
            label = "riftProgressSweep",
        )
    }

    // Animated values are read while drawing, so a running bar only redraws and never recomposes.
    Canvas(modifier = modifier.clip(CircleShape)) {
        drawRect(color = RiftColors.SurfaceContainerHigh)
        val radius = CornerRadius(size.height / 2f)
        if (sweep == null) {
            val width = size.width * fill
            if (width > 0f) drawRoundRect(brush = brush, size = Size(width, size.height), cornerRadius = radius)
        } else {
            val width = size.width * SEGMENT
            val start = (sweep.value * (1f + SEGMENT) - SEGMENT) * size.width
            drawRoundRect(
                brush = brush,
                topLeft = Offset(start, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

private const val FILL_MS = 350
private const val SWEEP_MS = 1200

/** The segment's share of the track. */
private const val SEGMENT = 0.32f

/** Eases in and out, without overshoot; the slow ends fall where the segment is off the track. */
private val SweepEasing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

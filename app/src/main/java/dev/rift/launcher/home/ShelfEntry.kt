package dev.rift.launcher.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the covers of a shelf come in when it first shows: one after another, sliding in from the
 * side [direction] points to (1 from the right), or rising from below when [rise]. Only a graphics
 * layer moves, so nothing is laid out again and focus stays where it is.
 */
data class ShelfEntry(
    val direction: Int,
    val distance: Dp,
    val startMs: Long,
    val stepMs: Long,
    val durationMs: Long,
    val rise: Boolean = false,
    val maxStaggered: Int = MAX_STAGGERED,
) {
    val totalMs: Long get() = startMs + stepMs * maxStaggered + durationMs

    companion object {
        private const val MAX_STAGGERED = 10

        /** The app start: covers roll in from the right, slowly. */
        val Intro = ShelfEntry(direction = 1, distance = 180.dp, startMs = 120, stepMs = 55, durationMs = 720)

        /** Switching tabs with LB/RB: a short, quick slide. */
        fun switch(direction: Int) = ShelfEntry(direction = direction, distance = 96.dp, startMs = 0, stepMs = 28, durationMs = 420)

        /** A library grid opening or changing tab: covers rise in one after another, quickly (Denis). */
        val Library = ShelfEntry(direction = 1, distance = 56.dp, startMs = 40, stepMs = 18, durationMs = 380, rise = true, maxStaggered = 24)
    }
}

/** Material 3's emphasized decelerate curve: fast in, long soft landing. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * One clock for the whole shelf, in milliseconds since it first showed; it stops (at the end) once
 * every cover is in. Covers read it only while drawing, so a cover can never be left half way, even
 * if the list composes it late or reuses it.
 */
@Composable
fun rememberShelfClock(entry: ShelfEntry?, started: Boolean = true): State<Long> =
    produceState(if (entry == null) Long.MAX_VALUE else 0L, started) {
    // Until [started], the covers wait unseen at the start of their entrance.
    if (entry == null || !started) return@produceState
    val start = withFrameMillis { it }
    while (true) {
        val elapsed = withFrameMillis { it } - start
        if (elapsed >= entry.totalMs) break
        value = elapsed
    }
    value = Long.MAX_VALUE
}

/** Applies [entry] to the cover at [index], timed by the shelf's [clock]; null leaves it in place. */
fun Modifier.shelfEntry(entry: ShelfEntry?, index: Int, clock: State<Long>): Modifier {
    if (entry == null) return this
    return graphicsLayer {
        // Fade each drawing on its own: an offscreen layer would clip the focused cover's growth,
        // lift, frame and rounded corners to the tile's bounds while it fades.
        compositingStrategy = CompositingStrategy.ModulateAlpha
        val elapsed = clock.value
        val p = if (elapsed == Long.MAX_VALUE) {
            1f
        } else {
            val t = (elapsed - entry.startMs - entry.stepMs * index.coerceAtMost(entry.maxStaggered)).toFloat() / entry.durationMs
            EmphasizedDecelerate.transform(t.coerceIn(0f, 1f))
        }
        val offset = (1f - p) * entry.distance.toPx() * entry.direction
        if (entry.rise) translationY = offset else translationX = offset
        alpha = p
    }
}

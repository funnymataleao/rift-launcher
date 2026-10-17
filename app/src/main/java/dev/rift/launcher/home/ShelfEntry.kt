package dev.rift.launcher.home

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * How the covers of a shelf come in when it first shows: one after another, sliding in from the
 * side [direction] points to (1 from the right, -1 from the left). Only a graphics layer moves, so
 * nothing is laid out again and focus stays where it is.
 */
data class ShelfEntry(val direction: Int, val distance: Dp, val startMs: Long, val stepMs: Long, val slow: Boolean) {
    companion object {
        /** The app start: covers roll in from the right, slowly. */
        val Intro = ShelfEntry(direction = 1, distance = 180.dp, startMs = 120, stepMs = 55, slow = true)

        /** Switching tabs with LB/RB: a short, quick slide from the side the switch goes to. */
        fun switch(direction: Int) = ShelfEntry(direction = direction, distance = 96.dp, startMs = 0, stepMs = 28, slow = false)
    }
}

/** Applies [entry] to the cover at [index]; null leaves the cover where it is. */
@Composable
fun Modifier.shelfEntry(entry: ShelfEntry?, index: Int): Modifier {
    val appear = remember { Animatable(if (entry != null) 0f else 1f) }
    val spec = if (entry?.slow == true) {
        MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    } else {
        MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    }
    LaunchedEffect(Unit) {
        if (entry != null && appear.value < 1f) {
            delay(entry.startMs + entry.stepMs * index.coerceAtMost(10))
            appear.animateTo(1f, spec)
        }
    }
    val direction = entry?.direction ?: 1
    val distance = entry?.distance ?: 0.dp
    return graphicsLayer {
        val p = appear.value
        translationX = (1f - p) * distance.toPx() * direction
        alpha = p.coerceIn(0f, 1f)
    }
}

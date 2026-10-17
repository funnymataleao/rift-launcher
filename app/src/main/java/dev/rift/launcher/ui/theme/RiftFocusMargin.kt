package dev.rift.launcher.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Free space kept around whatever has focus, on every scroll container of the app (Denis 2026-10-17: with
 * the D-pad the last cards were cut at the bottom). A scroll container normally scrolls a focused child
 * only until it touches the edge and ignores its own padding and the child's focus enlargement, so the
 * last row ended flush with the edge. With this spec it scrolls until the child is whole with [Margin]
 * on both sides. A screen that needs other numbers (home shelves, the apps grid) provides its own
 * [BringIntoViewSpec] around its list; the nearest one wins.
 */
@OptIn(ExperimentalFoundationApi::class)
private class RiftBringIntoViewSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // The margin shrinks when the child nearly fills the container; a child bigger than the
        // container is only lined up with the nearest edge, never scrolled back and forth.
        val m = margin.coerceAtMost(((containerSize - size) / 2f).coerceAtLeast(0f))
        val end = containerSize - m
        return when {
            size >= containerSize -> when {
                offset > 0f -> offset
                offset + size < containerSize -> offset + size - containerSize
                else -> 0f
            }
            offset < m -> offset - m
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

/** Gives every scroll container inside [content] the focus margin; used at the root of the Rift and GameNative themes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RiftFocusMargin(content: @Composable () -> Unit) {
    val margin = with(LocalDensity.current) { Margin.toPx() }
    val spec = remember(margin) { RiftBringIntoViewSpec(margin) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

private val Margin = 48.dp

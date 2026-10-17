package dev.rift.launcher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps room around a focused tile in a scrolling grid. A grid scrolls a focused tile only until it is
 * inside the screen, and does not count its own content padding, so the last row ended flush with the
 * edge (and under the system bar). With this on the tile, the grid scrolls to show [top] and [bottom]
 * of free space around it as well.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.viewGap(top: Dp = 32.dp, bottom: Dp = 64.dp): Modifier {
    val density = LocalDensity.current
    val responder = remember(top, bottom, density) {
        val topPx = with(density) { top.toPx() }
        val bottomPx = with(density) { bottom.toPx() }
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect =
                Rect(localRect.left, localRect.top - topPx, localRect.right, localRect.bottom + bottomPx)

            // The grid does the scrolling; there is nothing inside a tile to scroll.
            override suspend fun bringChildIntoView(localRect: () -> Rect?) = Unit
        }
    }
    return bringIntoViewResponder(responder)
}

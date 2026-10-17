package dev.rift.launcher.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Rift's one surface material (Denis 2026-10-17): black matte translucent glass. What lies behind
 * is blurred and darkened, never grey, never a white outline. Cards, plates, panels and pills all
 * use it, so the whole interface reads as one material.
 *
 * Use [blackGlass] on the element's background; the screen provides what lies behind through
 * [LocalGlassBackdrop] (a [GraphicsLayer] it records, in root coordinates). Clip the element to its
 * shape before the modifier; [rim] adds the hairline edge of the glass.
 */
object BlackGlass {
    /** The black over the blurred picture: dark enough for text, light enough to show the art. */
    val Tint = Color.Black.copy(alpha = 0.55f)

    /** Without a picture behind (or blur, before Android 12): nearly opaque black. */
    val Solid = Color.Black.copy(alpha = 0.82f)

    val Blur = 24.dp

    /** The hairline edge, brighter at the top like light on glass; also the focus frame's colours. */
    val RimColors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
}

/** What black glass blurs: the screen's picture behind it, recorded in root coordinates. */
val LocalGlassBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Paints black glass behind the content: [backdrop] lined up with this element, blurred into a layer
 * of its own (the content stays sharp), then [BlackGlass.Tint]. With no backdrop it is
 * [BlackGlass.Solid]. [rim] draws the hairline edge along [shape].
 */
@Composable
fun Modifier.blackGlass(
    shape: Shape? = null,
    rim: Boolean = false,
    backdrop: GraphicsLayer? = LocalGlassBackdrop.current,
): Modifier {
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            if (canBlur) {
                val px = BlackGlass.Blur.toPx()
                blurred.renderEffect = BlurEffect(px, px, TileMode.Clamp)
                blurred.record(IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                    translate(-origin.x, -origin.y) { drawLayer(backdrop!!) }
                }
                drawLayer(blurred)
                drawRect(BlackGlass.Tint)
            } else {
                drawRect(BlackGlass.Solid)
            }
            if (rim && shape != null) {
                drawOutline(
                    shape.createOutline(size, layoutDirection, this),
                    Brush.verticalGradient(BlackGlass.RimColors),
                    style = Stroke(1.dp.toPx() * 2),
                )
            }
        }
}

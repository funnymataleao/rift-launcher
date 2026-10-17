package dev.rift.launcher.stream

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import dev.rift.launcher.ui.theme.RiftAccent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

/** Frosted glass panel on pure black: a faint white fill, no edge. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RiftShapes.sheet)
            .background(GlassFill)
            .padding(28.dp),
        content = content,
    )
}

/**
 * A focusable surface with Rift's two states (Denis): focused it is filled with the accent (content
 * dark, see [focusedContent]), otherwise grey; no focus ring. A spring scale marks the move.
 * Gamepad A reaches it as DPAD_CENTER through Android's key fallback, like every Rift control.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RiftShapes.card,
    focusedScale: Float = 1.04f,
    background: (focused: Boolean) -> Brush = { if (it) RiftAccent.current.brush else SolidColor(RiftColors.SurfaceContainer) },
    focusRequester: FocusRequester? = null,
    // Kept for callers; focus is shown by the fill alone (no rings anywhere).
    outline: Boolean = false,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, RiftMotion.focusSpring(), label = "focusScale")
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background(focused))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}

/** The colour of text and icons on a [FocusSurface]: dark on the accent fill, light on grey. */
fun focusedContent(focused: Boolean): Color = if (focused) RiftColors.OnPrimary else RiftColors.OnSurface

/** Rift's pill button: accent when focused, grey otherwise. */
@Composable
fun StreamPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(RiftSizes.button).widthIn(min = 140.dp),
        shape = RiftShapes.full,
        focusedScale = 1.06f,
        focusRequester = focusRequester,
        outline = false,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Text(
            text = text,
            color = focusedContent(focused),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

/** Small round status dot: lemon when the PC is online, grey otherwise; dark on a focused (lemon) row. */
@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier, focused: Boolean = false) {
    Box(
        modifier = modifier
            .width(10.dp)
            .height(10.dp)
            .clip(RiftShapes.full)
            .background(
                when {
                    focused -> if (online) RiftColors.OnPrimary else RiftColors.OnPrimary.copy(alpha = 0.4f)
                    online -> RiftColors.Primary
                    else -> RiftColors.Outline
                },
            ),
    )
}

val GlassFill = Color(0x14FFFFFF)

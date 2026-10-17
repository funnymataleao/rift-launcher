package dev.rift.launcher.settings

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * THE slider of Rift, for every setting whose answers come in an order (resolution, frame rate, bitrate,
 * quality, volume, brightness, a scale, a percentage): a track with a stop at each answer, the chosen one
 * always written next to the title. Left and right step one answer (the ends stay, so a held button does
 * not leave the page), a finger drags or taps. Two states like every plate: grey at rest with an accent
 * track, filled with the accent while focused with a dark track. Only answers with no order (a mode, a
 * type; two to four of them) are [ChoiceChipsRow] pills.
 */
@Composable
internal fun StepSliderRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val index = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
    StepSliderPlate(
        header = { focused ->
            if (icon != null) RowIcon(icon, focused)
            RowTexts(title, subtitle, danger = false, focused = focused, modifier = Modifier.weight(1f))
        },
        valueText = options.getOrElse(index) { "" },
        startText = options.firstOrNull().orEmpty(),
        endText = options.lastOrNull().orEmpty(),
        count = options.size,
        index = index,
        onIndex = onSelect,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * The plate under [StepSliderRow] and the numeric sliders: [count] stops, the chosen one at [index]. [header]
 * is the icon and the texts (it gets the focus state and must give its text the weight to push the value aside).
 */
@Composable
internal fun StepSliderPlate(
    header: @Composable RowScope.(focused: Boolean) -> Unit,
    valueText: String,
    startText: String,
    endText: String,
    count: Int,
    index: Int,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val current by rememberUpdatedState(index)
    val change by rememberUpdatedState(onIndex)
    val last = (count - 1).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .plateFill(focused)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@onPreviewKeyEvent false
                }
                val next = current + step
                if (next in 0..last) {
                    change(next)
                    true
                } else {
                    // At the end a held button stays on the slider; a single press may go on to the list.
                    event.nativeKeyEvent.repeatCount > 0
                }
            }
            .focusable(enabled, interaction)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            header(focused)
            // The chosen answer, whole: it wraps instead of being cut.
            Text(
                text = valueText,
                color = plateText(focused),
                fontSize = 18.5.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        StepTrack(count = count, index = index, focused = focused, enabled = enabled, onIndex = { change(it) })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = startText,
                color = plateSubtext(focused),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
            )
            Text(
                text = endText,
                color = plateSubtext(focused),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private val TrackHeight = 12.dp
private val ThumbWidth = 6.dp
private val ThumbHeight = 32.dp
private val ThumbGap = 6.dp
private val TrackInset = 8.dp
private val TickRadius = 2.dp

/** The track: filled with the accent up to the handle, a dot at every stop. */
@Composable
private fun StepTrack(count: Int, index: Int, focused: Boolean, enabled: Boolean, onIndex: (Int) -> Unit) {
    val accent = RiftAccent.current
    val position by animateFloatAsState(index.toFloat(), RiftMotion.focusSpring(), label = "stepSlider")
    val change by rememberUpdatedState(onIndex)
    val last = (count - 1).coerceAtLeast(1)

    val activeFill: Brush = if (focused) SolidColor(accent.onAccent) else accent.brush
    val inactiveFill: Color = if (focused) accent.onAccent.copy(alpha = 0.22f) else RiftColors.SurfaceContainerHigh
    val tickOnActive: Color = if (focused) accent.main else accent.onAccent.copy(alpha = 0.6f)
    val tickOnInactive: Color = if (focused) accent.onAccent.copy(alpha = 0.55f) else RiftColors.OnSurfaceVariant.copy(alpha = 0.6f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(ThumbHeight)
            .pointerInput(count, enabled) {
                if (!enabled) return@pointerInput
                val inset = TrackInset.toPx()
                fun stopAt(x: Float) = (((x - inset) / (size.width - inset * 2)) * last).roundToInt().coerceIn(0, last)
                detectTapGestures { change(stopAt(it.x)) }
            }
            .pointerInput(count, enabled) {
                if (!enabled) return@pointerInput
                val inset = TrackInset.toPx()
                fun stopAt(x: Float) = (((x - inset) / (size.width - inset * 2)) * last).roundToInt().coerceIn(0, last)
                detectHorizontalDragGestures { pointer, _ -> change(stopAt(pointer.position.x)) }
            },
    ) {
        val inset = TrackInset.toPx()
        val trackH = TrackHeight.toPx()
        val thumbW = ThumbWidth.toPx()
        val gap = ThumbGap.toPx()
        val centerY = size.height / 2
        val top = centerY - trackH / 2
        val span = size.width - inset * 2
        val thumbX = inset + span * (position / last)
        val radius = CornerRadius(trackH / 2)

        val activeEnd = thumbX - gap
        if (activeEnd > 0f) {
            drawRoundRect(activeFill, Offset(0f, top), Size(activeEnd, trackH), radius)
        }
        val inactiveStart = thumbX + gap
        if (inactiveStart < size.width) {
            drawRoundRect(inactiveFill, Offset(inactiveStart, top), Size(size.width - inactiveStart, trackH), radius)
        }
        if (count in 2..MAX_STOPS) {
            for (stop in 0 until count) {
                val x = inset + span * (stop.toFloat() / last)
                if (abs(x - thumbX) < gap + thumbW) continue
                drawCircle(if (x < thumbX) tickOnActive else tickOnInactive, TickRadius.toPx(), Offset(x, centerY))
            }
        }
        drawRoundRect(activeFill, Offset(thumbX - thumbW / 2, 0f), Size(thumbW, size.height), CornerRadius(thumbW / 2))
    }
}

/** More stops than this are too close for dots; the track alone shows them. */
private const val MAX_STOPS = 24

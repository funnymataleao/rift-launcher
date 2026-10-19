package dev.rift.launcher.settings

import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// Rift: small enough that every colour (and every gradient) fits one row on the tablet.
private val SwatchSize = 48.dp
/** The least room between two circles: a focused one grows by a third and must not touch its neighbour. */
private val SwatchMinGap = 20.dp
private const val MIN_BRIGHTNESS = 0.4f

/**
 * The accent colour of the whole app: solid colours, a few gradients and a colour wheel. The choice applies
 * at once everywhere [RiftAccent] is read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentSettings() {
    val context = LocalContext.current
    val current = RiftAccent.current
    var wheelOpen by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(title = stringResource(R.string.rift_settings_accent_title)) {
        SwatchPlate(stringResource(R.string.rift_settings_accent_colors), RiftAccent.solids, current.id) {
            RiftAccent.select(context, it)
        }
        SwatchPlate(stringResource(R.string.rift_settings_accent_gradients), RiftAccent.gradients, current.id) {
            RiftAccent.select(context, it)
        }
        LinkRow(
            title = stringResource(R.string.rift_settings_accent_custom),
            icon = Icons.Rounded.Palette,
            value = stringResource(R.string.rift_settings_accent_custom_pick),
            onClick = { wheelOpen = true },
        )
    }

    if (wheelOpen) {
        ColorWheelDialog(
            initial = current.main,
            onApply = {
                RiftAccent.select(context, RiftAccent.custom(it))
                wheelOpen = false
            },
            onDismiss = { wheelOpen = false },
        )
    }
}

@Composable
private fun SwatchPlate(
    title: String,
    choices: List<RiftAccent.Choice>,
    selectedId: String,
    onPick: (RiftAccent.Choice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .plateFill(focused = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = title, color = RiftColors.OnSurface, fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)
        // The circles share the plate's whole width in even columns (Denis: they bunched up on the left with
        // empty room on the right); a short last row keeps the columns of the rows above it.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + SwatchMinGap) / (SwatchSize + SwatchMinGap)).toInt().coerceIn(1, choices.size.coerceAtLeast(1))
            Column(verticalArrangement = Arrangement.spacedBy(SwatchMinGap)) {
                choices.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { choice -> Swatch(choice, selected = choice.id == selectedId) { onPick(choice) } }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.size(SwatchSize)) }
                    }
                }
            }
        }
    }
}

/** A round colour (or gradient) to pick. It is the colour itself, so there is no plate to fill: the focused one just grows. */
@Composable
private fun Swatch(choice: RiftAccent.Choice, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.3f else 1f, RiftMotion.focusSpring(), label = "swatchScale")
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(choice.brush)
            .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = choice.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Colour wheel for a colour of the user's own: touch the wheel for hue and saturation, or use the three
 * sliders, which a gamepad can move. Brightness stops short of black so the accent always shows.
 */
@Composable
private fun ColorWheelDialog(initial: Color, onApply: (Color) -> Unit, onDismiss: () -> Unit) {
    val start = remember {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initial.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2].coerceIn(MIN_BRIGHTNESS, 1f)) }
    val picked = Color.hsv(hue, saturation, brightness)

    RiftDialog(
        title = stringResource(R.string.rift_settings_accent_custom),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_settings_accent_apply), onClick = { onApply(picked) }, primary = true)
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Wheel(
                hue = hue,
                saturation = saturation,
                brightness = brightness,
                picked = picked,
                onChange = { h, s ->
                    hue = h
                    saturation = s
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WheelSlider(stringResource(R.string.rift_settings_accent_hue), hue / 360f, picked) { hue = it * 360f }
                WheelSlider(stringResource(R.string.rift_settings_accent_saturation), saturation, picked) { saturation = it }
                WheelSlider(stringResource(R.string.rift_settings_accent_brightness), (brightness - MIN_BRIGHTNESS) / (1f - MIN_BRIGHTNESS), picked) {
                    brightness = MIN_BRIGHTNESS + it * (1f - MIN_BRIGHTNESS)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(PlateShape)
                        .background(picked),
                )
            }
        }
    }
}

@Composable
private fun WheelSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Column {
        Text(text = label, color = RiftColors.OnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = RiftColors.SurfaceContainerHigh,
            ),
        )
    }
}

@Composable
private fun Wheel(hue: Float, saturation: Float, brightness: Float, picked: Color, onChange: (Float, Float) -> Unit) {
    val hues = remember { List(13) { Color.hsv(it * 30f, 1f, 1f) } }
    Canvas(
        modifier = Modifier
            .size(212.dp)
            .pointerInput(Unit) {
                val radius = min(size.width, size.height) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                fun pick(at: Offset) {
                    val dx = at.x - center.x
                    val dy = at.y - center.y
                    val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                    onChange((angle + 360f) % 360f, (hypot(dx, dy) / radius).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pick(down.position)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        pick(change.position)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val radius = min(size.width, size.height) / 2f
        drawCircle(Brush.sweepGradient(hues, center))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = radius))
        drawCircle(Color.Black.copy(alpha = 1f - brightness))
        val angle = Math.toRadians(hue.toDouble())
        val marker = Offset(
            center.x + (cos(angle) * saturation * radius).toFloat(),
            center.y + (sin(angle) * saturation * radius).toFloat(),
        )
        drawCircle(picked, radius = 13.dp.toPx(), center = marker)
        drawCircle(Color.White, radius = 13.dp.toPx(), center = marker, style = Stroke(width = 3.dp.toPx()))
    }
}


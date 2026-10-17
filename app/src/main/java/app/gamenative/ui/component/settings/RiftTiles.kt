package app.gamenative.ui.component.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alorma.compose.settings.ui.base.internal.LocalSettingsGroupEnabled
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.RiftChip
import dev.rift.launcher.settings.RiftSwitch
import dev.rift.launcher.settings.RowMinHeight
import dev.rift.launcher.settings.StepSliderPlate
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import kotlin.math.roundToInt

/*
 * Rift's look for the rows of GameNative's settings screens (the game container): the same plates as Rift's
 * own Settings. A row at rest is a grey plate; the one with focus is filled with the accent (a gradient
 * stays a gradient) with dark text and icons. There are no outlines and no ripples.
 *
 * [RiftTile] has the shape of the library's tile scaffold, so the dropdown, text and list rows below it only
 * swap the call; [SettingsSwitch], [SettingsMenuLink] and [SettingsGroup] take the place of the library's
 * widgets of the same name.
 */

/** True inside a [RiftTile] that has focus, for what sits in its action slot. */
internal val LocalTileFocused = compositionLocalOf { false }

@Composable
fun RiftTile(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    // The library's colours are not used; the parameters only keep the old call sites compiling.
    @Suppress("UNUSED_PARAMETER") colors: SettingsTileColors = SettingsTileDefaults.colors(),
    @Suppress("UNUSED_PARAMETER") tonalElevation: Dp = 0.dp,
    @Suppress("UNUSED_PARAMETER") shadowElevation: Dp = 0.dp,
    action: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, RiftMotion.focusSpring(), label = "tileScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .plateFill(focused)
            // Before [modifier], so it sees the focus target (the clickable) that comes with it.
            .onFocusChanged { focused = it.hasFocus }
            .then(modifier)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CompositionLocalProvider(LocalTileFocused provides focused) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides plateText(focused)) { icon() }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CompositionLocalProvider(LocalContentColor provides plateText(focused)) {
                    ProvideTextStyle(TextStyle(fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)) { title() }
                }
                if (subtitle != null) {
                    CompositionLocalProvider(LocalContentColor provides plateSubtext(focused)) {
                        ProvideTextStyle(TextStyle(fontSize = 15.sp, lineHeight = 20.sp)) { subtitle() }
                    }
                }
            }
            if (action != null) {
                CompositionLocalProvider(LocalContentColor provides plateSubtext(focused)) { action() }
            }
        }
    }
}

/** A group of rows: one plate per row, a plate gap apart. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSettingsGroupEnabled provides enabled) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlateGap), content = content)
    }
}

/** A row with an on/off switch; the whole row toggles. */
@Composable
fun SettingsSwitch(
    state: Boolean,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    icon: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    colors: SettingsTileColors = SettingsTileDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    onCheckedChange: (Boolean) -> Unit,
) {
    RiftTile(
        modifier = Modifier
            .toggleable(
                value = state,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .then(modifier),
        enabled = enabled,
        title = title,
        subtitle = subtitle,
        icon = icon,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        action = { RiftSwitch(checked = state, focused = LocalTileFocused.current) },
    )
}

/** A row that does something when pressed; [action] is what shows at its end. */
@Composable
fun SettingsMenuLink(
    title: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    icon: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    colors: SettingsTileColors = SettingsTileDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    action: (@Composable () -> Unit)? = null,
) {
    RiftTile(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(modifier),
        enabled = enabled,
        title = title,
        subtitle = subtitle,
        icon = icon,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        action = action,
    )
}

/** How a list of answers is shown in a settings tile. */
internal enum class ChoiceLook { Slider, Pills, Dropdown }

/**
 * Answers that go from less to more (numbers, with their unit: 60 Hz, 1080p, 50 %, 512 MB, 1280x720; words like
 * Auto or Off may stand among them) are a slider with a stop at each. A few short answers with no order are pills
 * under the title, as in Rift's own Settings. A longer list stays a dropdown.
 */
internal fun choiceLook(items: List<String>, hasMuted: Boolean, hasAction: Boolean): ChoiceLook {
    if (hasMuted || hasAction) return ChoiceLook.Dropdown
    val numeric = items.count { NUMERIC.matches(it.trim()) }
    val neutral = items.count { it.trim().lowercase() in NEUTRAL }
    if (items.size in 3..MAX_STOPS && numeric >= 2 && numeric + neutral == items.size) return ChoiceLook.Slider
    if (items.size in 2..MAX_PILLS && items.all { it.length <= MAX_PILL_TEXT }) return ChoiceLook.Pills
    return ChoiceLook.Dropdown
}

private val NUMERIC = Regex("""[<>~≈]?\s?\d+([.,]\d+)?\s?([%a-zA-Zµ/]{0,6}|x\s?\d+([.,]\d+)?.*)""")
private val NEUTRAL = setOf("auto", "default", "off", "none", "unlimited", "disabled", "automatic")
private const val MAX_PILLS = 4
private const val MAX_PILL_TEXT = 14
private const val MAX_STOPS = 24

@Composable
internal fun RiftChoiceTile(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .plateFill(focused = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides RiftColors.OnSurface) { icon() }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CompositionLocalProvider(LocalContentColor provides RiftColors.OnSurface) {
                    ProvideTextStyle(TextStyle(fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)) { title() }
                }
                if (subtitle != null) {
                    CompositionLocalProvider(LocalContentColor provides RiftColors.OnSurfaceVariant) {
                        ProvideTextStyle(TextStyle(fontSize = 15.sp, lineHeight = 20.sp)) { subtitle() }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, label ->
                RiftChip(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The dropdown's answers in order, as the shared slider: the title, subtitle and icon are the tile's own. */
@Composable
internal fun RiftStepTile(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    StepSliderPlate(
        header = { focused ->
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides plateText(focused)) { icon() }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CompositionLocalProvider(LocalContentColor provides plateText(focused)) {
                    ProvideTextStyle(TextStyle(fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)) { title() }
                }
                if (subtitle != null) {
                    CompositionLocalProvider(LocalContentColor provides plateSubtext(focused)) {
                        ProvideTextStyle(TextStyle(fontSize = 15.sp, lineHeight = 20.sp)) { subtitle() }
                    }
                }
            }
        },
        valueText = items.getOrElse(selectedIndex) { "" },
        startText = items.firstOrNull().orEmpty(),
        endText = items.lastOrNull().orEmpty(),
        count = items.size,
        index = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        onIndex = onSelect,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * A number on a slider with a stop every [step] (the shared [StepSliderPlate]), the value at the title's end.
 * [valueText] is what the value is called.
 */
@Composable
fun RiftSliderTile(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    step: Float = defaultStep(valueRange),
    startText: String = "",
    endText: String = "",
) {
    val count = ((valueRange.endInclusive - valueRange.start) / step).roundToInt() + 1
    val index = ((value - valueRange.start) / step).roundToInt().coerceIn(0, count - 1)
    StepSliderPlate(
        header = { focused ->
            Text(
                text = title,
                color = plateText(focused),
                fontSize = 18.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        },
        valueText = valueText,
        startText = startText,
        endText = endText,
        count = count,
        index = index,
        onIndex = { onValueChange(valueRange.start + it * step) },
        enabled = enabled,
        modifier = modifier,
    )
}

private fun defaultStep(range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span >= 100f) 10f else if (span >= 20f) 5f else 1f
}

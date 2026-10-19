package dev.rift.launcher.settings

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

/*
 * Building blocks of Rift's settings: a group of plates (every row is a plate of its own), switch, link,
 * choice chips and info rows, plus the round and pill buttons dialogs use. The plate that has focus is
 * filled with the accent colour, with dark text and icons on it; there are no focus rings.
 */

/** One shape for every plate, in the list on the left and the rows on the right. */
internal val PlateShape = RiftShapes.card
internal val PlateGap = 8.dp
internal val RowMinHeight = 84.dp
internal val RowIconSize = 44.dp

/** The title of a row; the side panels measure their longest line with it. */
internal val RowTitleStyle = TextStyle(fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)

/**
 * A plate's fill when it has no focus. Grey on Rift's pages; the side panels make it black (Denis: the
 * plates in a panel are black, not grey).
 */
internal val LocalPlateRest = compositionLocalOf { RiftColors.SurfaceContainer }

/**
 * Whether plates are glass: the side panels' plates (Denis 2026-10-19: on the filters' blur they looked like
 * frosted glass, on the darker right panels like flat grey). A glass plate is a touch lighter at the top
 * than at the bottom (barely: Denis 2026-10-19, a strong gradient made the plates look rounded), with a faint
 * light edge, so it reads as glass over any backdrop, even plain black.
 */
internal val LocalPlateGlass = compositionLocalOf { false }

/** The fill of a plate: the accent colour (or gradient) while it has focus, otherwise [rest]. */
@Composable
internal fun Modifier.plateFill(focused: Boolean, shape: Shape = PlateShape, rest: Color = LocalPlateRest.current): Modifier =
    clip(shape).then(
        when {
            focused -> Modifier.background(RiftAccent.current.brush)
            LocalPlateGlass.current && rest.alpha > 0f && rest.alpha < 1f -> Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(rest.copy(alpha = (rest.alpha * 1.15f).coerceAtMost(1f)), rest.copy(alpha = rest.alpha * 0.88f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.04f), shape)
            else -> Modifier.background(rest)
        },
    )

/** Main text colour on a plate. */
internal fun plateText(focused: Boolean): Color = if (focused) RiftAccent.current.onAccent else RiftColors.OnSurface

/** Secondary text colour on a plate. */
internal fun plateSubtext(focused: Boolean): Color =
    if (focused) RiftAccent.current.onAccent.copy(alpha = 0.72f) else RiftColors.OnSurfaceVariant

/** The space between two groups on a page. */
internal val GroupGap = 32.dp

/** One size and weight for every group name: on the pages (Storage, Network) and in the sidebar (Sources, Rift). */
internal val GroupTitleStyle = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold)

/**
 * The last thing on a page, so a gamepad can always reach the bottom: rows that cannot take focus (a size, a
 * note) after the last one that can would otherwise never scroll into view. It is invisible, so it never keeps
 * the focus: Down onto it hands the focus straight back to the last row and then scrolls the page to its end
 * (Denis: the focus vanished at the bottom of every page).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PageEnd() {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    // Out of the focus change in progress, then the page goes on to its end.
                    scope.launch {
                        focusManager.moveFocus(FocusDirection.Up)
                        bringIntoView.bringIntoView()
                    }
                }
            }
            .focusable(),
    )
}

/**
 * A group of settings rows: its name, then one plate per row.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) {
            Text(
                text = title,
                color = RiftColors.OnSurface,
                style = GroupTitleStyle,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlateGap)) {
            content()
        }
    }
}

/** Plate look for anything that can take focus: a faint plate at rest, the accent fill while focused. */
@Composable
private fun Modifier.focusChrome(
    focused: Boolean,
    shape: Shape,
    restColor: Color = RiftColors.SurfaceContainer,
    scaleOnFocus: Float = 1.02f,
): Modifier {
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, RiftMotion.focusSpring(), label = "settingsFocusScale")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .plateFill(focused, shape, restColor)
}

@Composable
internal fun RowIcon(icon: ImageVector, focused: Boolean, danger: Boolean = false) {
    val colors = iconColors(focused, danger)
    RowIconDisc(colors) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.symbol, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RowIcon(icon: Painter, focused: Boolean) {
    val colors = iconColors(focused)
    RowIconDisc(colors) {
        Icon(painter = icon, contentDescription = null, tint = colors.symbol, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RowIconDisc(colors: IconColors, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(RowIconSize)
            .clip(CircleShape)
            .background(colors.disc),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun RowTexts(title: String, subtitle: String?, danger: Boolean, focused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = if (danger && !focused) RiftColors.Error else plateText(focused),
            style = RowTitleStyle,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = plateSubtext(focused),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small round action INSIDE a plate (delete, preview): it takes focus on its own, the plate stays the same size. */
internal class RowAction(
    val icon: ImageVector,
    val description: String,
    val onClick: () -> Unit,
    val modifier: Modifier = Modifier,
)

@Composable
private fun RowActions(actions: List<RowAction>, plateFocused: Boolean) {
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val colors = iconColors(plateFocused)
            Box(
                modifier = action.modifier
                    .size(RowIconSize)
                    .clip(CircleShape)
                    .then(
                        if (focused) Modifier.background(RiftAccent.current.brush) else Modifier.background(colors.disc),
                    )
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = action.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.description,
                    tint = if (focused) RiftAccent.current.onAccent else colors.symbol,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun RowChrome(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    focused: Boolean,
    enabled: Boolean,
    danger: Boolean,
    modifier: Modifier,
    interactive: Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    iconPainter: Painter? = null,
    actions: List<RowAction> = emptyList(),
    // Faint at rest (a store not signed in, say) but still reachable; full strength while focused.
    dimmed: Boolean = false,
    trailing: @Composable RowScope.() -> Unit,
) {
    val restAlpha = when {
        !enabled -> 0.45f
        dimmed && !focused -> 0.55f
        else -> 1f
    }
    @Composable
    fun RowScope.body() {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            RowIcon(icon, focused, danger)
        } else if (iconPainter != null) {
            RowIcon(iconPainter, focused)
        }
        RowTexts(title, subtitle, danger, focused, Modifier.weight(1f))
        trailing()
    }
    if (actions.isEmpty()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .alpha(restAlpha)
                .then(if (selected && !focused) Modifier.plateFill(focused = false, rest = RiftColors.Success.copy(alpha = 0.13f)) else Modifier.plateFill(focused))
                .then(interactive)
                .heightIn(min = RowMinHeight)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { body() }
        return
    }
    // With actions the plate is split: the part that takes focus and the actions are side by side inside one
    // plate, so D-pad right reaches an action and left comes back, and the plate keeps one size.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(restAlpha)
            .then(if (selected && !focused) Modifier.plateFill(focused = false, rest = RiftColors.Success.copy(alpha = 0.13f)) else Modifier.plateFill(focused))
            .heightIn(min = RowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(interactive)
                .heightIn(min = RowMinHeight)
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { body() }
        Box(modifier = Modifier.padding(end = 18.dp)) {
            RowActions(actions, plateFocused = focused)
        }
    }
}

/** A row with an on/off switch. The whole row toggles, so a gamepad needs one press. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowChrome(
        title = title,
        subtitle = subtitle,
        icon = icon,
        focused = focused,
        enabled = enabled,
        danger = false,
        modifier = modifier,
        leading = leading,
        interactive = Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    ) {
        RiftSwitch(checked, focused)
    }
}

/** A row with a check box, for picking several items at once. The whole row toggles. */
@Composable
internal fun CheckRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowChrome(
        title = title,
        subtitle = subtitle,
        icon = icon,
        focused = focused,
        enabled = enabled,
        danger = false,
        modifier = modifier,
        interactive = Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    ) {
        RiftCheckMark(checked, focused)
    }
}

/** Tabs at the top of a section: the chosen one is a full pill, as in [ChoiceChipsRow]. */
@Composable
internal fun SectionTabs(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            RiftChip(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Switch in the Material 3 Expressive shape: the thumb grows and carries a check mark when on. The track is
 * filled with the accent brush (a gradient accent shows as a gradient), a grey fill when off; there is no
 * outline. The stock Material switch takes only flat colours, so it is drawn here.
 */
@Composable
internal fun RiftSwitch(checked: Boolean, focused: Boolean) {
    val accent = RiftAccent.current
    // On the accent plate the switch turns dark, so it still reads.
    val thumb = when {
        checked -> accent.onAccent
        focused -> accent.onAccent.copy(alpha = 0.6f)
        else -> RiftColors.Outline
    }
    val thumbSize by animateDpAsState(if (checked) 24.dp else 20.dp, label = "switchThumb")
    val thumbStart by animateDpAsState(if (checked) 24.dp else 6.dp, label = "switchThumbX")
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .then(
                when {
                    checked && focused -> Modifier.background(accent.onAccent.copy(alpha = 0.22f))
                    checked -> Modifier.background(accent.brush)
                    focused -> Modifier.background(accent.onAccent.copy(alpha = 0.18f))
                    else -> Modifier.background(RiftColors.Outline.copy(alpha = 0.35f))
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumb),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (checked || focused) accent.main else RiftColors.SurfaceContainerHigh,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** The box of a [CheckRow]: filled with the accent brush when ticked, a grey fill when not. */
@Composable
internal fun RiftCheckMark(checked: Boolean, focused: Boolean) {
    val accent = RiftAccent.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .then(
                when {
                    checked && focused -> Modifier.background(accent.onAccent)
                    checked -> Modifier.background(accent.brush)
                    focused -> Modifier.background(accent.onAccent.copy(alpha = 0.18f))
                    else -> Modifier.background(RiftColors.Outline.copy(alpha = 0.35f))
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (focused) accent.main else accent.onAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The radio of a single-choice row, beside [RiftCheckMark]: an accent ring with a dot when picked, a grey ring when not. */
@Composable
internal fun RiftRadioMark(selected: Boolean, focused: Boolean) {
    val accent = RiftAccent.current
    val ring = when {
        focused -> accent.onAccent
        selected -> accent.main
        else -> RiftColors.Outline
    }
    Box(
        modifier = Modifier.size(24.dp).border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(ring))
    }
}

/** A radio mark (one of several is chosen); provider rows use it instead of a navigation chevron. */
@Composable
private fun ProviderSelectionMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .border(2.dp, if (checked) RiftColors.Success else RiftColors.Outline, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(RiftColors.Success))
    }
}

/**
 * A row that opens something: a dialog, a manager or a web page. [value] shows the current choice on
 * the right. [onLongClick] is for destructive actions that must not fire on a single press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    value: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: List<RowAction> = emptyList(),
    chevron: Boolean = true,
    selected: Boolean = false,
    selectionMark: Boolean? = null,
    valueColor: Color? = null,
    dimmed: Boolean = false,
    // In place of the value and the arrow: the row's own end (a button-like pill, say), told whether it is focused.
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowChrome(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconPainter = iconPainter,
        leading = leading,
        actions = actions,
        focused = focused,
        enabled = enabled,
        danger = danger,
        modifier = modifier,
        selected = selected,
        dimmed = dimmed,
        interactive = if (onLongClick != null) {
            Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
        } else {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
        },
    ) {
        if (trailing != null) {
            trailing(focused)
            return@RowChrome
        }
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = valueColor?.takeIf { !focused } ?: plateSubtext(focused),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        // An arrow only where the row opens another screen; an action (add, scan, delete) has none.
        if (selectionMark != null) {
            ProviderSelectionMark(checked = selectionMark)
        } else if (chevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = plateSubtext(focused),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** A row that only shows information, such as the app version. */
@Composable
internal fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actions: List<RowAction> = emptyList(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(focused = false)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) RowIcon(icon, focused = false)
        RowTexts(title, null, danger = false, focused = false, modifier = Modifier.weight(1f))
        Text(text = value, color = RiftColors.OnSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        RowActions(actions, plateFocused = false)
    }
}

/** The gap between a heading and the plates under it. */
internal val HeadingGap = 12.dp

/**
 * The name of the page at the top of its column: the same style, size and left line as the group names
 * (and as the captions in the section list), set [HeadingGap] above the first plates, not a whole group gap.
 */
@Composable
internal fun PageHeading(text: String, modifier: Modifier = Modifier) {
    val tuck = with(LocalDensity.current) { (GroupGap - HeadingGap).roundToPx() }
    Text(
        text = text,
        color = RiftColors.OnSurface,
        style = GroupTitleStyle,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) { placeable.place(0, 0) }
            }
            .padding(start = 6.dp),
    )
}

/**
 * A hint or a status that belongs on a settings page, set inside a plate like every other row (Denis: no loose
 * small text on the pages, above or between the headings). It takes no focus.
 */
@Composable
internal fun MessageRow(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(focused = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (title != null) {
            Text(text = title, color = RiftColors.OnSurface, fontSize = 18.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(text = text, color = RiftColors.OnSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/**
 * A plain explanation. With [underGroup] it explains the group just above and sits close under its rows,
 * not a whole group gap away; otherwise it just follows what is above it.
 */
@Composable
internal fun NoteText(text: String, modifier: Modifier = Modifier, underGroup: Boolean = false) {
    val tuck = with(LocalDensity.current) { (GroupGap - 8.dp).roundToPx() }
    Text(
        text = text,
        color = RiftColors.OnSurfaceVariant,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        modifier = modifier
            .then(
                if (!underGroup) {
                    Modifier
                } else {
                    // Reports less height and sits higher by the same amount: closer to the group above,
                    // the same distance as before to whatever follows.
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) { placeable.place(0, -tuck) }
                    }
                },
            )
            .padding(horizontal = 12.dp),
    )
}

/**
 * A row whose answer is one of two to four options WITHOUT an order (a mode, a type), shown as pills under the
 * title. Left and right move between them. Answers that go from less to more (resolution, frame rate, bitrate,
 * quality, volume, a scale) are a [StepSliderRow] instead.
 */
@Composable
internal fun ChoiceChipsRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .plateFill(focused = false)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) RowIcon(icon, focused = false)
            RowTexts(title, subtitle, danger = false, focused = false, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                RiftChip(
                    text = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Material 3 Expressive toggle button: the chosen option is a full pill and the others are softer rounded
 * rectangles, and the shape springs between them when the choice changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun RiftChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** One line that slowly scrolls when it does not fit (a long game name), instead of a second line. */
    marquee: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, RiftMotion.focusSpring(), label = "chipScale")
    val accent = RiftAccent.current
    // Two states, like every plate: focused is filled with the accent behind a see-through button (so a
    // gradient shows) with dark text, anything else is grey. The chosen option is told apart by an accent dot.
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = ToggleButtonShapes(
            shape = RiftShapes.full,
            pressedShape = RiftShapes.full,
            checkedShape = RiftShapes.full,
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = if (focused) Color.Transparent else RiftColors.Surface,
            contentColor = if (focused) accent.onAccent else RiftColors.OnSurfaceVariant,
            checkedContainerColor = if (focused) Color.Transparent else RiftColors.SurfaceContainerHigh,
            checkedContentColor = if (focused) accent.onAccent else RiftColors.OnSurface,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = RiftSizes.button)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focused) Modifier.clip(RiftShapes.full).background(accent.brush) else Modifier),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (focused) SolidColor(accent.onAccent) else accent.brush),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            // A long answer takes a second line, never an ellipsis.
            maxLines = if (marquee) 1 else 2,
            textAlign = TextAlign.Center,
            modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
        )
    }
}

/** The fill of a text field while it has focus: a lighter grey than at rest, never the accent. */
internal val FieldFocusedFill = Color(0xFF333333)

/** Text, placeholder and cursor colours of a text field on a [RiftInputPlate]: light text, accent cursor. */
internal val FieldText: Color get() = RiftColors.OnSurface
internal val FieldHint: Color get() = RiftColors.OnSurfaceVariant

/**
 * The plate a text field sits on. A field is never filled with the accent (Denis: the colour goes on the button,
 * not in the field): dark grey at rest, a lighter grey while it has focus, light text and an accent cursor
 * ([fieldCursor]). No outline.
 */
@Composable
internal fun RiftInputPlate(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.hasFocus }
            .fillMaxWidth()
            .heightIn(min = RiftSizes.button)
            .clip(RiftShapes.full)
            .background(if (focused) FieldFocusedFill else RiftColors.SurfaceContainerHigh)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** The cursor of every text field: the accent colour (a gradient accent shows as its main colour). */
@Composable
internal fun fieldCursor(): SolidColor = SolidColor(RiftAccent.current.main)

/**
 * A Material 3 Expressive button for dialogs and calls to action. Primary (Save, OK) is filled with the accent
 * with dark text; the others (Cancel) are grey until they get focus.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RiftPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, RiftMotion.focusSpring(), label = "pillScale")
    val accent = RiftAccent.current
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(
            shape = RiftShapes.full,
            pressedShape = RiftShapes.full,
        ),
        colors = ButtonDefaults.buttonColors(
            // Rift: only the button with the focus is filled with the accent; every other one, the dialog's main one
            // too, is grey, so it is always clear which one A presses (two filled buttons read as two cursors).
            containerColor = when {
                focused -> Color.Transparent
                else -> RiftColors.SurfaceContainerHigh
            },
            contentColor = when {
                focused -> accent.onAccent
                else -> RiftColors.OnSurface
            },
            disabledContainerColor = RiftColors.SurfaceContainerHigh,
            disabledContentColor = RiftColors.OnSurfaceVariant.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = 26.dp),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = RiftSizes.button)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                when {
                    !enabled -> Modifier
                    focused -> Modifier.clip(RiftShapes.full).background(accent.brush)
                    else -> Modifier
                },
            ),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** The round icon button: the same size everywhere in Rift. */
@Composable
internal fun RiftRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = RiftSizes.button,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(size)
            .focusChrome(
                focused = focused,
                shape = CircleShape,
                restColor = RiftColors.SurfaceContainerHigh,
                scaleOnFocus = 1.06f,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = plateText(focused))
    }
}

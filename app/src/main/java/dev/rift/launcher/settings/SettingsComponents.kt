package dev.rift.launcher.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
private val RowIconSize = 44.dp

/** The fill of a plate: the accent colour (or gradient) while it has focus, otherwise [rest]. */
@Composable
internal fun Modifier.plateFill(focused: Boolean, shape: Shape = PlateShape, rest: Color = RiftColors.SurfaceContainer): Modifier =
    clip(shape).then(
        if (focused) Modifier.background(RiftAccent.current.brush) else Modifier.background(rest),
    )

/** Main text colour on a plate. */
internal fun plateText(focused: Boolean): Color = if (focused) RiftAccent.current.onAccent else RiftColors.OnSurface

/** Secondary text colour on a plate. */
internal fun plateSubtext(focused: Boolean): Color =
    if (focused) RiftAccent.current.onAccent.copy(alpha = 0.72f) else RiftColors.OnSurfaceVariant

/**
 * A group of settings rows under a small caption, one plate per row.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) {
            // Same small caption as the group names in the section list.
            Text(
                text = title.uppercase(),
                color = RiftColors.OnSurfaceVariant,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 18.dp),
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
            fontSize = 18.5.sp,
            fontWeight = FontWeight.SemiBold,
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
    leading: (@Composable () -> Unit)? = null,
    iconPainter: Painter? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .plateFill(focused)
            .then(interactive)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = RiftAccent.current
    // On the accent plate the box turns dark, so it still reads.
    val on = if (focused) accent.onAccent else accent.main
    RowChrome(
        title = title,
        subtitle = subtitle,
        icon = icon,
        focused = focused,
        enabled = true,
        danger = false,
        modifier = modifier,
        interactive = Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = on,
                uncheckedColor = if (focused) accent.onAccent.copy(alpha = 0.6f) else RiftColors.Outline,
                checkmarkColor = if (focused) accent.main else accent.onAccent,
            ),
        )
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
 * Material 3 Expressive switch: the thumb grows and carries a check mark when on. Accent outline, thumb and
 * a faint tint when on, a grey outline when off, so a switch stays a small accent on black.
 */
@Composable
internal fun RiftSwitch(checked: Boolean, focused: Boolean) {
    val accent = RiftAccent.current
    // On the accent plate the switch turns dark, so it still reads.
    val on = if (focused) accent.onAccent else accent.main
    Switch(
        checked = checked,
        onCheckedChange = null,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = on,
            checkedTrackColor = on.copy(alpha = if (focused) 0.22f else 0.26f),
            checkedBorderColor = on,
            checkedIconColor = if (focused) accent.main else accent.onAccent,
            uncheckedThumbColor = if (focused) accent.onAccent.copy(alpha = 0.6f) else RiftColors.Outline,
            uncheckedTrackColor = if (focused) Color.Transparent else RiftColors.SurfaceContainerHigh,
            uncheckedBorderColor = if (focused) accent.onAccent.copy(alpha = 0.6f) else RiftColors.Outline,
            uncheckedIconColor = if (focused) accent.main else RiftColors.SurfaceContainerHigh,
        ),
    )
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    RowChrome(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconPainter = iconPainter,
        focused = focused,
        enabled = enabled,
        danger = danger,
        modifier = modifier,
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
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                color = plateSubtext(focused),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = plateSubtext(focused),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A row that only shows information, such as the app version. */
@Composable
internal fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
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
    }
}

/** A plain explanation under a group. */
@Composable
internal fun NoteText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = RiftColors.OnSurfaceVariant,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        modifier = modifier.padding(horizontal = 12.dp),
    )
}

/**
 * A row whose answer is one of a few options shown as chips under the title. Left and right move
 * between chips, so a short list needs no dialog.
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RiftChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, RiftMotion.focusSpring(), label = "chipScale")
    val accent = RiftAccent.current
    // The focused chip is filled with the accent behind a see-through button, so a gradient shows.
    OutlinedToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = ToggleButtonShapes(
            shape = RiftShapes.full,
            pressedShape = RiftShapes.full,
            checkedShape = RiftShapes.full,
        ),
        colors = ToggleButtonDefaults.outlinedToggleButtonColors(
            containerColor = if (focused) Color.Transparent else RiftColors.Surface,
            contentColor = if (focused) accent.onAccent else RiftColors.OnSurfaceVariant,
            checkedContainerColor = if (focused) Color.Transparent else RiftColors.SurfaceContainerHigh,
            checkedContentColor = if (focused) accent.onAccent else RiftColors.OnSurface,
        ),
        border = BorderStroke(
            width = if (focused) 0.dp else if (selected) 1.5.dp else 1.dp,
            color = when {
                focused -> Color.Transparent
                selected -> accent.main
                else -> RiftColors.OutlineVariant
            },
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
        Text(
            text = text,
            fontSize = 14.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A Material 3 Expressive button for dialogs and calls to action. Primary is white with black text, like Play
 * on the home screen; its shape springs to a softer rectangle while pressed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RiftPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, RiftMotion.focusSpring(), label = "pillScale")
    val accent = RiftAccent.current
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(
            shape = RiftShapes.full,
            pressedShape = RiftShapes.full,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                focused -> Color.Transparent
                primary -> Color.White
                else -> RiftColors.SurfaceContainerHigh
            },
            contentColor = when {
                focused -> accent.onAccent
                primary -> RiftColors.OnPrimary
                else -> RiftColors.OnSurface
            },
        ),
        contentPadding = PaddingValues(horizontal = 26.dp),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = RiftSizes.button)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focused) Modifier.clip(RiftShapes.full).background(accent.brush) else Modifier),
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(RiftSizes.button)
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

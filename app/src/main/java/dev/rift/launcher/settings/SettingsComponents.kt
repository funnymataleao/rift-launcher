package dev.rift.launcher.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

/*
 * Building blocks of Rift's settings: a group of rows (a titled column of cards), switch, link, choice
 * chips and info rows, plus the round and pill buttons dialogs use. Everything is one size and shape
 * language with the home screen: a white ring and a small spring scale on focus, round buttons, and
 * the accent colour only on what is on or selected.
 */

private val RowShape = RiftShapes.sheet
private val RowMinHeight = 84.dp
private val RowIconSize = 44.dp

/**
 * A titled card of settings rows, like a group in macOS System Settings: a large heading, then rows in
 * one rounded card separated by hairlines.
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
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .background(RiftColors.OutlineVariant)
                .border(1.dp, RiftColors.OutlineVariant, RowShape),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            content()
        }
    }
}

/** Standard colours and ring for anything that can take focus. */
@Composable
private fun Modifier.focusChrome(
    focused: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    restColor: Color = RiftColors.SurfaceContainer,
    focusedColor: Color = RiftColors.SurfaceContainerHigh,
    scaleOnFocus: Float = 1.02f,
): Modifier {
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, RiftMotion.focusSpring(), label = "settingsFocusScale")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(if (focused) focusedColor else restColor)
        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White.copy(alpha = 0.9f) else RiftColors.OutlineVariant, shape)
}

@Composable
private fun RowIcon(icon: ImageVector, focused: Boolean, tint: Color = RiftColors.OnSurface) {
    Box(
        modifier = Modifier
            .size(RowIconSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (focused) 0.16f else 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, danger: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            color = if (danger) RiftColors.Error else RiftColors.OnSurface,
            fontSize = 18.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = RiftColors.OnSurfaceVariant,
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
    trailing: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (focused) RiftColors.SurfaceContainerHigh else RiftColors.SurfaceContainer)
            .then(interactive)
            .heightIn(min = RowMinHeight),
    ) {
        // The focus ring sits inside the row, away from the card's rounded corners, so the card's mask never
        // cuts it and it stays a thin, whole outline.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (leading != null) {
                leading()
            } else if (icon != null) {
                RowIcon(icon, focused, tint = if (danger) RiftColors.Error else RiftColors.OnSurface)
            }
            RowTexts(title, subtitle, danger, Modifier.weight(1f))
            trailing()
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
        RiftSwitch(checked)
    }
}

/**
 * Material 3 Expressive switch: the thumb grows and carries a check mark when on. Lemon outline, thumb and
 * a faint tint when on, a grey outline when off, so a switch stays a small accent on black.
 */
@Composable
private fun RiftSwitch(checked: Boolean) {
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
            checkedThumbColor = RiftColors.Primary,
            checkedTrackColor = RiftColors.Primary.copy(alpha = 0.26f),
            checkedBorderColor = RiftColors.Primary,
            checkedIconColor = RiftColors.OnPrimary,
            uncheckedThumbColor = RiftColors.Outline,
            uncheckedTrackColor = RiftColors.SurfaceContainerHigh,
            uncheckedBorderColor = RiftColors.Outline,
            uncheckedIconColor = RiftColors.SurfaceContainerHigh,
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
                color = if (focused) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
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
            tint = RiftColors.OnSurfaceVariant,
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
            .background(RiftColors.SurfaceContainer)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) RowIcon(icon, focused = false)
        RowTexts(title, null, danger = false, modifier = Modifier.weight(1f))
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
            .background(RiftColors.SurfaceContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) RowIcon(icon, focused = false)
            RowTexts(title, subtitle, danger = false, modifier = Modifier.weight(1f))
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
    OutlinedToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = ToggleButtonShapes(
            shape = RoundedCornerShape(16.dp),
            pressedShape = RoundedCornerShape(10.dp),
            checkedShape = RoundedCornerShape(26.dp),
        ),
        colors = ToggleButtonDefaults.outlinedToggleButtonColors(
            containerColor = RiftColors.Surface,
            contentColor = RiftColors.OnSurfaceVariant,
            checkedContainerColor = RiftColors.SurfaceContainerHigh,
            checkedContentColor = RiftColors.OnSurface,
        ),
        border = BorderStroke(
            width = if (focused) 2.dp else if (selected) 1.5.dp else 1.dp,
            color = when {
                focused -> Color.White
                selected -> RiftColors.Primary
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
            },
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
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(
            shape = RiftShapes.full,
            pressedShape = RoundedCornerShape(14.dp),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color.White else RiftColors.SurfaceContainerHigh,
            contentColor = if (primary) RiftColors.OnPrimary else RiftColors.OnSurface,
        ),
        border = if (focused) BorderStroke(2.dp, if (primary) RiftColors.Primary else Color.White.copy(alpha = 0.9f)) else null,
        contentPadding = PaddingValues(horizontal = 26.dp),
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = RiftSizes.button)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
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
                restColor = RiftColors.Surface.copy(alpha = 0.7f),
                scaleOnFocus = 1.06f,
            )
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = RiftColors.OnSurface)
    }
}

/** A circular badge for a section: a lemon glyph on a dark disc with a thin lemon ring. */
@Composable
internal fun SectionBadge(icon: ImageVector, size: Dp = RiftSizes.button) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(RiftColors.SurfaceContainer)
            .border(1.dp, RiftColors.OutlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RiftColors.OnSurface,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}


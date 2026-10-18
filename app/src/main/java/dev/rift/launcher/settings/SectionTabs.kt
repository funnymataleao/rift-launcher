package dev.rift.launcher.settings

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.gamenative.R
import dev.rift.launcher.home.RiftSection
import dev.rift.launcher.home.RiftTabPrefs
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import kotlin.math.roundToInt

private val TabPlateHeight = RowMinHeight

/**
 * The tabs along the top of the home screen, like the platform list in Beacon Game Launcher. Each tab is a
 * plate of its own: drag it to reorder (a finger, or A / arrows / A on a gamepad), the eye hides it and the
 * pencil renames it. A tab still appears only once its source is set up.
 */
@Composable
internal fun TabsSettings() {
    val context = LocalContext.current
    var order by remember { mutableStateOf(RiftTabPrefs.order(context)) }
    var hidden by remember { mutableStateOf(RiftTabPrefs.hidden(context)) }
    var names by remember { mutableStateOf(RiftTabPrefs.names(context)) }
    var renaming by remember { mutableStateOf<RiftSection?>(null) }

    // A tab being dragged with a finger, and one picked up with the gamepad (with the order to go back to).
    var dragging by remember { mutableStateOf<RiftSection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var grabbed by remember { mutableStateOf<RiftSection?>(null) }
    var orderBeforeGrab by remember { mutableStateOf<List<RiftSection>?>(null) }
    val density = LocalDensity.current
    val step = with(density) { (TabPlateHeight + PlateGap).toPx() }
    val stackHeight = with(density) { (step * order.size).toDp() } - PlateGap

    /** Puts [section] at index [to]; the new order is saved at once, so the home screen follows. */
    fun moveTo(section: RiftSection, to: Int) {
        val from = order.indexOf(section)
        val target = to.coerceIn(0, order.lastIndex)
        if (from < 0 || from == target) return
        order = order.toMutableList().apply { add(target, removeAt(from)) }
        RiftTabPrefs.setOrder(context, order)
    }

    fun drag(section: RiftSection, dy: Float) {
        dragOffset += dy
        val from = order.indexOf(section)
        val target = ((from * step + dragOffset) / step).roundToInt().coerceIn(0, order.lastIndex)
        if (target != from) {
            moveTo(section, target)
            // The plate keeps following the finger after the others have shifted.
            dragOffset -= (target - from) * step
        }
    }

    fun release() {
        grabbed = null
        orderBeforeGrab = null
    }

    fun setHidden(section: RiftSection, hide: Boolean) {
        hidden = if (hide) hidden + section.key else hidden - section.key
        RiftTabPrefs.setHidden(context, hidden)
    }

    fun rename(section: RiftSection, name: String) {
        RiftTabPrefs.setName(context, section, name)
        names = RiftTabPrefs.names(context)
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_tabs_group)) {
        Box(modifier = Modifier.fillMaxWidth().height(stackHeight)) {
            order.forEachIndexed { index, section ->
                key(section) {
                    val isDragging = dragging == section
                    val rest by animateFloatAsState(index * step, RiftMotion.noBounce(), label = "tabSlot")
                    val y = if (isDragging) (index * step + dragOffset).coerceIn(0f, (order.lastIndex) * step) else rest
                    val defaultName = stringResource(section.titleRes)
                    val custom = names[section.key]
                    val isHidden = section.key in hidden
                    // The last tab left showing cannot be hidden, so the home screen never ends up without tabs.
                    val canHide = isHidden || order.count { it.key !in hidden } > 1
                    TabPlate(
                        title = custom ?: defaultName,
                        subtitle = when {
                            grabbed == section -> stringResource(R.string.rift_settings_tab_grabbed)
                            custom != null -> "${stringResource(if (isHidden) R.string.rift_settings_tab_hidden else R.string.rift_settings_tab_shown)} · $defaultName"
                            else -> stringResource(if (isHidden) R.string.rift_settings_tab_hidden else R.string.rift_settings_tab_shown)
                        },
                        icon = section.icon(),
                        dimmed = isHidden,
                        lifted = isDragging || grabbed == section,
                        canHide = canHide,
                        onToggleHidden = { setHidden(section, !isHidden) },
                        onRename = { renaming = section },
                        onDragStart = {
                            dragging = section
                            dragOffset = 0f
                        },
                        onDrag = { drag(section, it) },
                        onDragEnd = {
                            dragging = null
                            dragOffset = 0f
                        },
                        onToggleGrab = {
                            if (grabbed == section) {
                                release()
                            } else {
                                grabbed = section
                                orderBeforeGrab = order
                            }
                        },
                        onKey = { code ->
                            when {
                                grabbed != section -> false
                                code == KeyEvent.KEYCODE_DPAD_UP -> {
                                    moveTo(section, order.indexOf(section) - 1)
                                    true
                                }
                                code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    moveTo(section, order.indexOf(section) + 1)
                                    true
                                }
                                code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BACK -> {
                                    orderBeforeGrab?.let {
                                        order = it
                                        RiftTabPrefs.setOrder(context, it)
                                    }
                                    release()
                                    true
                                }
                                else -> false
                            }
                        },
                        onFocusLost = { if (grabbed == section) release() },
                        modifier = Modifier
                            .offset { IntOffset(0, y.roundToInt()) }
                            .zIndex(if (isDragging || grabbed == section) 1f else 0f),
                    )
                }
            }
        }
    }
    NoteText(stringResource(R.string.rift_settings_tabs_note), underGroup = true)
    NoteText(stringResource(R.string.rift_settings_tabs_hint), underGroup = true)

    renaming?.let { section ->
        RenameTabDialog(
            initial = names[section.key] ?: stringResource(section.titleRes),
            onSave = {
                rename(section, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun TabPlate(
    title: String,
    subtitle: String,
    icon: ImageVector,
    dimmed: Boolean,
    lifted: Boolean,
    canHide: Boolean,
    onToggleHidden: () -> Unit,
    onRename: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleGrab: () -> Unit,
    onKey: (Int) -> Boolean,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val plateFocused by interaction.collectIsFocusedAsState()
    // The plate is filled while it has focus or is being carried.
    val filled = plateFocused || lifted
    val scale by animateFloatAsState(if (lifted) 1.03f else 1f, RiftMotion.focusSpring(), label = "tabLift")
    val buttonColors = iconColors(filled)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TabPlateHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .plateFill(filled)
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && onKey(event.key.nativeKeyCode)
            }
            .onFocusChanged { if (!it.hasFocus) onFocusLost() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggleGrab)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowIcon(icon, filled)
        RowTexts(title, subtitle, danger = false, focused = filled, modifier = Modifier.weight(1f).alpha(if (dimmed) 0.5f else 1f))
        PlateIconButton(
            icon = if (dimmed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            description = stringResource(if (dimmed) R.string.rift_settings_tab_show else R.string.rift_settings_tab_hide),
            enabled = canHide,
            onClick = onToggleHidden,
            plateFilled = filled,
        )
        PlateIconButton(
            icon = Icons.Rounded.Edit,
            description = stringResource(R.string.rift_settings_tab_rename),
            onClick = onRename,
            plateFilled = filled,
        )
        // The handle drags at once; the rest of the plate needs a long press, so scrolling still works.
        Box(
            modifier = Modifier
                .size(44.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.rift_settings_tab_drag),
                tint = buttonColors.symbol,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** A small round button on a plate: faint at rest, filled with the accent while it has focus itself. */
@Composable
private fun PlateIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    plateFilled: Boolean,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = iconColors(focused || plateFilled)
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .then(if (focused) Modifier.background(RiftAccent.current.brush) else Modifier.background(colors.disc))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (focused) RiftAccent.current.onAccent else colors.symbol,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun RenameTabDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_settings_tab_rename),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_settings_tab_save), onClick = { onSave(text) }, primary = true)
        },
    ) {
        RiftInputPlate { focused ->
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(RiftTabPrefs.MAX_NAME_LENGTH) },
                singleLine = true,
                textStyle = TextStyle(color = plateText(focused), fontSize = 17.sp),
                cursorBrush = SolidColor(plateText(focused)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        NoteText(stringResource(R.string.rift_settings_tab_rename_hint))
    }
}

private fun RiftSection.icon(): ImageVector = when (this) {
    RiftSection.PcGames -> Icons.Rounded.DesktopWindows
    RiftSection.AndroidGames -> Icons.Rounded.Android
    RiftSection.PcStreaming -> Icons.Rounded.Cast
    RiftSection.Emulators -> Icons.Rounded.SportsEsports
    RiftSection.CloudStreaming -> Icons.Rounded.Cloud
}

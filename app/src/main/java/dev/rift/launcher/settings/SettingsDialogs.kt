package dev.rift.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes

/** Rift's own modal: a dark card with a title, content and pill buttons. Back (B) dismisses. */
@Composable
internal fun RiftDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .widthIn(min = 420.dp, max = 620.dp)
                .padding(24.dp)
                .clip(RiftShapes.sheet)
                .background(RiftColors.Surface.copy(alpha = 0.92f))
                .border(1.dp, RiftColors.OutlineVariant, RiftShapes.sheet)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text = title, color = RiftColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions() }
        }
    }
}

/** Confirm or cancel. Cancel takes the first focus so a stray press does not confirm. */
@Composable
internal fun RiftConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RiftDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            RiftPillButton(text = dismissText, onClick = onDismiss, modifier = Modifier.focusRequester(first))
            RiftPillButton(text = confirmText, onClick = onConfirm, primary = true)
        },
    ) {
        Text(text = message, color = RiftColors.OnSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

/** A short list of options, one of which is chosen. Choosing closes the dialog. */
@Composable
internal fun RiftChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    RiftDialog(title = title, onDismiss = onDismiss) {
        val initial = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { initial.requestFocus() } }
        Column(
            modifier = Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, label ->
                ChoiceOptionRow(
                    label = label,
                    selected = index == selectedIndex,
                    multi = false,
                    onClick = {
                        onSelect(index)
                        onDismiss()
                    },
                    modifier = if (index == selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))) {
                        Modifier.focusRequester(initial)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** A list where any number of options can be on. Done closes it. */
@Composable
internal fun RiftMultiChoiceDialog(
    title: String,
    options: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    doneText: String,
    onDismiss: () -> Unit,
) {
    RiftDialog(
        title = title,
        onDismiss = onDismiss,
        actions = { RiftPillButton(text = doneText, onClick = onDismiss, primary = true) },
    ) {
        val initial = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { initial.requestFocus() } }
        Column(
            modifier = Modifier
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, label ->
                ChoiceOptionRow(
                    label = label,
                    selected = index in selected,
                    multi = true,
                    onClick = { onToggle(index) },
                    modifier = if (index == 0) Modifier.focusRequester(initial) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ChoiceOptionRow(
    label: String,
    selected: Boolean,
    multi: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RiftShapes.full
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RiftSizes.button)
            .clip(shape)
            .background(if (focused) RiftColors.SurfaceContainerHigh else RiftColors.SurfaceContainer)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White.copy(alpha = 0.9f) else RiftColors.OutlineVariant, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = if (multi) Role.Checkbox else Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) RiftColors.Primary else Color.Transparent)
                .border(2.dp, if (selected) RiftColors.Primary else RiftColors.Outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = RiftColors.OnPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = label,
            color = RiftColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Blocks the screen with Material 3 Expressive's morphing loading indicator and a message, for example while the app restarts. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RiftBusyDialog(message: String) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Row(
            modifier = Modifier
                .clip(RiftShapes.sheet)
                .background(RiftColors.Surface)
                .border(1.dp, RiftColors.OutlineVariant, RiftShapes.sheet)
                .padding(horizontal = 32.dp, vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LoadingIndicator(color = RiftColors.Primary, modifier = Modifier.size(48.dp))
            Text(text = message, color = RiftColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

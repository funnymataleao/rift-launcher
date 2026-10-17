package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import app.gamenative.ui.screen.library.components.getIconForOption
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay

/** One line of the Start menu; [chevron] marks a line that opens a further list. */
class RiftStartMenuItem(
    val label: String,
    val chevron: Boolean = false,
    val destructive: Boolean = false,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
)

/**
 * Start on a game: a small black-glass menu in the middle of the screen, Steam Big Picture style.
 * The screen behind is blurred and dimmed by the home (see overlayOpen there); the line under the
 * cursor is filled with the user's accent. Each tab passes its own short list; [sub] is an optional
 * second list ("Properties…") that replaces the first and goes back with B.
 * The item clicks close the menu themselves through [onDismiss] when they should.
 */
@Composable
fun RiftStartMenu(
    isOpen: Boolean,
    items: List<RiftStartMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subTitle: String? = null,
    sub: List<RiftStartMenuItem>? = null,
    // A new key means a new list in place: the cursor goes back to its first line.
    listKey: Any? = null,
) {
    var inSub by remember { mutableStateOf(false) }
    LaunchedEffect(isOpen) { if (!isOpen) inSub = false }
    val shown = if (inSub && sub != null) sub else items
    fun back() { if (inSub) inSub = false else onDismiss() }
    BackHandler(enabled = isOpen) { back() }

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // A little darker under the menu, on top of the blur.
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { back() } },
            contentAlignment = Alignment.Center,
        ) {
            val view = LocalView.current
            val first = remember(inSub, listKey) { FocusRequester() }
            LaunchedEffect(inSub, listKey) {
                // After a touch Compose gives no focus in touch mode: ask for it, then once more.
                if (view.isInTouchMode) view.requestFocusFromTouch()
                runCatching { first.requestFocus() }
                delay(120)
                runCatching { first.requestFocus() }
            }
            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .heightIn(max = 520.dp)
                    .clip(shape)
                    .blackGlass(shape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .onPreviewKeyEvent { event ->
                        val code = event.nativeKeyEvent.keyCode
                        if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                            if (event.type == KeyEventType.KeyDown) back()
                            true
                        } else {
                            false
                        }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (inSub && subTitle != null) {
                    Text(
                        text = subTitle,
                        color = RiftColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                shown.forEachIndexed { index, item ->
                    StartMenuRow(
                        item = item,
                        onClick = {
                            if (item.chevron && sub != null && !inSub) inSub = true else item.onClick()
                        },
                        focusRequester = if (index == 0) first else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun StartMenuRow(item: RiftStartMenuItem, onClick: () -> Unit, focusRequester: FocusRequester?) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val text = when {
        focused -> RiftColors.OnPrimary
        item.destructive -> RiftColors.Error
        else -> RiftColors.OnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) RiftColors.Primary else Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .selectable(selected = focused, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        if (item.icon != null) {
            Icon(item.icon, contentDescription = null, tint = text, modifier = Modifier.size(24.dp))
        }
        Text(
            text = item.label,
            color = text,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (item.chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = text)
        }
    }
}

/**
 * The PC games Start menu: the few lines people use ([onPlay] is null when the game is not
 * installed), and "Properties…" ([onProperties]) for the game's full properties page.
 */
@Composable
fun RiftGameStartMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    options: List<AppMenuOption>,
    onPlay: (() -> Unit)?,
    onProperties: (() -> Unit)?,
    onHide: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val main = listOf(
        AppOptionMenuType.ChangeArt,
        AppOptionMenuType.RiftOptimize,
        AppOptionMenuType.AddToFavorites,
        AppOptionMenuType.RemoveFromFavorites,
    ).mapNotNull { type -> options.firstOrNull { it.optionType == type } }
    val items = buildList {
        if (onPlay != null) {
            add(RiftStartMenuItem(stringResource(R.string.rift_menu_play), icon = Icons.Default.PlayArrow) { onPlay(); onDismiss() })
        }
        main.forEach { option ->
            add(RiftStartMenuItem(stringResource(option.optionType.title), icon = getIconForOption(option.optionType)) { option.onClick(); onDismiss() })
        }
        if (onHide != null) {
            add(RiftStartMenuItem(stringResource(R.string.rift_menu_hide), icon = Icons.Default.VisibilityOff) { onHide(); onDismiss() })
        }
        if (onProperties != null) {
            add(RiftStartMenuItem(stringResource(R.string.rift_menu_properties), icon = Icons.Default.Settings) { onProperties(); onDismiss() })
        }
        add(RiftStartMenuItem(stringResource(R.string.rift_menu_cancel), icon = Icons.Default.Close) { onDismiss() })
    }
    RiftStartMenu(isOpen = isOpen, items = items, onDismiss = onDismiss, modifier = modifier)
}

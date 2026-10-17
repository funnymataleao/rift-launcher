package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.library.components.OptionCategory
import app.gamenative.ui.screen.library.components.getIconForOption
import app.gamenative.ui.screen.library.components.groupOptions
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay

/**
 * The game's properties page, Steam style: the groups of GameNative's options (container, game
 * management, cloud saves, help) in a list on the left, the options of the group under the cursor
 * on the right. It reuses the same options and their dialogs as the old side panel, which it
 * replaces; Start's menu and the settings icon on the game page both open it. B closes it.
 */
@Composable
fun RiftGameProperties(
    isOpen: Boolean,
    title: String,
    options: List<AppMenuOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = isOpen) { onDismiss() }
    val groups = groupOptions(options).filterValues { it.isNotEmpty() }.toList()
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(120)),
        modifier = modifier,
    ) {
        var selected by remember { mutableStateOf(groups.firstOrNull()?.first) }
        val first = remember { FocusRequester() }
        val view = LocalView.current
        LaunchedEffect(Unit) {
            if (view.isInTouchMode) view.requestFocusFromTouch()
            runCatching { first.requestFocus() }
            delay(120)
            runCatching { first.requestFocus() }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) { detectTapGestures { } }
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                        if (event.type == KeyEventType.KeyDown) onDismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(Color.Black)
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = RiftColors.OnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = stringResource(R.string.rift_menu_properties_title),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
                groups.forEachIndexed { index, (category, _) ->
                    PropertiesRow(
                        label = categoryLabel(category),
                        onClick = { selected = category },
                        onFocused = { selected = category },
                        focusRequester = if (index == 0) first else null,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                groups.firstOrNull { it.first == selected }?.second.orEmpty().forEach { option ->
                    val destructive = option.optionType == AppOptionMenuType.Uninstall ||
                        option.optionType == AppOptionMenuType.ResetToDefaults ||
                        option.optionType == AppOptionMenuType.ResetDrm
                    PropertiesRow(
                        label = stringResource(option.optionType.title),
                        icon = option.optionType,
                        destructive = destructive,
                        onClick = { option.onClick(); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: OptionCategory): String = stringResource(
    when (category) {
        OptionCategory.QUICK_ACTIONS -> R.string.game_options_quick_actions
        OptionCategory.GAME_MANAGEMENT -> R.string.game_options_game_management
        OptionCategory.CONTAINER -> R.string.game_options_container
        OptionCategory.CLOUD_SAVES -> R.string.game_options_cloud_saves
        OptionCategory.HELP_INFO -> R.string.game_options_help_info
    },
)

@Composable
private fun PropertiesRow(
    label: String,
    onClick: () -> Unit,
    icon: AppOptionMenuType? = null,
    destructive: Boolean = false,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val color = when {
        focused -> RiftColors.OnPrimary
        destructive -> RiftColors.Error
        else -> RiftColors.OnSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) RiftColors.Primary else Color.Transparent)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .selectable(selected = focused, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(getIconForOption(icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            color = color,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

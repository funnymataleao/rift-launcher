package dev.rift.launcher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.library.components.OptionCategory
import app.gamenative.ui.screen.library.components.getIconForOption
import app.gamenative.ui.screen.library.components.groupOptions
import dev.rift.launcher.settings.LinkRow
import dev.rift.launcher.settings.RiftSidebarScreen
import dev.rift.launcher.settings.SettingsGroup
import dev.rift.launcher.settings.SidebarEntry

/**
 * The game's properties page, built from the same parts as Rift's Settings (one frame, the list of groups on
 * the left, a page of plates on the right, an opaque black screen): the groups of GameNative's options
 * (container, game management, cloud saves, help) and their options. It reuses the same options and their
 * dialogs as the old side panel; Start's menu and the settings icon on the game page both open it. B goes from
 * the page to the list, and from the list out.
 */
@Composable
fun RiftGameProperties(
    isOpen: Boolean,
    title: String,
    options: List<AppMenuOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = groupOptions(options).filterValues { it.isNotEmpty() }.toList()
    AnimatedVisibility(
        visible = isOpen && groups.isNotEmpty(),
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(100)),
        modifier = modifier,
    ) {
        var selected by remember { mutableIntStateOf(0) }
        val index = selected.coerceIn(0, groups.size - 1)
        val entries = groups.map { (category, _) -> SidebarEntry(categoryLabel(category), categoryIcon(category)) }
        RiftSidebarScreen(
            title = title,
            entries = entries,
            selectedIndex = index,
            onSelect = { selected = it },
            onBack = onDismiss,
        ) {
            val (category, items) = groups[index]
            SettingsGroup(title = categoryLabel(category)) {
                items.forEach { option ->
                    val type = option.optionType
                    LinkRow(
                        title = stringResource(type.title),
                        icon = getIconForOption(type),
                        danger = type in DESTRUCTIVE,
                        // An arrow only where another screen or dialog opens; a plain action has none.
                        chevron = type !in ACTIONS,
                        onClick = {
                            option.onClick()
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

private val DESTRUCTIVE = setOf(
    AppOptionMenuType.Uninstall,
    AppOptionMenuType.ResetToDefaults,
    AppOptionMenuType.ResetDrm,
)

/** Options that do their thing at once, without a screen or dialog of their own. */
private val ACTIONS = setOf(
    AppOptionMenuType.AddToFavorites,
    AppOptionMenuType.RemoveFromFavorites,
    AppOptionMenuType.CopyLaunchLink,
    AppOptionMenuType.CreateShortcut,
    AppOptionMenuType.RefreshMeta,
    AppOptionMenuType.RunContainer,
    AppOptionMenuType.ForceCloudSync,
    AppOptionMenuType.PlayWithDiagnostics,
)

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

private fun categoryIcon(category: OptionCategory): ImageVector = when (category) {
    OptionCategory.QUICK_ACTIONS -> Icons.Rounded.FlashOn
    OptionCategory.GAME_MANAGEMENT -> Icons.Rounded.Build
    OptionCategory.CONTAINER -> Icons.Rounded.Inventory2
    OptionCategory.CLOUD_SAVES -> Icons.Rounded.Cloud
    OptionCategory.HELP_INFO -> Icons.Rounded.Info
}

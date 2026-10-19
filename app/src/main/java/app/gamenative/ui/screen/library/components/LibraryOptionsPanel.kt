package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.SteamCollection
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.SortOption
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.adaptivePanelWidth
import dev.rift.launcher.settings.PlateGap
import dev.rift.launcher.settings.RiftCheckMark
import dev.rift.launcher.settings.RowIcon
import dev.rift.launcher.settings.RowIconSize
import dev.rift.launcher.settings.RowTexts
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.DrawerBackdrop
import dev.rift.launcher.ui.RiftDrawer
import dev.rift.launcher.ui.rememberDrawerAccordion
import dev.rift.launcher.ui.RiftDrawerPanel
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import java.util.EnumSet
import kotlinx.coroutines.delay

/**
 * The filters panel on the left of the home (Select), on every tab. It is built like Rift's settings and the
 * System panel on the right (Denis 2026-10-18): the same frame and width ([rememberHomePanelWidth]), one
 * group per line (icon, its name, the current choice under it), the group's choices unrolled right under it.
 * Focus is the accent fill with dark text, nothing has an outline.
 */
@Composable
fun LibraryOptionsPanel(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    selectedFilters: EnumSet<AppFilter>,
    onFilterChanged: (AppFilter) -> Unit,
    currentSortOption: SortOption,
    onSortOptionChanged: (SortOption) -> Unit,
    steamCollections: List<SteamCollection>?,
    selectedSteamCollectionIds: Set<String>,
    steamCollectionCounts: Map<String, Int>,
    skippedDynamicCollections: Boolean,
    isSteamConnected: Boolean,
    hasSteamCredentials: Boolean,
    isOffline: Boolean,
    onSteamCollectionToggle: (String) -> Unit,
    onClearSteamCollections: () -> Unit,
    curatedLists: List<SteamCollection>?,
    selectedCuratedListIds: Set<String>,
    curatedListCounts: Map<String, Int>,
    onCuratedListToggle: (String) -> Unit,
    onClearCuratedLists: () -> Unit,
    modifier: Modifier = Modifier,
    // Rift draws the panel as a flat, full height glass sheet without a close button (B closes it).
    panelShape: Shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
    panelShadowElevation: Dp = 24.dp,
    showCloseButton: Boolean = true,
    // Rift: a tab other than PC games has its own short list of groups instead of GameNative's.
    tabGroups: List<TabFilterGroup>? = null,
    extraGroup: TabFilterGroup? = null,
    // Rift: PC games' store filter (Steam, GOG, Epic...), right under the sort.
    storeGroup: TabFilterGroup? = null,
    // Rift: a counter the home bumps when gamepad input arrives while the panel has no focus, and a report of
    // whether the panel holds focus, so input never leaks to the covers under the blur.
    focusAsk: Int = 0,
    onFocusChanged: (Boolean) -> Unit = {},
    // Rift: the glass of every Rift panel, blurring the home behind it.
    glassBackdrop: DrawerBackdrop? = null,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    // Accordion: one group open at a time, except a tab with only a group or two, which opens with them all
    // unrolled (Denis 2026-10-19). Which groups are open is kept across openings and restarts (Denis 2026-10-19:
    // all closed stays closed); the defaults are only for the first time.
    val allOpen = tabGroups != null && tabGroups.size <= 2
    val tabKeys = tabGroups?.map { "tab:" + it.label }.orEmpty()
    var openGroups by rememberDrawerAccordion(
        key = if (tabGroups != null) "filters:" + tabKeys.joinToString("|") else "filters",
        default = if (allOpen) tabKeys.toSet() else emptySet(),
    )
    val headerFocus = remember { mutableMapOf("sort" to firstItemFocusRequester) }
    fun headerRequester(key: String) = headerFocus.getOrPut(key) { FocusRequester() }
    fun toggleGroup(key: String) {
        openGroups = when {
            key in openGroups -> openGroups - key
            allOpen -> openGroups + key
            else -> setOf(key)
        }
    }

    // B closes the panel; the open groups stay as they are for the next time.
    BackHandler(enabled = isOpen) { onDismiss() }

    // As wide as the System panel on the right.
    val panelWidth = adaptivePanelWidth(rememberHomePanelWidth())

    Box(modifier = modifier.fillMaxSize().onFocusChanged { onFocusChanged(it.hasFocus) }) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth },
                animationSpec = RiftDrawer.SlideIn
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = RiftDrawer.SlideOut
            )
        ) {
            RiftDrawerPanel(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .shadow(panelShadowElevation, panelShape)
                    .clip(panelShape),
                backdrop = glassBackdrop,
            ) {
                // The title as on the System panel.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.rift_filters_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showCloseButton) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.options_panel_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(PlateGap),
                ) {
                    if (tabGroups != null) {
                        tabGroups.forEachIndexed { index, group ->
                            val key = "tab:" + group.label
                            FilterGroup(
                                label = group.label,
                                value = group.value,
                                icon = group.icon,
                                expanded = key in openGroups,
                                onToggle = { toggleGroup(key) },
                                headerFocus = if (index == 0) firstItemFocusRequester else headerRequester(key),
                            ) {
                                group.options.forEach { option ->
                                    FilterOption(
                                        text = option.text,
                                        selected = option.selected,
                                        // Picking applies the filter and closes the panel; the home puts focus back on the row.
                                        onClick = {
                                            option.onPick()
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        FilterGroup(
                            label = stringResource(R.string.rift_filter_sort),
                            value = stringResource(currentSortOption.displayTextRes),
                            icon = currentSortOption.icon(),
                            expanded = "sort" in openGroups,
                            onToggle = { toggleGroup("sort") },
                            headerFocus = headerRequester("sort"),
                        ) {
                            SortOption.entries.forEach { option ->
                                FilterOption(
                                    text = stringResource(option.displayTextRes),
                                    selected = currentSortOption == option,
                                    onClick = {
                                        onSortOptionChanged(option)
                                        openGroups = openGroups - "sort"
                                        runCatching { headerRequester("sort").requestFocus() }
                                    },
                                )
                            }
                        }
                        if (storeGroup != null) {
                            FilterGroup(
                                label = storeGroup.label,
                                value = storeGroup.value,
                                icon = storeGroup.icon,
                                expanded = "store" in openGroups,
                                onToggle = { toggleGroup("store") },
                                headerFocus = headerRequester("store"),
                            ) {
                                storeGroup.options.forEach { option ->
                                    FilterOption(
                                        text = option.text,
                                        selected = option.selected,
                                        onClick = {
                                            option.onPick()
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }
                        FilterGroup(
                            label = stringResource(R.string.library_app_type),
                            value = summarize(TYPE_FILTERS, selectedFilters),
                            icon = Icons.Default.Category,
                            expanded = "type" in openGroups,
                            onToggle = { toggleGroup("type") },
                            headerFocus = headerRequester("type"),
                        ) {
                            TYPE_FILTERS.forEach { f ->
                                FilterOption(
                                    text = stringResource(f.displayTextRes),
                                    selected = selectedFilters.contains(f),
                                    multiple = true,
                                    onClick = { onFilterChanged(f) },
                                )
                            }
                        }
                        FilterGroup(
                            label = stringResource(R.string.library_app_status),
                            value = summarize(STATUS_FILTERS, selectedFilters),
                            icon = Icons.Default.FilterList,
                            expanded = "status" in openGroups,
                            onToggle = { toggleGroup("status") },
                            headerFocus = headerRequester("status"),
                        ) {
                            STATUS_FILTERS.forEach { f ->
                                FilterOption(
                                    text = stringResource(f.displayTextRes),
                                    selected = selectedFilters.contains(f),
                                    multiple = true,
                                    onClick = { onFilterChanged(f) },
                                )
                            }
                        }
                        if (extraGroup != null) {
                            FilterGroup(
                                label = extraGroup.label,
                                value = extraGroup.value,
                                icon = extraGroup.icon,
                                expanded = "extra" in openGroups,
                                onToggle = { toggleGroup("extra") },
                                headerFocus = headerRequester("extra"),
                            ) {
                                extraGroup.options.forEach { option ->
                                    FilterOption(
                                        text = option.text,
                                        selected = option.selected,
                                        onClick = {
                                            option.onPick()
                                            onDismiss()
                                        },
                                    )
                                }
                            }
                        }

                        // Steam collections — local view filter, shown only when Steam is connected.
                        if (isSteamConnected) {
                            CollectionFilterSection(
                                collections = steamCollections,
                                selectedIds = selectedSteamCollectionIds,
                                counts = steamCollectionCounts,
                                titleRes = R.string.steam_collections_title,
                                icon = Icons.Default.Folder,
                                loadingRes = R.string.steam_collections_loading,
                                clearRes = R.string.steam_collections_clear,
                                // No static collections to list, but still explain why (offline /
                                // only smart collections) so the section doesn't look broken.
                                messageRes = buildList {
                                    if (isOffline) add(R.string.steam_collections_offline)
                                    if (skippedDynamicCollections) {
                                        add(R.string.steam_collections_smart_unsupported)
                                    }
                                },
                                expanded = "steam" in openGroups,
                                onExpandedChange = { toggleGroup("steam") },
                                headerFocus = headerRequester("steam"),
                                onToggle = onSteamCollectionToggle,
                                onClear = onClearSteamCollections,
                            )
                        }
                    }
                }
            }
        }
    }

    // Rift: the curated lists are gone from the panel (Denis 2026-10-19: only one 4:3 list, nobody uses it); a
    // list picked before would filter the library with nothing left to unpick it.
    LaunchedEffect(selectedCuratedListIds.isNotEmpty()) {
        if (selectedCuratedListIds.isNotEmpty()) onClearCuratedLists()
    }
    // After a touch the window is in touch mode, where nothing takes gamepad focus: leave it, and retry while the
    // panel is still sliding in and the first header is not attached yet.
    val view = LocalView.current
    LaunchedEffect(isOpen, focusAsk) {
        if (!isOpen) return@LaunchedEffect
        repeat(15) {
            if (view.isInTouchMode) view.requestFocusFromTouch()
            if (runCatching { firstItemFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
}

@Composable
private fun CollectionFilterSection(
    collections: List<SteamCollection>?,
    selectedIds: Set<String>,
    counts: Map<String, Int>,
    @StringRes titleRes: Int,
    icon: ImageVector,
    @StringRes loadingRes: Int,
    @StringRes clearRes: Int,
    @StringRes emptyRes: Int? = null,
    messageRes: List<Int> = emptyList(),
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    headerFocus: FocusRequester,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    val chosen = collections?.filter { it.id in selectedIds }.orEmpty()
    val valueText = when (chosen.size) {
        0 -> stringResource(R.string.rift_filter_any)
        1 -> chosen.first().name
        else -> chosen.first().name + " +" + (chosen.size - 1)
    }
    FilterGroup(
        label = stringResource(titleRes),
        value = valueText,
        icon = icon,
        expanded = expanded,
        onToggle = { onExpandedChange(!expanded) },
        headerFocus = headerFocus,
    ) {
        when {
            collections == null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = OptionMinHeight)
                        .padding(horizontal = DrawerRowPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
                ) {
                    Box(modifier = Modifier.size(RowIconSize), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = RiftAccent.current.main,
                            strokeWidth = 2.5.dp,
                        )
                    }
                    RowTexts(stringResource(loadingRes), null, danger = false, focused = false, modifier = Modifier.weight(1f))
                }
            }
            collections.isEmpty() -> {
                val messages = messageRes.ifEmpty { listOfNotNull(emptyRes) }
                messages.forEach { message -> CollectionFilterMessage(message) }
            }
            else -> {
                if (selectedIds.isNotEmpty()) {
                    FilterOption(
                        text = stringResource(clearRes),
                        selected = false,
                        onClick = onClear,
                        icon = Icons.Default.Close,
                    )
                }
                collections.sortedBy { it.name.lowercase() }.forEach { collection ->
                    FilterOption(
                        text = collection.name,
                        selected = collection.id in selectedIds,
                        multiple = true,
                        onClick = { onToggle(collection.id) },
                        trailingText = counts[collection.id]?.toString(),
                    )
                }
                messageRes.forEach { message -> CollectionFilterMessage(message) }
            }
        }
    }
}

/** A note under a group's choices, on the same line as their names. */
@Composable
private fun CollectionFilterMessage(@StringRes messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        color = RiftColors.OnSurfaceVariant,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(start = OptionTextStart, end = DrawerRowPadding, top = 4.dp, bottom = 4.dp),
    )
}

private val TYPE_FILTERS = listOf(
    AppFilter.GAME,
    AppFilter.APPLICATION,
    AppFilter.TOOL,
    AppFilter.DEMO,
    AppFilter.PLAYTEST,
)

private val STATUS_FILTERS = listOf(
    AppFilter.INSTALLED,
    AppFilter.SHARED,
    AppFilter.COMPATIBLE,
    AppFilter.EXPIRED,
    AppFilter.PLAYABLE,
    AppFilter.FIVE_STAR,
    AppFilter.FIVE_STAR_GPU,
    AppFilter.PROVEN_GPU,
    AppFilter.VR,
)

/** One group of a non-PC tab's filters: its current value and the options (single choice). */
class TabFilterGroup(val label: String, val value: String, val icon: ImageVector, val options: List<TabFilterOption>)

class TabFilterOption(val text: String, val selected: Boolean, val onPick: () -> Unit)

@Composable
private fun summarize(options: List<AppFilter>, selected: EnumSet<AppFilter>): String {
    val chosen = options.filter { selected.contains(it) }
    return when (chosen.size) {
        0 -> stringResource(R.string.rift_filter_any)
        1 -> stringResource(chosen.first().displayTextRes)
        else -> stringResource(chosen.first().displayTextRes) + " +" + (chosen.size - 1)
    }
}

/** A choice row: shorter than a group's line. */
private val OptionMinHeight = DrawerChoiceMinHeight

/** Where a choice's name starts: under the group's name, the mark sits under the group's icon. */
private val OptionTextStart = DrawerRowPadding + RowIconSize + DrawerRowGap

/**
 * One filter group, a row like the System panel's: the group's icon, its name and the current choice under the
 * name. A unrolls the choices right under it; the caller keeps only one group open at a time.
 */
@Composable
private fun FilterGroup(
    label: String,
    value: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerFocus: FocusRequester,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .plateFill(isFocused)
                .focusRequester(headerFocus)
                .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onToggle)
                .heightIn(min = DrawerRowMinHeight)
                .padding(horizontal = DrawerRowPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DrawerRowGap),
        ) {
            RowIcon(icon, isFocused)
            RowTexts(label, value, danger = false, focused = isFocused, modifier = Modifier.weight(1f))
            DrawerChevron(expanded = expanded, focused = isFocused)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .padding(top = PlateGap),
                verticalArrangement = Arrangement.spacedBy(PlateGap),
                content = content,
            )
        }
    }
}

/** One choice of a group: a radio for a single choice, a check box where several can be picked, or [icon] for an action. */
@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    multiple: Boolean = false,
    icon: ImageVector? = null,
    trailingText: String? = null,
) = DrawerChoiceRow(text = text, selected = selected, onClick = onClick, multiple = multiple, icon = icon, trailingText = trailingText)

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1920px,height=1080px,dpi=440,orientation=landscape"
)
@Composable
private fun Preview_LibraryOptionsPanel() {
    val context = LocalContext.current
    PrefManager.init(context)
    PluviaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Game Library",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                LibraryOptionsPanel(
                    isOpen = true,
                    onDismiss = { },
                    selectedFilters = EnumSet.of(AppFilter.GAME),
                    onFilterChanged = { },
                    currentSortOption = SortOption.INSTALLED_FIRST,
                    onSortOptionChanged = { },
                    steamCollections = listOf(
                        SteamCollection(id = "fav", name = "Favorites", appIds = setOf(440, 570)),
                        SteamCollection(id = "rpg", name = "RPGs", appIds = setOf(292030)),
                    ),
                    selectedSteamCollectionIds = setOf("fav"),
                    steamCollectionCounts = mapOf("fav" to 2, "rpg" to 1),
                    skippedDynamicCollections = true,
                    isSteamConnected = true,
                    hasSteamCredentials = true,
                    isOffline = false,
                    onSteamCollectionToggle = { },
                    onClearSteamCollections = { },
                    curatedLists = listOf(
                        SteamCollection(id = "curated:4-3", name = "4:3 games", appIds = setOf(730, 240)),
                    ),
                    selectedCuratedListIds = emptySet(),
                    curatedListCounts = mapOf("curated:4-3" to 2),
                    onCuratedListToggle = { },
                    onClearCuratedLists = { },
                )
            }
        }
    }
}

private fun SortOption.icon(): ImageVector = when (this) {
    SortOption.INSTALLED_FIRST -> Icons.Default.Download
    SortOption.NAME_ASC -> Icons.Default.SortByAlpha
    SortOption.NAME_DESC -> Icons.Default.SortByAlpha
    SortOption.RECENTLY_PLAYED -> Icons.Default.Schedule
    SortOption.SIZE_SMALLEST -> Icons.Default.Compress
    SortOption.SIZE_LARGEST -> Icons.Default.Storage
    SortOption.FPS_HIGH -> Icons.Rounded.Speed
    SortOption.RUNS_HIGH -> Icons.Rounded.SportsEsports
    SortOption.REVIEWS_HIGH -> Icons.Rounded.Star
    SortOption.REVIEWS_GPU_HIGH -> Icons.Rounded.Stars
    SortOption.COMPATIBILITY -> Icons.Rounded.Verified
}

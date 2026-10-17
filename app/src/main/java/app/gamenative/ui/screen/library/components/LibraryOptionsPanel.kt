package app.gamenative.ui.screen.library.components

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.SteamCollection
import app.gamenative.ui.component.OptionListItem
import app.gamenative.ui.component.OptionSectionHeader
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.SortOption
import app.gamenative.ui.theme.PluviaTheme
import app.gamenative.ui.util.adaptivePanelWidth
import java.util.EnumSet

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
    // Rift: a counter the home bumps when gamepad input arrives while the panel has no focus, and a report of
    // whether the panel holds focus, so input never leaks to the covers under the blur.
    focusAsk: Int = 0,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    // Accordion: only one group is open at a time. B closes the open group first, then the panel.
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val headerFocus = remember { mutableMapOf("sort" to firstItemFocusRequester) }
    fun headerRequester(key: String) = headerFocus.getOrPut(key) { FocusRequester() }
    fun toggleGroup(key: String) {
        openGroup = if (openGroup == key) null else key
    }

    // The active group is open when the panel opens: the only group of a short list, else the top one.
    val onlyGroup = tabGroups?.size == 1
    BackHandler(enabled = isOpen) {
        val open = openGroup
        if (open != null && !onlyGroup) {
            openGroup = null
            runCatching { headerRequester(open).requestFocus() }
        } else {
            onDismiss()
        }
    }

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
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Surface(
                modifier = Modifier
                    .width(adaptivePanelWidth(300.dp))
                    .fillMaxHeight(),
                shape = panelShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = panelShadowElevation,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.rift_filters_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (showCloseButton) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.options_panel_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp)
                    ) {
                        if (tabGroups != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusGroup()
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tabGroups.forEachIndexed { index, group ->
                                    val key = "tab$index"
                                    FilterAccordion(
                                        label = group.label,
                                        valueText = group.value,
                                        icon = group.icon,
                                        expanded = onlyGroup || openGroup == key,
                                        onToggle = { if (!onlyGroup) toggleGroup(key) },
                                        headerFocus = if (index == 0) firstItemFocusRequester else headerRequester(key),
                                    ) {
                                        group.options.forEach { option ->
                                            OptionListItem(
                                                text = option.text,
                                                selected = option.selected,
                                                // Picking applies the filter and closes the panel; the home puts focus back on the row.
                                                onClick = {
                                                    option.onPick()
                                                    openGroup = null
                                                    onDismiss()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusGroup()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterAccordion(
                                label = stringResource(R.string.options_sort_by),
                                valueText = stringResource(currentSortOption.displayTextRes),
                                icon = currentSortOption.icon(),
                                expanded = openGroup == "sort",
                                onToggle = { toggleGroup("sort") },
                                headerFocus = headerRequester("sort"),
                            ) {
                                SortOption.entries.forEach { option ->
                                    OptionListItem(
                                        text = stringResource(option.displayTextRes),
                                        selected = currentSortOption == option,
                                        onClick = {
                                            onSortOptionChanged(option)
                                            openGroup = null
                                            runCatching { headerRequester("sort").requestFocus() }
                                        },
                                        icon = option.icon(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            FilterAccordion(
                                label = stringResource(R.string.library_app_type),
                                valueText = summarize(TYPE_FILTERS, selectedFilters),
                                icon = Icons.Default.Category,
                                expanded = openGroup == "type",
                                onToggle = { toggleGroup("type") },
                                headerFocus = headerRequester("type"),
                            ) {
                                TYPE_FILTERS.forEach { f ->
                                    OptionListItem(
                                        text = stringResource(f.displayTextRes),
                                        selected = selectedFilters.contains(f),
                                        onClick = { onFilterChanged(f) },
                                        icon = f.icon,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            FilterAccordion(
                                label = stringResource(R.string.library_app_status),
                                valueText = summarize(STATUS_FILTERS, selectedFilters),
                                icon = Icons.Default.FilterList,
                                expanded = openGroup == "status",
                                onToggle = { toggleGroup("status") },
                                headerFocus = headerRequester("status"),
                            ) {
                                STATUS_FILTERS.forEach { f ->
                                    OptionListItem(
                                        text = stringResource(f.displayTextRes),
                                        selected = selectedFilters.contains(f),
                                        onClick = { onFilterChanged(f) },
                                        icon = f.icon,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (extraGroup != null) {
                                FilterAccordion(
                                    label = extraGroup.label,
                                    valueText = extraGroup.value,
                                    icon = extraGroup.icon,
                                    expanded = openGroup == "extra",
                                    onToggle = { toggleGroup("extra") },
                                    headerFocus = headerRequester("extra"),
                                ) {
                                    extraGroup.options.forEach { option ->
                                        OptionListItem(
                                            text = option.text,
                                            selected = option.selected,
                                            onClick = {
                                                option.onPick()
                                                openGroup = null
                                                onDismiss()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
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
                                expanded = openGroup == "steam",
                                onExpandedChange = { toggleGroup("steam") },
                                headerFocus = headerRequester("steam"),
                                onToggle = onSteamCollectionToggle,
                                onClear = onClearSteamCollections,
                            )
                        }
                        if (isSteamConnected || hasSteamCredentials) {
                            CollectionFilterSection(
                                collections = curatedLists,
                                selectedIds = selectedCuratedListIds,
                                counts = curatedListCounts,
                                titleRes = R.string.curated_lists_title,
                                icon = Icons.Default.Star,
                                loadingRes = R.string.curated_lists_loading,
                                clearRes = R.string.curated_lists_clear,
                                emptyRes = R.string.curated_lists_empty,
                                messageRes = if (isOffline) {
                                    listOf(R.string.curated_lists_offline)
                                } else {
                                    emptyList()
                                },
                                expanded = openGroup == "curated",
                                onExpandedChange = { toggleGroup("curated") },
                                headerFocus = headerRequester("curated"),
                                onToggle = onCuratedListToggle,
                                onClear = onClearCuratedLists,
                            )
                        }
                        }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    LaunchedEffect(isOpen) {
        if (isOpen) openGroup = if (tabGroups != null) "tab0" else "sort"
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
    FilterAccordion(
        label = stringResource(titleRes),
        valueText = valueText,
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
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(loadingRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            collections.isEmpty() -> {
                val messages = messageRes.ifEmpty { listOfNotNull(emptyRes) }
                messages.forEach { message -> CollectionFilterMessage(message) }
            }
            else -> {
                if (selectedIds.isNotEmpty()) {
                    OptionListItem(
                        text = stringResource(clearRes),
                        selected = false,
                        onClick = onClear,
                        icon = Icons.Default.Close,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                collections.sortedBy { it.name.lowercase() }.forEach { collection ->
                    OptionListItem(
                        text = collection.name,
                        selected = collection.id in selectedIds,
                        onClick = { onToggle(collection.id) },
                        trailingText = counts[collection.id]?.toString(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                messageRes.forEach { message -> CollectionFilterMessage(message) }
            }
        }
    }
}

@Composable
private fun CollectionFilterMessage(@StringRes messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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

/**
 * One filter group on one line (label + current value + chevron). A unrolls the options right
 * inside the panel; the caller keeps only one group open at a time.
 */
@Composable
private fun FilterAccordion(
    label: String,
    valueText: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerFocus: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isFocused) 0.14f else if (expanded) 0.09f else 0.06f
                    )
                )
                .then(
                    if (isFocused) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), shape)
                    } else Modifier
                )
                .focusRequester(headerFocus)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                content()
            }
        }
    }
}

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

package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import app.gamenative.data.FavoritesManager
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.enums.SortOption
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.screen.library.components.LibraryDetailPane
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.rememberRiftSoundPlayer
import dev.rift.launcher.sound.riftKeySounds
import dev.rift.launcher.ui.theme.RiftBrush
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import dev.rift.launcher.ui.theme.RiftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenPadding = 48.dp
private val CardGap = 18.dp
private const val LOAD_MORE_THRESHOLD = 6

/**
 * Rift's start screen: Big Picture style hero, Beacon style section tabs on top, cover shelves.
 *
 * Games and launching come from GameNative: the library is GameNative's [LibraryViewModel], the game
 * page is its [LibraryDetailPane] and Play goes through the same pre-launch flow that opens
 * XServerScreen.
 */
@Composable
fun RiftHomeScreen(
    onClickPlay: (appId: String, asContainer: Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
    onAiDebugRun: (String) -> Unit,
    onOpenStores: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    // Own key so Rift does not share tab and paging state with GameNative's library screen.
    viewModel: LibraryViewModel = hiltViewModel(key = "rift-home-library"),
) {
    val context = LocalContext.current
    val sounds = rememberRiftSoundPlayer()
    val libraryState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(libraryState.visibleLibraryTabs) {
        if (libraryState.currentTab != LibraryTab.ALL && LibraryTab.ALL in libraryState.visibleLibraryTabs) {
            viewModel.onTabChanged(LibraryTab.ALL)
        }
    }

    val library = remember(libraryState.appInfoList) {
        libraryState.appInfoList.filterNot { it.isRecommended || it.isFeatured || it.isRecTeaser || it.isRecLoading }
    }
    val sections = remember(library) { RiftSections.configured(context, library) }

    var sectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sections.firstOrNull { it.key == sectionKey } ?: sections.firstOrNull()

    var focusedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpenedItem by remember { mutableStateOf<LibraryItem?>(null) }
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }

    // Library filters live in the left panel (Select), as in Beacon.
    var shelfFilter by rememberSaveable { mutableStateOf(ShelfFilter.ALL) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sourceNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val favorites by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val sources = remember(sourceNames) { GameSource.entries.filter { it.name in sourceNames }.toSet() }
    val presentSources = remember(library) { GameSource.entries.filter { source -> library.any { it.gameSource == source } } }
    val filterActive = shelfFilter != ShelfFilter.ALL || favoritesOnly || sources.isNotEmpty()
    val shown = remember(library, shelfFilter, favoritesOnly, sources, favorites) {
        shelfFilter.apply(library).filter { item ->
            (!favoritesOnly || item.appId in favorites) && (sources.isEmpty() || item.gameSource in sources)
        }
    }
    val focusedItem = shown.firstOrNull { it.appId == focusedAppId } ?: shown.firstOrNull()

    fun toggleSource(source: GameSource) {
        val current = sources.ifEmpty { presentSources.toSet() }
        val next = if (source in current) current - source else current + source
        // Nothing or everything picked both mean "all sources".
        sourceNames = if (next.isEmpty() || next.containsAll(presentSources)) emptyList() else next.map { it.name }
    }

    // A filter only sees loaded pages, so pull in the rest of the library while one is active.
    LaunchedEffect(filterActive, libraryState.currentPaginationPage, libraryState.lastPaginationPage, libraryState.isLoading) {
        if (filterActive && !libraryState.isLoading &&
            libraryState.currentPaginationPage < libraryState.lastPaginationPage
        ) {
            viewModel.onPageChange(1)
        }
    }

    fun openGame(item: LibraryItem) {
        lastOpenedItem = item
        openedAppId = item.appId
    }

    fun playOrOpen(item: LibraryItem) {
        if (item.isInstalled) {
            sounds.play(RiftSound.Launch)
            onClickPlay(item.appId, false)
        } else {
            openGame(item)
        }
    }

    fun switchSection(step: Int) {
        if (sections.size < 2 || section == null) return
        val index = (sections.indexOf(section) + step).mod(sections.size)
        sectionKey = sections[index].key
    }

    val opened = openedAppId?.let { id -> library.firstOrNull { it.appId == id } ?: lastOpenedItem?.takeIf { it.appId == id } }
    if (opened != null) {
        BackHandler { openedAppId = null }
        LibraryDetailPane(
            libraryItem = opened,
            onClickPlay = { asContainer ->
                sounds.play(RiftSound.Launch)
                onClickPlay(opened.appId, asContainer)
            },
            onTestGraphics = { onTestGraphics(opened.appId) },
            onPlayWithDiagnostics = { onPlayWithDiagnostics(opened.appId) },
            onAiDebugRun = { onAiDebugRun(opened.appId) },
            onBack = {
                pendingFocusKey = focusKeyFor(opened)
                openedAppId = null
            },
        )
        return
    }

    BackHandler { onExit() }

    var menuOpen by rememberSaveable { mutableStateOf(false) }
    fun closeMenu() {
        menuOpen = false
        // Hand focus back to the cover that had it before the menu opened.
        focusedItem?.let { pendingFocusKey = focusKeyFor(it) }
    }
    BackHandler(enabled = menuOpen) { closeMenu() }

    RiftTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
                .riftKeySounds(sidebarOpen = menuOpen)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (keyCode in MENU_KEYS) {
                        if (menuOpen) closeMenu() else menuOpen = true
                        return@onPreviewKeyEvent true
                    }
                    if (menuOpen) return@onPreviewKeyEvent false
                    when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            switchSection(-1)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            switchSection(1)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            focusedItem?.let(::playOrOpen)
                            focusedItem != null
                        }
                        else -> false
                    }
                },
        ) {
            val cardHeight = (maxHeight * 0.34f).coerceIn(150.dp, 300.dp)

            HeroBackdrop(item = focusedItem)

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    sections = sections,
                    selected = section,
                    onSelect = { sectionKey = it.key },
                    onOpenMenu = { menuOpen = true },
                    onOpenStores = onOpenStores,
                    onOpenSettings = onOpenSettings,
                )

                when {
                    section == null -> EmptyState(
                        message = stringResource(R.string.rift_no_sources_message),
                        onConnect = onOpenStores,
                        modifier = Modifier.weight(1f),
                    )
                    library.isEmpty() && libraryState.isLoading -> Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = RiftColors.Primary)
                    }
                    library.isEmpty() -> EmptyState(
                        message = stringResource(R.string.rift_empty_message),
                        onConnect = onOpenStores,
                        modifier = Modifier.weight(1f),
                    )
                    else -> {
                        HeroInfo(
                            item = focusedItem,
                            onPlay = { focusedItem?.let(::playOrOpen) },
                            onDetails = { focusedItem?.let(::openGame) },
                            modifier = Modifier.weight(1f),
                        )
                        PcGamesShelf(
                            shown = shown,
                            title = shelfTitle(shelfFilter, favoritesOnly, sources),
                            resetKey = listOf(shelfFilter, favoritesOnly, sources, libraryState.currentSortOption),
                            cardHeight = cardHeight,
                            pendingFocusKey = pendingFocusKey,
                            onFocusConsumed = { pendingFocusKey = null },
                            onFocused = { focusedAppId = it.appId },
                            onOpen = ::openGame,
                            onNearEnd = {
                                if (!filterActive &&
                                    libraryState.currentPaginationPage < libraryState.lastPaginationPage) {
                                    viewModel.onPageChange(1)
                                }
                            },
                        )
                    }
                }

                HintsBar()
            }

            LeftMenu(
                visible = menuOpen,
                filters = LeftMenuFilters(
                    show = shelfFilter,
                    onShow = { shelfFilter = it },
                    favoritesOnly = favoritesOnly,
                    onFavoritesOnly = { favoritesOnly = it },
                    sort = libraryState.currentSortOption,
                    onSort = viewModel::onSortOptionChanged,
                    presentSources = presentSources,
                    isSourceOn = { sources.isEmpty() || it in sources },
                    onToggleSource = ::toggleSource,
                ),
                sections = sections,
                selected = section,
                onSelectSection = {
                    sectionKey = it.key
                    closeMenu()
                },
                onOpenStores = {
                    menuOpen = false
                    onOpenStores()
                },
                onOpenSettings = {
                    menuOpen = false
                    onOpenSettings()
                },
                onExit = onExit,
                onDismiss = ::closeMenu,
            )
        }
    }

    // Focus the first cover once the library shows up, so a gamepad works without touching the screen.
    LaunchedEffect(shown.isNotEmpty()) {
        if (shown.isNotEmpty() && pendingFocusKey == null) {
            val target = focusedAppId?.let { id -> shown.firstOrNull { it.appId == id } } ?: shown.first()
            pendingFocusKey = focusKeyFor(target)
        }
    }
}

/** Select (View) opens the left menu, as in Beacon; Start (Menu) does the same until the right panel exists. */
private val MENU_KEYS = setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU)

/** One cover row on the home screen; Denis wants installed or not installed picked with a filter, not a second row. */
private enum class ShelfFilter(val label: Int) {
    ALL(R.string.rift_filter_all),
    INSTALLED(R.string.rift_filter_installed),
    NOT_INSTALLED(R.string.rift_filter_not_installed),
    ;

    fun apply(library: List<LibraryItem>): List<LibraryItem> = when (this) {
        ALL -> library
        INSTALLED -> library.filter { it.isInstalled }
        NOT_INSTALLED -> library.filterNot { it.isInstalled }
    }
}

/** Sort options offered in the left panel; the order itself comes from GameNative's library. */
private val SORT_OPTIONS = listOf(SortOption.INSTALLED_FIRST, SortOption.RECENTLY_PLAYED, SortOption.NAME_ASC, SortOption.SIZE_LARGEST)

private fun focusKeyFor(item: LibraryItem): String = item.appId

@Composable
private fun shelfTitle(filter: ShelfFilter, favoritesOnly: Boolean, sources: Set<GameSource>): String {
    val parts = mutableListOf(stringResource(filter.label))
    if (favoritesOnly) parts += stringResource(R.string.rift_filter_favorites)
    sources.forEach { parts += sourceLabel(it) }
    return parts.joinToString(" · ")
}

@Composable
private fun PcGamesShelf(
    shown: List<LibraryItem>,
    title: String,
    resetKey: Any,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(resetKey) { listState.scrollToItem(0) }

    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "${title.uppercase()} · ${shown.size}",
            color = RiftColors.OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.7.sp,
            modifier = Modifier.padding(start = ScreenPadding, top = 6.dp),
        )
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight + 28.dp)
                    .padding(horizontal = ScreenPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.rift_filter_empty),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            return@Column
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            itemsIndexed(shown, key = { _, item -> item.appId }) { index, item ->
                GameCard(
                    item = item,
                    height = cardHeight,
                    requestFocus = pendingFocusKey == focusKeyFor(item),
                    onFocusRequested = onFocusConsumed,
                    onFocused = {
                        onFocused(item)
                        // Keep the focused cover near the left edge, Big Picture style.
                        scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                        if (index >= shown.size - LOAD_MORE_THRESHOLD) onNearEnd()
                    },
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    item: LibraryItem,
    height: Dp,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }

    val scale by animateFloatAsState(if (focused) 1.07f else 1f, RiftMotion.focusSpring(), label = "cardScale")
    val lift by animateDpAsState(if (focused) (-8).dp else 0.dp, RiftMotion.focusSpring(), label = "cardLift")
    val glow = rememberCoverGlow(item.capsuleImageUrl.ifEmpty { item.headerImageUrl }, enabled = focused)

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { focusRequester.requestFocus() }
            onFocusRequested()
        }
    }

    Box(
        modifier = Modifier
            .height(height)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift.toPx()
            }
            .shadow(
                elevation = if (focused) 29.dp else 0.dp,
                shape = RiftShapes.card,
                ambientColor = glow.copy(alpha = 0.34f),
                spotColor = glow.copy(alpha = 0.34f),
            )
            .clip(RiftShapes.card)
            .background(RiftColors.SurfaceContainer)
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RiftShapes.card)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(
            text = item.name,
            color = RiftColors.OnSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(12.dp),
        )
        CoilImage(
            modifier = Modifier.fillMaxSize(),
            imageModel = { item.capsuleImageUrl.ifEmpty { item.headerImageUrl } },
            imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = item.name),
            loading = {},
            failure = {},
        )
        if (item.isInstalled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(10.dp)
                    .clip(RiftShapes.full)
                    .background(RiftColors.Success),
            )
        }
    }
}

@Composable
private fun rememberCoverGlow(url: String, enabled: Boolean): Color {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, enabled) {
        if (enabled && color == null) {
            color = withContext(Dispatchers.IO) { runCatching { CoverColors.dominant(context, url) }.getOrNull() }
        }
    }
    return color ?: RiftColors.GlowFallback
}

@Composable
private fun HeroBackdrop(item: LibraryItem?) {
    val url = item?.let { it.heroImageUrl.ifEmpty { it.headerImageUrl.ifEmpty { it.capsuleImageUrl } } }.orEmpty()
    Crossfade(
        targetState = url,
        animationSpec = tween(RiftMotion.HERO_CROSSFADE_MS, easing = FastOutSlowInEasing),
        label = "hero",
    ) { heroUrl ->
        val zoom = remember(heroUrl) { Animatable(1.04f) }
        LaunchedEffect(heroUrl) { zoom.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
        Box(modifier = Modifier.fillMaxSize()) {
            if (heroUrl.isNotEmpty()) {
                CoilImage(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                        },
                    imageModel = { heroUrl },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = null),
                    loading = {},
                    failure = {},
                )
            }
        }
    }
    // Scrims from the design tokens: strong at the bottom for the shelves, light at the top for the bar.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to RiftColors.Background.copy(alpha = 0.55f),
                    0.22f to Color.Transparent,
                    0.38f to Color.Transparent,
                    0.66f to RiftColors.Background.copy(alpha = 0.55f),
                    1f to RiftColors.Background.copy(alpha = 0.96f),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0f to RiftColors.Background.copy(alpha = 0.7f),
                    0.45f to Color.Transparent,
                ),
            ),
    )
}

@Composable
private fun TopBar(
    sections: List<RiftSection>,
    selected: RiftSection?,
    onSelect: (RiftSection) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenStores: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Outlined.Tune, stringResource(R.string.rift_hint_filters), onOpenMenu)
        Spacer(modifier = Modifier.width(16.dp))
        RiftWordmark()
        Spacer(modifier = Modifier.width(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sections.forEach { section ->
                SectionTab(
                    title = stringResource(section.titleRes),
                    selected = section == selected,
                    onClick = { onSelect(section) },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton(Icons.Outlined.Storefront, stringResource(R.string.rift_stores), onOpenStores)
            RoundIconButton(Icons.Outlined.Settings, stringResource(R.string.rift_settings), onOpenSettings)
        }
    }
}

@Composable
private fun RiftWordmark() {
    Text(
        text = "RIFT",
        style = TextStyle(brush = RiftBrush.accent),
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 3.sp,
    )
}

/**
 * The one button shape Rift uses: a full pill (or a circle when square), [RiftSizes.button] tall,
 * white ring and a slight spring scale on focus.
 */
@Composable
private fun PillButton(
    onClick: () -> Unit,
    background: (focused: Boolean) -> Brush,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    focusRequester: FocusRequester? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, RiftMotion.focusSpring(), label = "buttonScale")
    Box(
        modifier = modifier
            .height(RiftSizes.button)
            .then(if (square) Modifier.width(RiftSizes.button) else Modifier.widthIn(min = RiftSizes.button))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RiftShapes.full)
            .background(background(focused))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, RiftShapes.full)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (square) 0.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(focused)
    }
}

private fun solid(color: Color) = Brush.linearGradient(listOf(color, color))

private fun quietBackground(focused: Boolean) =
    solid(if (focused) RiftColors.SurfaceContainerHigh else RiftColors.Surface.copy(alpha = 0.7f))

@Composable
private fun SectionTab(title: String, selected: Boolean, onClick: () -> Unit) {
    PillButton(
        onClick = onClick,
        background = { focused -> if (selected) solid(RiftColors.Primary) else quietBackground(focused) },
    ) {
        Text(
            text = title,
            color = if (selected) RiftColors.OnPrimary else RiftColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    PillButton(onClick = onClick, background = ::quietBackground, square = true) {
        Icon(icon, contentDescription = description, tint = RiftColors.OnSurface)
    }
}

@Composable
private fun HeroInfo(
    item: LibraryItem?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (item == null) return@Column
        GameLogo(item = item)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${sourceLabel(item.gameSource)} · " +
                stringResource(if (item.isInstalled) R.string.rift_installed else R.string.rift_not_installed),
            color = RiftColors.OnSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(
                text = stringResource(if (item.isInstalled) R.string.rift_play else R.string.rift_install),
                onClick = onPlay,
            )
            SecondaryButton(text = stringResource(R.string.rift_details), onClick = onDetails)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun GameLogo(item: LibraryItem) {
    val logoUrl = if (item.gameSource == GameSource.STEAM && item.gameId != 0) {
        "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/${item.gameId}/logo.png"
    } else {
        ""
    }
    var logoFailed by remember(item.appId) { mutableStateOf(logoUrl.isEmpty()) }
    if (!logoFailed) {
        CoilImage(
            modifier = Modifier
                .heightIn(max = 120.dp)
                .widthIn(max = 420.dp)
                .fillMaxHeight(0.5f),
            imageModel = { logoUrl },
            imageOptions = ImageOptions(
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                contentDescription = item.name,
            ),
            loading = {},
            failure = { LaunchedEffect(Unit) { logoFailed = true } },
        )
    } else {
        Text(
            text = item.name.uppercase(),
            color = RiftColors.OnSurface,
            fontSize = 40.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 640.dp),
        )
    }
}

@Composable
private fun sourceLabel(source: GameSource): String = stringResource(
    when (source) {
        GameSource.STEAM -> R.string.rift_source_steam
        GameSource.GOG -> R.string.rift_source_gog
        GameSource.EPIC -> R.string.rift_source_epic
        GameSource.AMAZON -> R.string.rift_source_amazon
        GameSource.CUSTOM_GAME -> R.string.rift_source_custom
    },
)

@Composable
private fun PlayButton(text: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    PillButton(
        onClick = onClick,
        background = { RiftBrush.accent },
        modifier = Modifier.widthIn(min = 160.dp),
        focusRequester = focusRequester,
    ) {
        Text(text = text, color = RiftColors.OnPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    PillButton(onClick = onClick, background = ::quietBackground) {
        Text(text = text, color = RiftColors.OnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyState(message: String, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.rift_empty_title),
            color = RiftColors.OnSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = RiftColors.OnSurfaceVariant,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        PlayButton(
            text = stringResource(R.string.rift_empty_action),
            onClick = onConnect,
            focusRequester = focusRequester,
        )
    }
}

@Composable
private fun HintsBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, RiftColors.Background.copy(alpha = 0.92f))))
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("SELECT", stringResource(R.string.rift_hint_filters))
        Hint("A", stringResource(R.string.rift_hint_select))
        Hint("Y", stringResource(R.string.rift_hint_play))
        Hint("LB RB", stringResource(R.string.rift_hint_sections))
        Hint("B", stringResource(R.string.rift_hint_back))
    }
}

@Composable
private fun Hint(glyph: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .clip(RiftShapes.full)
                .background(RiftColors.SurfaceContainerHigh)
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(text = glyph, color = RiftColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = label.uppercase(),
            color = RiftColors.OnSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** What the left panel needs to show and change the library filters. */
private class LeftMenuFilters(
    val show: ShelfFilter,
    val onShow: (ShelfFilter) -> Unit,
    val favoritesOnly: Boolean,
    val onFavoritesOnly: (Boolean) -> Unit,
    val sort: SortOption,
    val onSort: (SortOption) -> Unit,
    val presentSources: List<GameSource>,
    val isSourceOn: (GameSource) -> Boolean,
    val onToggleSource: (GameSource) -> Unit,
)

/**
 * Left panel, Beacon style: library filters on top, then sections, Stores and Settings.
 * Opened with Select (or the menu button), closed with B. Focus stays inside while it is open.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LeftMenu(
    visible: Boolean,
    filters: LeftMenuFilters,
    sections: List<RiftSection>,
    selected: RiftSection?,
    onSelectSection: (RiftSection) -> Unit,
    onOpenStores: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(RiftMotion.panelSpring()) { -it },
        exit = slideOutHorizontally(RiftMotion.panelSpring()) { -it },
    ) {
        val firstItem = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(RiftSizes.leftPanel)
                .background(RiftColors.Surface.copy(alpha = 0.96f))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RiftWordmark()

            MenuLabel(stringResource(R.string.rift_filter_show))
            ShelfFilter.entries.forEachIndexed { index, option ->
                MenuItem(
                    title = stringResource(option.label),
                    selected = option == filters.show,
                    focusRequester = if (index == 0) firstItem else null,
                    onClick = { filters.onShow(option) },
                )
            }
            MenuItem(
                title = stringResource(R.string.rift_filter_favorites),
                icon = if (filters.favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                selected = filters.favoritesOnly,
                onClick = { filters.onFavoritesOnly(!filters.favoritesOnly) },
            )

            MenuLabel(stringResource(R.string.rift_filter_sort))
            SORT_OPTIONS.forEach { option ->
                MenuItem(
                    title = stringResource(option.displayTextRes),
                    selected = option == filters.sort,
                    onClick = { filters.onSort(option) },
                )
            }

            if (filters.presentSources.size > 1) {
                MenuLabel(stringResource(R.string.rift_filter_source))
                filters.presentSources.forEach { source ->
                    MenuItem(
                        title = sourceLabel(source),
                        selected = filters.isSourceOn(source),
                        onClick = { filters.onToggleSource(source) },
                    )
                }
            }

            MenuLabel(stringResource(R.string.rift_menu_sections))
            sections.forEach { section ->
                MenuItem(
                    title = stringResource(section.titleRes),
                    selected = section == selected,
                    onClick = { onSelectSection(section) },
                )
            }
            MenuItem(title = stringResource(R.string.rift_stores), icon = Icons.Outlined.Storefront, onClick = onOpenStores)
            MenuItem(title = stringResource(R.string.rift_settings), icon = Icons.Outlined.Settings, onClick = onOpenSettings)
            Spacer(modifier = Modifier.height(12.dp))
            MenuItem(title = stringResource(R.string.rift_exit), onClick = onExit)
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = RiftColors.OnSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.7.sp,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuItem(
    title: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
) {
    PillButton(
        onClick = onClick,
        background = { focused ->
            when {
                selected -> solid(RiftColors.Primary)
                focused -> solid(RiftColors.SurfaceContainerHigh)
                else -> solid(Color.Transparent)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
    ) {
        val color = if (selected) RiftColors.OnPrimary else RiftColors.OnSurface
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(text = title, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

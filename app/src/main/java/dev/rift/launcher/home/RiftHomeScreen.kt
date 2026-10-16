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
import androidx.compose.material.icons.outlined.Menu
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
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.screen.library.components.LibraryDetailPane
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
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

    val focusedItem = library.firstOrNull { it.appId == focusedAppId } ?: library.firstOrNull()

    fun openGame(item: LibraryItem) {
        lastOpenedItem = item
        openedAppId = item.appId
    }

    fun playOrOpen(item: LibraryItem) {
        if (item.isInstalled) onClickPlay(item.appId, false) else openGame(item)
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
            onClickPlay = { asContainer -> onClickPlay(opened.appId, asContainer) },
            onTestGraphics = { onTestGraphics(opened.appId) },
            onPlayWithDiagnostics = { onPlayWithDiagnostics(opened.appId) },
            onAiDebugRun = { onAiDebugRun(opened.appId) },
            onBack = {
                pendingFocusKey = focusKeyFor(opened, library)
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
        focusedItem?.let { pendingFocusKey = focusKeyFor(it, library) }
    }
    BackHandler(enabled = menuOpen) { closeMenu() }

    RiftTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
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
                        PcGamesShelves(
                            library = library,
                            cardHeight = cardHeight,
                            pendingFocusKey = pendingFocusKey,
                            onFocusConsumed = { pendingFocusKey = null },
                            onFocused = { focusedAppId = it.appId },
                            onOpen = ::openGame,
                            onNearEnd = {
                                if (libraryState.currentPaginationPage < libraryState.lastPaginationPage) {
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
    LaunchedEffect(library.isNotEmpty()) {
        if (library.isNotEmpty() && pendingFocusKey == null) {
            val target = focusedAppId?.let { id -> library.firstOrNull { it.appId == id } } ?: library.first()
            pendingFocusKey = focusKeyFor(target, library)
        }
    }
}

/** Select (View) opens the left menu, as in Beacon; Start (Menu) does the same until the right panel exists. */
private val MENU_KEYS = setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU)

private const val SHELF_INSTALLED = "installed"
private const val SHELF_LIBRARY = "library"

private fun focusKeyFor(item: LibraryItem, library: List<LibraryItem>): String {
    val hasInstalledShelf = library.any { it.isInstalled }
    val shelf = if (item.isInstalled && hasInstalledShelf) SHELF_INSTALLED else SHELF_LIBRARY
    return "$shelf:${item.appId}"
}

@Composable
private fun PcGamesShelves(
    library: List<LibraryItem>,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
) {
    val installed = remember(library) { library.filter { it.isInstalled } }
    // One shelf stays fully visible, the next one peeks in so it is clear there is more below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight + 96.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (installed.isNotEmpty()) {
            Shelf(
                key = SHELF_INSTALLED,
                title = stringResource(R.string.rift_shelf_installed),
                items = installed,
                cardHeight = cardHeight,
                pendingFocusKey = pendingFocusKey,
                onFocusConsumed = onFocusConsumed,
                onFocused = onFocused,
                onOpen = onOpen,
                onNearEnd = {},
            )
        }
        Shelf(
            key = SHELF_LIBRARY,
            title = stringResource(R.string.rift_shelf_library),
            items = library,
            cardHeight = cardHeight,
            pendingFocusKey = pendingFocusKey,
            onFocusConsumed = onFocusConsumed,
            onFocused = onFocused,
            onOpen = onOpen,
            onNearEnd = onNearEnd,
        )
    }
}

@Composable
private fun Shelf(
    key: String,
    title: String,
    items: List<LibraryItem>,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = "${title.uppercase()} · ${items.size}",
            color = RiftColors.OnSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.7.sp,
            modifier = Modifier.padding(start = ScreenPadding, bottom = 10.dp, top = 6.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            itemsIndexed(items, key = { _, item -> item.appId }) { index, item ->
                val focusKey = "$key:${item.appId}"
                GameCard(
                    item = item,
                    height = cardHeight,
                    requestFocus = pendingFocusKey == focusKey,
                    onFocusRequested = onFocusConsumed,
                    onFocused = {
                        onFocused(item)
                        // Keep the focused cover near the left edge, Big Picture style.
                        scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                        if (index >= items.size - LOAD_MORE_THRESHOLD) onNearEnd()
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
        RoundIconButton(Icons.Outlined.Menu, stringResource(R.string.rift_menu), onOpenMenu)
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
        background = { focused -> if (selected) solid(Color.White) else quietBackground(focused) },
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
        Hint("SELECT", stringResource(R.string.rift_menu))
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

/**
 * Left navigation panel, Beacon style: opened with Select (or the menu button), closed with B.
 * Focus stays inside while it is open.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LeftMenu(
    visible: Boolean,
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
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RiftWordmark()
            Spacer(modifier = Modifier.height(14.dp))
            sections.forEachIndexed { index, section ->
                MenuItem(
                    title = stringResource(section.titleRes),
                    selected = section == selected,
                    focusRequester = if (index == 0) firstItem else null,
                    onClick = { onSelectSection(section) },
                )
            }
            MenuItem(
                title = stringResource(R.string.rift_stores),
                focusRequester = if (sections.isEmpty()) firstItem else null,
                onClick = onOpenStores,
            )
            MenuItem(title = stringResource(R.string.rift_settings), onClick = onOpenSettings)
            Spacer(modifier = Modifier.weight(1f))
            MenuItem(title = stringResource(R.string.rift_exit), onClick = onExit)
        }
    }
}

@Composable
private fun MenuItem(
    title: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    PillButton(
        onClick = onClick,
        background = { focused ->
            when {
                selected -> solid(Color.White)
                focused -> solid(RiftColors.SurfaceContainerHigh)
                else -> solid(Color.Transparent)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
    ) {
        Text(
            text = title,
            color = if (selected) RiftColors.OnPrimary else RiftColors.OnSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

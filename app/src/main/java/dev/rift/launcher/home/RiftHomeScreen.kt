package dev.rift.launcher.home

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.events.AndroidEvent
import app.gamenative.PluviaApp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.focusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import app.gamenative.ui.component.GamepadButton
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawWithContent
import android.os.Build
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import app.gamenative.ui.component.CommunityCompatibilityBadge
import app.gamenative.ui.component.CompatibilityBadge
import app.gamenative.ui.data.LibraryState
import app.gamenative.ui.data.communityCompatibilityFor
import app.gamenative.ui.enums.LibraryTab
import app.gamenative.ui.model.LibraryViewModel
import app.gamenative.ui.screen.library.components.GameSourceIcon
import app.gamenative.ui.screen.library.components.LibraryDetailPane
import app.gamenative.ui.screen.library.components.LibraryOptionsPanel
import app.gamenative.utils.SteamUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.rememberRiftSoundPlayer
import dev.rift.launcher.sound.riftKeySounds
import dev.rift.launcher.stream.PcStreaming
import dev.rift.launcher.stream.PcStreamingContent
import dev.rift.launcher.stream.rememberPcStreamingTab
import dev.rift.launcher.ui.theme.RiftBrush
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftGlassTheme
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import dev.rift.launcher.ui.theme.RiftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ScreenPadding = 48.dp
private val CardGap = 18.dp

/** Lifts the row off the hints bar (Denis: covers bigger and higher). */
private val RowBottomGap = 6.dp
private const val LOAD_MORE_THRESHOLD = 6

/**
 * Rift's start screen: Big Picture style hero, section tabs between the bumpers on top, one cover row.
 *
 * Games and launching come from GameNative: the library is GameNative's [LibraryViewModel], the game
 * page is its [LibraryDetailPane], Select opens its library filter panel, Start its system menu, and
 * Play goes through the same pre-launch flow that opens XServerScreen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RiftHomeScreen(
    onClickPlay: (appId: String, asContainer: Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
    onAiDebugRun: (String) -> Unit,
    onOpenStores: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    onExit: () -> Unit,
    isOffline: Boolean,
    isSteamConnected: Boolean,
    // Own key so Rift does not share tab and paging state with GameNative's library screen.
    viewModel: LibraryViewModel = hiltViewModel(key = "rift-home-library"),
) {
    val context = LocalContext.current
    val homeScope = rememberCoroutineScope()
    val sounds = rememberRiftSoundPlayer()
    val libraryState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(libraryState.visibleLibraryTabs) {
        if (libraryState.currentTab != LibraryTab.ALL && LibraryTab.ALL in libraryState.visibleLibraryTabs) {
            viewModel.onTabChanged(LibraryTab.ALL)
        }
    }

    val loadedLibrary = remember(libraryState.appInfoList) {
        libraryState.appInfoList.filterNot { it.isRecommended || it.isFeatured || it.isRecTeaser || it.isRecLoading }
    }
    // While GameNative reloads the list (it empties it first), keep showing the last one: no flashes.
    var lastLibrary by remember { mutableStateOf(emptyList<LibraryItem>()) }
    val library = if (loadedLibrary.isEmpty() && libraryState.isLoading) lastLibrary else loadedLibrary
    SideEffect { if (loadedLibrary.isNotEmpty() || !libraryState.isLoading) lastLibrary = loadedLibrary }
    // Android games the user added in settings; reloads when that list changes.
    val androidGames by produceState(emptyList<AndroidGame>()) {
        suspend fun load() {
            value = withContext(Dispatchers.IO) { runCatching { AndroidGames.selected(context) }.getOrDefault(emptyList()) }
        }
        load()
        val prefs = AndroidGames.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AndroidGames.KEY_PACKAGES) launch { load() }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val pairedPcs by PcStreaming.pairedHosts(context).collectAsStateWithLifecycle()
    val sections = remember(library, androidGames, pairedPcs) { RiftSections.configured(context, library, androidGames, pairedPcs) }

    // Start-up: pure black until there is something to show (or, with nothing set up, until the
    // stores have had a moment to sign in), then one entrance: the art drifts in with a slight zoom
    // and the covers roll in from the right one after another.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STARTUP_SETTLE_MS)
        settled = true
    }
    val ready = library.isNotEmpty() || androidGames.isNotEmpty() || pairedPcs.isNotEmpty() || (settled && !libraryState.isLoading)
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    val intro = remember { Animatable(if (introPlayed) 1f else 0f) }
    val introSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(ready) {
        if (ready && !introPlayed) {
            intro.animateTo(1f, introSpec)
            introPlayed = true
        }
    }
    val streamTab = if (pairedPcs.isNotEmpty()) rememberPcStreamingTab() else null

    var sectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sections.firstOrNull { it.key == sectionKey } ?: sections.firstOrNull()

    var focusedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var openedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpenedItem by remember { mutableStateOf<LibraryItem?>(null) }
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }

    // Row positions live here, above the game page, so coming back (or switching tabs) keeps them.
    val pcRowState = rememberLazyListState()
    val androidRowState = rememberLazyListState()
    val filterKey = listOf(libraryState.appInfoSortType.toList(), libraryState.currentSortOption)
    var lastFilterKey by remember { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != lastFilterKey) {
            lastFilterKey = filterKey
            pcRowState.scrollToItem(0)
        }
    }

    val focusedItem = library.firstOrNull { it.appId == focusedAppId } ?: library.firstOrNull()
    val focusedAndroid = androidGames.firstOrNull { it.packageName == focusedPackage } ?: androidGames.firstOrNull()

    fun openGame(item: LibraryItem) {
        lastOpenedItem = item
        openedAppId = item.appId
    }

    /** Puts focus back on the cover this section had last. */
    fun focusSection(target: RiftSection?) {
        // The cover may be scrolled out of the row (a search changes it), so bring it into view first.
        if (target == RiftSection.PcGames) {
            val index = focusedItem?.let(library::indexOf) ?: -1
            if (index >= 0 && pcRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { pcRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        pendingFocusKey = when (target) {
            RiftSection.PcGames -> focusedItem?.let(::focusKeyFor)
            RiftSection.AndroidGames -> focusedAndroid?.let(::androidFocusKey)
            RiftSection.PcStreaming -> streamTab?.focusKey()
            null -> null
        }
    }

    fun selectSection(target: RiftSection) {
        sectionKey = target.key
        focusSection(target)
    }

    fun switchSection(step: Int) {
        if (sections.size < 2 || section == null) return
        selectSection(sections[(sections.indexOf(section) + step).mod(sections.size)])
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

    // B on the home does nothing, like Big Picture (and a HOME launcher has nowhere to go back to).
    BackHandler { }

    // Search: a quiet icon in the top bar; typing filters the row through GameNative's library search.
    // With a gamepad: A on the icon starts typing, B (or Down) hides the keyboard and goes back to the
    // row with the results, B again closes the search. The field only takes focus while typing.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchTyping by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun stopTyping() {
        searchTyping = false
        keyboard?.hide()
        focusSection(section)
    }
    fun closeSearch() {
        searchTyping = false
        keyboard?.hide()
        searchOpen = false
        viewModel.onIsSearching(false)
        focusSection(section)
    }
    BackHandler(enabled = searchOpen) { if (searchTyping) stopTyping() else closeSearch() }

    // Select: GameNative's library filter panel. Start: its system menu. Both over a blurred screen.
    val panelOpen = libraryState.isOptionsPanelOpen
    var systemMenuOpen by rememberSaveable { mutableStateOf(false) }
    // Start: the focused game's options, GameNative's panel alone over the home (see RiftOptionsOnly).
    // It stays attached after closing while one of its dialogs may be up, until the next key here.
    var optionsItem by remember { mutableStateOf<LibraryItem?>(null) }
    var optionsToken by remember { mutableIntStateOf(0) }
    val optionsOnly = remember { RiftOptionsOnly() }
    val gameOptionsOpen = optionsItem != null && optionsOnly.panelOpen
    // B on the home: every installed Android app in a grid, Beacon style.
    var appsOpen by rememberSaveable { mutableStateOf(false) }
    var backDownHere by remember { mutableStateOf(false) }
    val overlayOpen = panelOpen || systemMenuOpen || gameOptionsOpen || appsOpen
    var selectLongPressed by remember { mutableStateOf(false) }
    val quickAccessKey = remember { RiftControls.quickAccessKey(context) }
    // B / Back closes the open panel and never leaves the app.
    BackHandler(enabled = panelOpen) { viewModel.onOptionsPanelToggle(false) }
    BackHandler(enabled = systemMenuOpen) { systemMenuOpen = false }
    // Holding Select opens the quick access panel: on the key's repeats, or after a hold timer for
    // controllers that do not repeat.
    val selectHold = remember { arrayOfNulls<Job>(1) }
    fun toggleQuickAccess() {
        if (selectLongPressed) return
        selectLongPressed = true
        if (!viewModel.state.value.isOptionsPanelOpen) systemMenuOpen = !systemMenuOpen
    }
    // Keeps key handling alive when no cover holds focus (after a panel closes, say): the screen
    // itself takes focus instead of nothing.
    val rootFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    val composeView = LocalView.current
    fun refocusCover() {
        // Compose can hand focus back to the window (Back at the top of the focus tree), and then no
        // key reaches the screen at all: take the view's focus back first.
        if (!composeView.hasFocus()) composeView.requestFocus()
        // Behind the apps grid the covers must not take focus; the grid keeps its own.
        if (appsOpen) return
        // Across a frame, so the cover sees its focus request change even if it was the last one.
        pendingFocusKey = null
        homeScope.launch {
            withFrameNanos { }
            focusSection(section)
        }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }
    // Safety net at the activity level (GameNative's key event bus sees every key first): any press
    // while nothing in this screen has focus puts focus back on a cover and is not passed on.
    DisposableEffect(composeView) {
        val listener: (AndroidEvent.KeyEvent) -> Boolean = { androidEvent ->
            val ev = androidEvent.event
            if (ev.action == KeyEvent.ACTION_DOWN && !composeView.hasFocus() && composeView.hasWindowFocus()) {
                refocusCover()
                true
            } else {
                false
            }
        }
        PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(listener)
        onDispose { PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(listener) }
    }
    // Coming back to the app: focus a cover again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refocusCover() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // What the footer blurs: the screen behind it, recorded once per frame and drawn again under the footer.
    val backdrop = rememberGraphicsLayer()
    // The panel's layout choice belongs to GameNative's library screen; Rift only keeps it in sync.
    var libraryLayout by remember { mutableStateOf(PrefManager.libraryLayout) }
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen) focusSection(section)
    }
    val blur by animateDpAsState(if (overlayOpen) 28.dp else 0.dp, tween(300), label = "backdropBlur")

    val rightStick = rememberRightStick()
    RiftTheme {
      CompositionLocalProvider(LocalRightStick provides rightStick) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
                .riftKeySounds(sidebarOpen = overlayOpen)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (optionsItem != null && optionsOnly.panelOpen) return@onPreviewKeyEvent false
                    if (optionsItem != null && event.type == KeyEventType.KeyDown) optionsItem = null
                    if (appsOpen) return@onPreviewKeyEvent false
                    // Never stranded: a direction press with nothing but the screen focused goes to a cover.
                    if (rootFocused && event.type == KeyEventType.KeyDown && native.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
                        refocusCover()
                        return@onPreviewKeyEvent true
                    }
                    if (searchOpen) {
                        val leave = native.keyCode == KeyEvent.KEYCODE_BUTTON_B || native.keyCode == KeyEvent.KEYCODE_BACK ||
                            native.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                            (searchTyping && native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                        if (leave) {
                            // Handled on key up, after the whole press, so nothing else sees half of it.
                            if (event.type == KeyEventType.KeyUp) {
                                if (searchTyping) stopTyping() else if (native.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) closeSearch()
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                    // B on the home opens the apps grid. Both halves of the press are taken here: left to
                    // Compose, Back moves focus out of the row and the gamepad has nothing left to drive.
                    val isBack = native.keyCode == KeyEvent.KEYCODE_BUTTON_B || native.keyCode == KeyEvent.KEYCODE_BACK ||
                        native.keyCode == KeyEvent.KEYCODE_ESCAPE
                    if (isBack && !systemMenuOpen && !viewModel.state.value.isOptionsPanelOpen) {
                        // Only a press that started here: the release of a B that closed the game page,
                        // a panel or the options must not open the grid.
                        if (event.type == KeyEventType.KeyDown) backDownHere = true
                        if (event.type == KeyEventType.KeyUp && backDownHere) {
                            backDownHere = false
                            appsOpen = true
                        }
                        return@onPreviewKeyEvent true
                    }
                    // Select: short press opens the filter panel, a long press the quick access panel.
                    if (native.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
                        when {
                            event.type == KeyEventType.KeyDown && native.repeatCount == 0 && !native.isLongPress -> {
                                selectLongPressed = false
                                selectHold[0]?.cancel()
                                selectHold[0] = homeScope.launch {
                                    delay(SELECT_HOLD_MS)
                                    toggleQuickAccess()
                                }
                            }
                            event.type == KeyEventType.KeyDown -> {
                                selectHold[0]?.cancel()
                                toggleQuickAccess()
                            }
                            event.type == KeyEventType.KeyUp -> {
                                selectHold[0]?.cancel()
                                if (!selectLongPressed && !systemMenuOpen && section == RiftSection.PcGames) {
                                    viewModel.onOptionsPanelToggle(!viewModel.state.value.isOptionsPanelOpen)
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Guide/Xbox opens quick access whenever the system lets it through to the app; a
                    // button picked in settings does too.
                    if (native.keyCode == KeyEvent.KEYCODE_BUTTON_MODE ||
                        (quickAccessKey != KeyEvent.KEYCODE_UNKNOWN && native.keyCode == quickAccessKey)
                    ) {
                        if (!panelOpen) systemMenuOpen = !systemMenuOpen
                        return@onPreviewKeyEvent true
                    }
                    if (overlayOpen) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            switchSection(-1)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            switchSection(1)
                            true
                        }
                        // Y (and Start): the focused game's options, GameNative's panel alone over the home.
                        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                            if (section == RiftSection.PcGames) {
                                focusedItem?.let {
                                    optionsOnly.panelOpen = true
                                    optionsToken++
                                    optionsItem = it
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
                // Last resort for the apps grid: a B the grid did not take (focus somewhere odd) closes it.
                .onKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    if (!appsOpen || (code != KeyEvent.KEYCODE_BUTTON_B && code != KeyEvent.KEYCODE_BACK)) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyUp) appsOpen = false
                    true
                }
                .focusRequester(rootFocus)
                // Focus should sit on a cover; if it ever lands on the screen itself, send it back.
                .onFocusChanged {
                    rootFocused = it.isFocused
                    if (it.isFocused) refocusCover()
                }
                .focusable(),
        ) {
            val cardHeight = (maxHeight * 0.37f).coerceIn(160.dp, 340.dp)

            Box(modifier = Modifier.fillMaxSize().blur(blur)) {
              Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
              ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = intro.value
                            alpha = p
                            scaleX = 1.12f - 0.12f * p
                            scaleY = 1.12f - 0.12f * p
                        },
                ) {
                    if (section == RiftSection.AndroidGames) AndroidBackdrop(focusedAndroid) else HeroBackdrop(item = focusedItem.takeIf { section == RiftSection.PcGames })
                }

                Column(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (ready) 1f else 0f }) {
                    Box(modifier = Modifier.graphicsLayer { alpha = intro.value }) {
                    TopBar(
                        sections = sections,
                        selected = section,
                        onSelect = ::selectSection,
                        onStep = ::switchSection,
                        search = if (section == RiftSection.PcGames) {
                            SearchState(
                                open = searchOpen,
                                typing = searchTyping,
                                query = libraryState.searchQuery,
                                onOpen = {
                                    if (!searchOpen) viewModel.onIsSearching(true)
                                    searchOpen = true
                                    searchTyping = true
                                },
                                onQuery = viewModel::onSearchQuery,
                                onDone = ::stopTyping,
                            )
                        } else {
                            null
                        },
                    )
                    }

                    when {
                        !ready -> Spacer(modifier = Modifier.weight(1f))
                        section == null -> EmptyState(
                            message = stringResource(R.string.rift_no_sources_message),
                            onConnect = onOpenStores,
                            modifier = Modifier.weight(1f),
                        )
                        section == RiftSection.AndroidGames -> {
                            AndroidHero(game = focusedAndroid, modifier = Modifier.weight(1f))
                            AndroidShelf(
                                games = androidGames,
                                listState = androidRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = { focusedPackage = it.packageName },
                                onOpen = { AndroidGames.launch(context, it) },
                            )
                        }
                        section == RiftSection.PcStreaming && streamTab != null -> PcStreamingContent(
                            tab = streamTab,
                            cardHeight = cardHeight,
                            screenPadding = ScreenPadding,
                            cardGap = CardGap,
                            rowBottomGap = RowBottomGap,
                            pendingFocusKey = pendingFocusKey,
                            onFocusConsumed = { pendingFocusKey = null },
                            onWantFocus = { if (pendingFocusKey == null) focusSection(section) },
                            modifier = Modifier.weight(1f),
                        )
                        // Still loading with nothing cached: stay black rather than show a spinner.
                        library.isEmpty() && libraryState.isLoading -> Spacer(modifier = Modifier.weight(1f))
                        else -> {
                            // Big Picture style: only the big logo of the focused game over its art.
                            HeroLogo(item = focusedItem, modifier = Modifier.weight(1f).graphicsLayer { alpha = intro.value })
                            PcGamesShelf(
                                shown = library,
                                rollIn = !introPlayed,
                                libraryState = libraryState,
                                listState = pcRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = { focusedAppId = it.appId },
                                // A opens the game page; games never start straight from the home (Denis).
                                onOpen = ::openGame,
                                onNearEnd = {
                                    if (libraryState.currentPaginationPage < libraryState.lastPaginationPage) {
                                        viewModel.onPageChange(1)
                                    }
                                },
                            )
                        }
                    }

                    // The footer is frosted glass over the lower part of the reflections, so the row sits low
                    // and its reflections fade out under the glass.
                    Spacer(modifier = Modifier.height(FooterHeight - 36.dp))
                }
              }
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(FooterHeight).graphicsLayer { alpha = intro.value }) {
                    FrostedGlass(backdrop = backdrop, modifier = Modifier.matchParentSize())
                    HintsBar(showFilters = section == RiftSection.PcGames)
                }
            }

            RiftGlassTheme {
                LibraryOptionsPanel(
                    isOpen = panelOpen,
                    onDismiss = { viewModel.onOptionsPanelToggle(false) },
                    selectedFilters = libraryState.appInfoSortType,
                    onFilterChanged = viewModel::onFilterChanged,
                    currentSortOption = libraryState.currentSortOption,
                    onSortOptionChanged = viewModel::onSortOptionChanged,
                    currentView = libraryLayout,
                    onViewChanged = { layout ->
                        PrefManager.libraryLayout = layout
                        libraryLayout = layout
                    },
                    steamCollections = libraryState.steamCollections,
                    selectedSteamCollectionIds = libraryState.selectedSteamCollectionIds,
                    steamCollectionCounts = libraryState.steamCollectionCounts,
                    skippedDynamicCollections = libraryState.skippedDynamicCollections,
                    isSteamConnected = isSteamConnected,
                    hasSteamCredentials = SteamUtils.hasStoredCredentials(),
                    isOffline = isOffline,
                    onSteamCollectionToggle = viewModel::onSteamCollectionToggle,
                    onClearSteamCollections = viewModel::onClearSteamCollections,
                    curatedLists = libraryState.curatedLists,
                    selectedCuratedListIds = libraryState.selectedCuratedListIds,
                    curatedListCounts = libraryState.curatedListCounts,
                    onCuratedListToggle = viewModel::onCuratedListToggle,
                    onClearCuratedLists = viewModel::onClearCuratedLists,
                    panelShape = RectangleShape,
                    panelShadowElevation = 0.dp,
                    showCloseButton = false,
                )
                optionsItem?.let { item ->
                    key(optionsToken) {
                        CompositionLocalProvider(LocalRiftOptionsOnly provides optionsOnly) {
                            LibraryDetailPane(
                                libraryItem = item,
                                onClickPlay = { asContainer ->
                                    sounds.play(RiftSound.Launch)
                                    onClickPlay(item.appId, asContainer)
                                },
                                onTestGraphics = { onTestGraphics(item.appId) },
                                onPlayWithDiagnostics = { onPlayWithDiagnostics(item.appId) },
                                onAiDebugRun = { onAiDebugRun(item.appId) },
                                onBack = { optionsItem = null },
                            )
                        }
                    }
                }
                RiftAppsGrid(isOpen = appsOpen, onDismiss = { appsOpen = false })
                RiftSystemMenu(
                    isOpen = systemMenuOpen,
                    onDismiss = { systemMenuOpen = false },
                    onNavigateRoute = { route ->
                        systemMenuOpen = false
                        onNavigateRoute(route)
                    },
                    onDownloadsClick = {
                        systemMenuOpen = false
                        onDownloadsClick()
                    },
                    onLogout = onLogout,
                    onGoOnline = onGoOnline,
                    isOffline = isOffline,
                )
            }
        }
      }
    }

    // Focus a cover once the section has something to show, so a gamepad works without touching the screen.
    LaunchedEffect(section, library.isNotEmpty(), androidGames.isNotEmpty()) {
        if (pendingFocusKey == null) focusSection(section)
    }
}

private fun focusKeyFor(item: LibraryItem): String = item.appId

/** Steam's library header art ratio (460×215), used for the first, wide tile. */
private const val WIDE_TILE_RATIO = 460f / 215f

/**
 * GameNative's own card badges, kept small: compatibility verdict top left, store icon top right,
 * per-device stats (engine, runs, FPS) along the bottom.
 */
@Composable
private fun BoxScope.CoverBadges(item: LibraryItem, libraryState: LibraryState) {
    val community = libraryState.communityCompatibilityFor(item)
    val status = libraryState.compatibilityMap[item.name]
    val badgeModifier = Modifier.align(Alignment.TopStart).padding(8.dp)
    when {
        community != null -> CommunityCompatibilityBadge(
            verdict = community.verdict,
            verdictLoaded = community.verdictLoaded,
            loadFailed = community.loadFailed,
            checking = community.isChecking,
            showLabel = true,
            modifier = badgeModifier,
        )
        status != null -> CompatibilityBadge(status = status, showLabel = true, modifier = badgeModifier)
    }
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(RiftShapes.full)
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isInstalled) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(6.dp)
                    .clip(RiftShapes.full)
                    .background(RiftColors.Success),
            )
        }
        // Store icons take the content colour; white so they read on the dark pill.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            GameSourceIcon(gameSource = item.gameSource, iconSize = 12, alignmentBoxSize = 20)
        }
    }
    // Device play stats (GameStatsRow) stay on the game page; on the home they clutter the covers.
}

@Composable
private fun HeroLogo(item: LibraryItem?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, bottom = 14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (item != null) GameLogo(item = item)
    }
}

/** What the top bar needs for the library search. */
private class SearchState(
    val open: Boolean,
    val typing: Boolean,
    val query: String,
    val onOpen: () -> Unit,
    val onQuery: (String) -> Unit,
    val onDone: () -> Unit,
)

private fun androidFocusKey(game: AndroidGame): String = "android:${game.packageName}"

@Composable
private fun PcGamesShelf(
    shown: List<LibraryItem>,
    rollIn: Boolean,
    libraryState: LibraryState,
    listState: LazyListState,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    if (shown.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight * 1.2f + 40.dp)
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
        return
    }
    RiftRow(
        state = listState,
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 18.dp, bottom = RowBottomGap),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        itemsIndexed(shown, key = { _, item -> item.appId }) { index, item ->
            // The first tile is the wide one (library header art), the rest are 2:3 covers.
            val wide = index == 0
            val url = if (wide) {
                item.headerImageUrl.ifEmpty { item.heroImageUrl.ifEmpty { item.capsuleImageUrl } }
            } else {
                item.capsuleImageUrl.ifEmpty { item.headerImageUrl }
            }
            val context = LocalContext.current
            // Entrance: the covers on screen roll in from the right one after another.
            val appear = remember { Animatable(if (rollIn) 0f else 1f) }
            val appearSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
            LaunchedEffect(Unit) {
                if (appear.value < 1f) {
                    delay(ROLL_IN_START_MS + ROLL_IN_STEP_MS * index.coerceAtMost(10))
                    appear.animateTo(1f, appearSpec)
                }
            }
            Box(
                modifier = Modifier.graphicsLayer {
                    val p = appear.value
                    translationX = (1f - p) * ROLL_IN_DISTANCE.toPx()
                    alpha = p.coerceIn(0f, 1f)
                },
            ) {
            CoverCard(
                height = cardHeight,
                aspectRatio = if (wide) WIDE_TILE_RATIO else 2f / 3f,
                paletteKey = url,
                loadPalette = { CoverColors.palette(context, url) },
                requestFocus = pendingFocusKey == focusKeyFor(item),
                onFocusRequested = onFocusConsumed,
                onFocused = {
                    onFocused(item)
                    if (index >= shown.size - LOAD_MORE_THRESHOLD) onNearEnd()
                },
                onClick = { onOpen(item) },
                cover = {
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
                        imageModel = { url },
                        imageOptions = ImageOptions(contentScale = ContentScale.Crop, contentDescription = item.name),
                        loading = {},
                        failure = {},
                    )
                },
                badge = { CoverBadges(item, libraryState) },
            )
            }
        }
    }
}

@Composable
private fun rememberAppIcon(packageName: String?): Bitmap? {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(null, packageName) {
        value = packageName?.let { withContext(Dispatchers.IO) { AndroidGames.icon(context, it) } }
    }
    return icon
}

@Composable
private fun AndroidShelf(
    games: List<AndroidGame>,
    listState: LazyListState,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (AndroidGame) -> Unit,
    onOpen: (AndroidGame) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    RiftRow(
        state = listState,
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 18.dp, bottom = RowBottomGap),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        itemsIndexed(games, key = { _, game -> game.packageName }) { index, game ->
            val icon = rememberAppIcon(game.packageName)
            CoverCard(
                height = cardHeight,
                paletteKey = "android:${game.packageName}",
                loadPalette = { AndroidGames.icon(context, game.packageName)?.let(CoverColors::palette) },
                requestFocus = pendingFocusKey == androidFocusKey(game),
                onFocusRequested = onFocusConsumed,
                onFocused = {
                    onFocused(game)
                },
                onClick = { onOpen(game) },
                cover = {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(RiftColors.SurfaceContainerHigh, RiftColors.Surface)))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = game.label,
                                modifier = Modifier.fillMaxWidth(0.55f).clip(RiftShapes.card),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = game.label,
                            color = RiftColors.OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AndroidBackdrop(game: AndroidGame?) {
    val icon = rememberAppIcon(game?.packageName)
    val palette = remember(icon) { icon?.let(CoverColors::palette) }
    val glow = palette?.first() ?: Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1400f, 200f),
                    radius = 1400f,
                ),
            ),
    )
}

@Composable
private fun AndroidHero(game: AndroidGame?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (game == null) return@Column
        Text(
            text = game.label.uppercase(),
            color = RiftColors.OnSurface,
            style = LogoTitleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = LogoWidth),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.rift_source_android),
            color = RiftColors.OnSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
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
    onStep: (Int) -> Unit,
    search: SearchState?,
) {
    // Big Picture style: only the section tabs, between the bumpers that switch them.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BumperButton(GamepadButton.LB.iconRes, "LB") { onStep(-1) }
        sections.forEach { section ->
            SectionTab(
                title = stringResource(section.titleRes),
                selected = section == selected,
                onClick = { onSelect(section) },
            )
        }
        BumperButton(GamepadButton.RB.iconRes, "RB") { onStep(1) }
        Spacer(modifier = Modifier.weight(1f))
        if (search != null) SearchField(search)
    }
}

/** Light, half transparent search: an icon until used, then a slim pill with the query. */
@Composable
private fun SearchField(search: SearchState) {
    if (!search.open) {
        PillButton(onClick = search.onOpen, background = { focused -> solid(if (focused) RiftColors.SurfaceContainerHigh else Color.Transparent) }, square = true) {
            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.rift_search), tint = RiftColors.OnSurfaceVariant.copy(alpha = 0.7f))
        }
        return
    }
    if (!search.typing) {
        // Not typing: a plain pill with the query; A opens the keyboard again.
        PillButton(onClick = search.onOpen, background = { focused -> solid(Color.White.copy(alpha = if (focused) 0.16f else 0.08f)) }, modifier = Modifier.width(320.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.rift_search), tint = RiftColors.OnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Text(
                    text = search.query.ifEmpty { stringResource(R.string.rift_search) },
                    color = if (search.query.isEmpty()) RiftColors.OnSurfaceVariant else RiftColors.OnSurface,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = Modifier
            .height(RiftSizes.button)
            .width(320.dp)
            .clip(RiftShapes.full)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = RiftColors.OnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        BasicTextField(
            value = search.query,
            onValueChange = search.onQuery,
            singleLine = true,
            textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 16.sp),
            cursorBrush = solid(RiftColors.Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search.onDone() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
    }
}

/**
 * The one button shape Rift uses: a full pill (or a circle when square), [RiftSizes.button] tall,
 * white ring and a slight spring scale on focus.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PillButton(
    onClick: () -> Unit,
    background: (focused: Boolean) -> Brush,
    modifier: Modifier = Modifier,
    square: Boolean = false,
    focusRequester: FocusRequester? = null,
    focusable: Boolean = true,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "buttonScale")
    // Expressive press feedback: the pill squeezes toward a rounded square and springs back.
    val corner by animateIntAsState(if (pressed) 30 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "buttonCorner")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .height(RiftSizes.button)
            .then(if (square) Modifier.width(RiftSizes.button) else Modifier.widthIn(min = RiftSizes.button))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background(focused))
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (focusable) Modifier else Modifier.focusProperties { canFocus = false })
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
    // Active tab: dark grey pill with a thin lemon mark; lemon stays an accent.
    PillButton(
        onClick = onClick,
        background = { focused -> solid(if (selected || focused) RiftColors.SurfaceContainerHigh else Color.Transparent) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = if (selected) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(3.dp)
                    .clip(RiftShapes.full)
                    .background(if (selected) RiftColors.Primary else Color.Transparent),
            )
        }
    }
}

@Composable
private fun BumperButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    PillButton(onClick = onClick, background = { solid(Color.Transparent) }, square = true, focusable = false) {
        Image(painter = painterResource(icon), contentDescription = label, modifier = Modifier.size(36.dp))
    }
}

private val LogoWidth = 560.dp
private val LogoHeight = 190.dp

/** The one style for games without a logo image: same size and weight everywhere, never a cover. */
private val LogoTitleStyle = TextStyle(
    color = RiftColors.OnSurface,
    fontSize = 54.sp,
    lineHeight = 56.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = (-0.5).sp,
)

/**
 * The focused game's logo: Steam's transparent library logo when the game has one, otherwise its
 * name in [LogoTitleStyle]. Coil caches the logos.
 */
@Composable
private fun GameLogo(item: LibraryItem) {
    val isSteam = item.gameSource == GameSource.STEAM && item.gameId != 0
    // Resolved off the main thread; null while looking it up, "" when the game has no logo image.
    val logoUrl by produceState<String?>(if (isSteam) null else "", item.appId) {
        value = if (isSteam) withContext(Dispatchers.IO) { GameLogos.steamLogoUrl(item.gameId) } else ""
    }
    val url = logoUrl ?: return
    Crossfade(targetState = item.appId to url, animationSpec = tween(250), label = "logo") { (appId, url) ->
        var logoFailed by remember(appId) { mutableStateOf(url.isEmpty()) }
        Box(modifier = Modifier.width(LogoWidth).height(LogoHeight), contentAlignment = Alignment.BottomStart) {
            if (!logoFailed) {
                CoilImage(
                    modifier = Modifier.fillMaxSize(),
                    imageModel = { url },
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
                    text = item.name,
                    style = LogoTitleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
        background = { RiftBrush.action },
        modifier = Modifier.widthIn(min = 160.dp),
        focusRequester = focusRequester,
    ) {
        Text(text = text, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

private val FooterHeight = 58.dp
private const val STARTUP_SETTLE_MS = 2500L
private const val ROLL_IN_START_MS = 120L
private const val ROLL_IN_STEP_MS = 55L
private val ROLL_IN_DISTANCE = 180.dp
private const val SELECT_HOLD_MS = 450L

/**
 * Frosted glass: the recorded [backdrop], shifted so it lines up with what is behind this box,
 * blurred and darkened. The blur needs Android 12; older systems get the dark tint only.
 */
@Composable
private fun FrostedGlass(backdrop: GraphicsLayer, modifier: Modifier = Modifier) {
    var top by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .onGloballyPositioned { top = it.positionInRoot().y }
            .graphicsLayer {
                clip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(18.dp.toPx(), 18.dp.toPx(), TileMode.Clamp)
                }
            }
            .drawBehind {
                translate(top = -top) { drawLayer(backdrop) }
                drawRect(Color.Black.copy(alpha = 0.28f))
            },
    )
}

/** Frosted glass footer with a hairline on top; the blur itself comes from [modifier]. */
@Composable
private fun HintsBar(showFilters: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FooterHeight)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.12f), Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // GameNative's own Kenney prompts (coloured face buttons), with its A/B X/Y swap setting.
        // Menus on the left, game actions on the right, like Big Picture; the tabs explain LB/RB.
        val swap = PrefManager.swapFaceButtons
        fun face(button: GamepadButton, swapped: GamepadButton) = if (swap) swapped.iconRes else button.iconRes
        if (showFilters) Hint(listOf(GamepadButton.SELECT.iconRes), stringResource(R.string.rift_hint_filters))
        Hint(listOf(GamepadButton.SELECT.iconRes), stringResource(R.string.rift_hint_menu))
        Spacer(modifier = Modifier.weight(1f))
        if (showFilters) Hint(listOf(face(GamepadButton.Y, GamepadButton.X)), stringResource(R.string.rift_hint_options))
        if (showFilters) Hint(listOf(face(GamepadButton.A, GamepadButton.B)), stringResource(R.string.rift_details))
        Hint(listOf(face(GamepadButton.B, GamepadButton.A)), stringResource(R.string.rift_hint_apps))
    }
}

@Composable
private fun Hint(icons: List<Int>, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            icons.forEach { Image(painter = painterResource(it), contentDescription = null, modifier = Modifier.size(28.dp)) }
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
 * Scrolls the row only as far as needed to keep the focused cover (and its lift and glow) in view,
 * Big Picture style, instead of jumping it to the edge.
 */
@OptIn(ExperimentalFoundationApi::class)
private class KeepInView(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = margin
        val end = containerSize - margin
        return when {
            size > end - start || offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RiftRow(
    state: LazyListState,
    contentPadding: PaddingValues,
    horizontalArrangement: Arrangement.Horizontal,
    content: LazyListScope.() -> Unit,
) {
    val margin = with(LocalDensity.current) { (ScreenPadding + 16.dp).toPx() }
    val spec = remember(margin) { KeepInView(margin) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyRow(state = state, contentPadding = contentPadding, horizontalArrangement = horizontalArrangement, content = content)
    }
}

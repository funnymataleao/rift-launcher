package dev.rift.launcher.home

import android.content.SharedPreferences
import android.graphics.Bitmap
import app.gamenative.ui.util.SnackbarManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import timber.log.Timber
import kotlinx.coroutines.flow.first
import app.gamenative.ui.enums.SortOption
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search as SearchFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import app.gamenative.data.FavoritesManager
import app.gamenative.ui.screen.library.components.toggleFavorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
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
import coil.request.ImageRequest
import coil.imageLoader
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import dev.rift.launcher.ui.RiftStartup
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.events.AndroidEvent
import app.gamenative.PluviaApp
import app.gamenative.ui.icons.InputIcons
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
import android.os.SystemClock
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
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
import app.gamenative.ui.screen.library.components.TabFilterGroup
import app.gamenative.ui.screen.library.components.TabFilterOption
import app.gamenative.utils.SteamUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.cloud.CloudGamePage
import dev.rift.launcher.cloud.CloudLibrary
import dev.rift.launcher.cloud.CloudLogos
import dev.rift.launcher.cloud.XcloudAccount
import dev.rift.launcher.cloud.rememberCloudStreamingTab
import dev.rift.launcher.emulators.EmulatorLibrary
import dev.rift.launcher.emulators.toLibraryItem
import dev.rift.launcher.settings.RenameGameDialog
import dev.rift.launcher.art.ArtKind
import dev.rift.launcher.art.GameArtStore
import dev.rift.launcher.meta.RiftAutoMeta
import dev.rift.launcher.ui.BlackGlass
import dev.rift.launcher.ui.LocalGlassBackdrop
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.art.LocalRiftArtPicker
import dev.rift.launcher.art.RiftArtPicker
import dev.rift.launcher.settings.RiftPrefs
import dev.rift.launcher.settings.RiftSettingsSection
import dev.rift.launcher.assistant.chat.AssistantChat
import dev.rift.launcher.assistant.chat.ChatGame
import dev.rift.launcher.assistant.chat.RIFT_ASSISTANT_ROUTE
import app.gamenative.ui.screen.PluviaScreen
import dev.rift.launcher.sound.RiftLayerSound
import dev.rift.launcher.sound.RiftMusicEffect
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.rememberRiftSoundPlayer
import dev.rift.launcher.sound.riftKeySounds
import dev.rift.launcher.stream.PcStreaming
import dev.rift.launcher.stream.PcStreamingTab
import dev.rift.launcher.stream.rememberPcStreamingTab
import dev.rift.launcher.ui.theme.RiftBrush
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftGlassTheme
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftSizes
import dev.rift.launcher.ui.theme.RiftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PANEL_EXIT_REFOCUS_MS = 450L
private const val HIDE_REFOCUS_MS = 500L
/**
 * The one edge inset of the home (Denis): the distance from the screen edge to the first cover. The tabs and
 * the avatar sit this far from the top, the left and right blocks of the top bar and of the hints bar this far
 * from the sides, and the hints this far from the bottom.
 */
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
    RiftMusicEffect()
    val libraryState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(libraryState.visibleLibraryTabs) {
        if (libraryState.currentTab != LibraryTab.ALL && LibraryTab.ALL in libraryState.visibleLibraryTabs) {
            viewModel.onTabChanged(LibraryTab.ALL)
        }
    }

    // The pictures the user picked (art picker) replace the store's, everywhere the games show.
    val pickedArt by GameArtStore.version.collectAsStateWithLifecycle()
    val uninstalledMode by rememberUninstalledMode(RiftSection.PcGames)
    val streamUninstalledMode by rememberUninstalledMode(RiftSection.PcStreaming)
    // Games the person hid (Start menu) are gone from every tab and library.
    val hiddenGames by RiftHiddenGames.hidden(context).collectAsStateWithLifecycle()
    // Names the person gave to Emulators and Android games; loaded before the first list is made.
    val gameNames by RiftGameNames.names(context).collectAsStateWithLifecycle()
    val favoriteIds by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val loadedLibrary = remember(libraryState.appInfoList, pickedArt, uninstalledMode, hiddenGames, gameNames) {
        GameArtStore.apply(
            context,
            libraryState.appInfoList.filterNot {
                it.isRecommended || it.isFeatured || it.isRecTeaser || it.isRecLoading ||
                    it.appId in hiddenGames ||
                    (uninstalledMode == UninstalledMode.HIDE && !it.isInstalled)
            },
        ).map { item -> gameNames[item.appId]?.let { item.copy(name = it) } ?: item }
    }
    // While GameNative reloads the list (it empties it first), keep showing the last one: no flashes.
    var lastLibrary by remember { mutableStateOf(emptyList<LibraryItem>()) }
    val library = if (loadedLibrary.isEmpty() && libraryState.isLoading) lastLibrary else loadedLibrary
    SideEffect { if (loadedLibrary.isNotEmpty() || !libraryState.isLoading) lastLibrary = loadedLibrary }
    // Android games the user added in settings; reloads when that list changes.
    val allAndroidGames by produceState(emptyList<AndroidGame>()) {
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
    val androidGames = remember(allAndroidGames, hiddenGames) { allAndroidGames.filterNot { it.toArtItem().appId in hiddenGames } }
    val pairedPcs by PcStreaming.pairedHosts(context).collectAsStateWithLifecycle()
    // The PC streaming tab exists only while it has games to show; setup hints live in settings.
    val streamTab = if (pairedPcs.isNotEmpty()) rememberPcStreamingTab() else null
    // Each tab keeps its own sort and filter; the filters panel edits the tab that is open.
    // Home streaming starts with the games last streamed from Rift, then the ones newest on the PC.
    var streamView by remember { mutableStateOf(RiftTabFilters.get(context, RiftSection.PcStreaming, TabSort.LAST_PLAYED)) }
    var androidView by remember { mutableStateOf(RiftTabFilters.get(context, RiftSection.AndroidGames)) }
    val streamItems by remember(streamTab, streamView, streamUninstalledMode, pickedArt, hiddenGames, gameNames) { derivedStateOf { GameArtStore.apply(context, streamTab?.arrange(context, streamView.copy(showAll = streamUninstalledMode != UninstalledMode.HIDE)).orEmpty().filterNot { it.appId in hiddenGames }) } }
    val hasStreamGames = streamTab?.items?.any { it.appId !in hiddenGames } == true
    val cloudSignedIn by XcloudAccount.signedIn(context).collectAsStateWithLifecycle()
    // The cloud tab exists only while a cloud account is signed in and its catalog has games; the
    // sign-in and the hints live in settings.
    val cloudTab = if (cloudSignedIn == true) rememberCloudStreamingTab() else null
    // The cloud tab starts on its own order (played from Rift, then Game Pass's most popular).
    var cloudView by remember { mutableStateOf(RiftTabFilters.get(context, RiftSection.CloudStreaming, TabSort.LAST_PLAYED)) }
    val cloudItems = remember(cloudTab, cloudView, hiddenGames, pickedArt, gameNames) { GameArtStore.apply(context, cloudTab?.arrange(cloudView).orEmpty().filterNot { it.appId in hiddenGames }) }
    val hasCloudGames = cloudTab?.items?.any { it.appId !in hiddenGames } == true
    // The Emulators tab exists only while the systems set up in settings have games; the systems, their
    // emulator apps and folders live in settings. The folders are scanned again each time Rift comes back
    // to the front, so a new game, a new system or a newly installed emulator shows up without a restart.
    val emulatorGames by EmulatorLibrary.games.collectAsStateWithLifecycle()
    val homeLifecycle = LocalLifecycleOwner.current
    // The SteamGridDB key, read again each time Rift comes back (it is entered in settings).
    var artKey by remember { mutableStateOf(RiftPrefs.of(context).steamGridDbKey) }
    // The hint footer; the switch lives in settings, so it is read again each time Rift comes back.
    var showHintBar by remember { mutableStateOf(RiftPrefs.of(context).showHintBar) }
    SideEffect { HintBarState.shown = showHintBar }
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                EmulatorLibrary.refresh(context)
                artKey = RiftPrefs.of(context).steamGridDbKey
                showHintBar = RiftPrefs.of(context).showHintBar
            }
        }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }
    // The description, rating and tags of every Android and emulator game are fetched as soon as the game is
    // in the library (needs the IGDB keys from settings; asked again each time the home comes back).
    var metaVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(homeLifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) metaVersion++ }
        homeLifecycle.lifecycle.addObserver(observer)
        onDispose { homeLifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(emulatorGames, androidGames, metaVersion, gameNames) {
        withContext(Dispatchers.IO) {
            RiftAutoMeta.prefetch(
                context,
                emulatorGames.map { it.toLibraryItem() to it.platformId } + androidGames.map { it.toPageItem() to null },
            )
        }
    }
    var emulatorView by remember { mutableStateOf(RiftTabFilters.get(context, RiftSection.Emulators, TabSort.LAST_PLAYED)) }
    val emulatorItems by remember(emulatorGames, emulatorView, pickedArt, hiddenGames, gameNames) {
        derivedStateOf { GameArtStore.apply(context, EmulatorLibrary.arrange(context, emulatorGames, emulatorView).filterNot { it.appId in hiddenGames }) }
    }
    val hasEmulatorGames = emulatorGames.any { it.appId !in hiddenGames }
    // The streaming, cloud and emulator lists load a moment after the home comes back (from settings, a game
    // page, a game); until they have, the tabs they had last time stay, so the open tab never jumps to
    // PC Games and back.
    var sourcesGrace by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SOURCES_GRACE_MS)
        sourcesGrace = false
    }
    val stickyStream = hasStreamGames || (sourcesGrace && RiftTabsMemory.stream)
    val stickyCloud = hasCloudGames || (sourcesGrace && RiftTabsMemory.cloud)
    val stickyEmulators = hasEmulatorGames || (sourcesGrace && RiftTabsMemory.emulators)
    SideEffect {
        if (!sourcesGrace || hasStreamGames) RiftTabsMemory.stream = hasStreamGames
        if (!sourcesGrace || hasCloudGames) RiftTabsMemory.cloud = hasCloudGames
        if (!sourcesGrace || hasEmulatorGames) RiftTabsMemory.emulators = hasEmulatorGames
    }
    val sections = remember(library, androidGames, stickyStream, stickyCloud, stickyEmulators) {
        RiftSections.configured(context, library, androidGames, stickyStream, stickyCloud, stickyEmulators)
    }

    // Start-up: pure black until there is something to show (or, with nothing set up, until the
    // stores have had a moment to sign in), then one entrance: the art drifts in with a slight zoom
    // and the covers roll in from the right one after another.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STARTUP_SETTLE_MS)
        settled = true
    }
    val ready = library.isNotEmpty() || androidGames.isNotEmpty() || hasStreamGames || hasCloudGames || hasEmulatorGames || (settled && !libraryState.isLoading)
    var introPlayed by rememberSaveable { mutableStateOf(false) }
    val intro = remember { Animatable(if (introPlayed) 1f else 0f) }
    // The covers roll in together with the backdrop's entrance, after the curtain, never behind it.
    val introRunning by remember { derivedStateOf { intro.value > 0f } }
    val introSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(ready) {
        if (ready && !introPlayed) {
            // The curtain stays until the first covers and the backdrop are in the image cache, so the
            // entrance never shows grey placeholders; then it fades and the entrance plays.
            if (!RiftStartup.homeReady) {
                withTimeoutOrNull(FIRST_IMAGES_TIMEOUT_MS) { preloadFirstImages(context, library) }
                RiftStartup.homeReady = true
                // Marks the moment the home is really ready, for start-up traces.
                (context as? android.app.Activity)?.reportFullyDrawn()
                delay(RiftStartup.CURTAIN_FADE_MS.toLong())
            }
            intro.animateTo(1f, introSpec)
            introPlayed = true
        } else if (ready) {
            RiftStartup.homeReady = true
        }
    }
    var sectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val section = sections.firstOrNull { it.key == sectionKey } ?: sections.firstOrNull()

    var focusedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedCloudId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedEmulatorId by rememberSaveable { mutableStateOf<String?>(null) }
    var emulatorCardFocused by rememberSaveable { mutableStateOf(false) }
    // The Emulators library shows all games or one system's; null is all.
    var emulatorLibraryTab by rememberSaveable { mutableStateOf<String?>(null) }
    var cloudCardFocused by rememberSaveable { mutableStateOf(false) }
    var streamCardFocused by rememberSaveable { mutableStateOf(false) }
    // The home streaming library's tab: all games, the ones on the PC, the ones not on it.
    var streamLibraryTab by rememberSaveable { mutableStateOf(STREAM_TAB_ALL) }
    var openedAppId by rememberSaveable { mutableStateOf<String?>(null) }
    // The open game page was opened from the PC streaming tab: its main button streams the game.
    var openedForStream by rememberSaveable { mutableStateOf(false) }
    // The open game page is a cloud game's: its main button plays it in the cloud.
    var openedForCloud by rememberSaveable { mutableStateOf(false) }
    var lastOpenedItem by remember { mutableStateOf<LibraryItem?>(null) }
    // The art picker (SteamGridDB) for one game; opened from the game's options or Y on a streamed game.
    var artItem by remember { mutableStateOf<LibraryItem?>(null) }
    var artToken by remember { mutableIntStateOf(0) }
    // "Choose game info": the game and its emulator system while the picker is open.
    var metaItem by remember { mutableStateOf<LibraryItem?>(null) }
    var metaPlatform by remember { mutableStateOf<String?>(null) }
    var metaToken by remember { mutableIntStateOf(0) }
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }

    // Row positions live here, above the game page, so coming back (or switching tabs) keeps them.
    val pcRowState = rememberLazyListState()
    val androidRowState = rememberLazyListState()
    val streamRowState = rememberLazyListState()
    val cloudRowState = rememberLazyListState()
    val emulatorRowState = rememberLazyListState()
    val filterKey = listOf(libraryState.appInfoSortType.toList(), libraryState.currentSortOption, streamView, streamUninstalledMode, uninstalledMode, androidView, cloudView, emulatorView)
    var lastFilterKey by remember { mutableStateOf(filterKey) }
    LaunchedEffect(filterKey) {
        if (filterKey != lastFilterKey) {
            lastFilterKey = filterKey
            pcRowState.scrollToItem(0)
        }
    }

    // Big Picture: the home row has only the latest games and ends in a card that opens the whole
    // library, a grid with the tab's sources (the stores) as tabs.
    val homeRow = library.take(HOME_ROW_COUNT)
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    var libraryCardFocused by rememberSaveable { mutableStateOf(false) }
    var libraryFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryPending by remember { mutableStateOf<String?>(null) }
    var libraryTabSwitch by remember { mutableIntStateOf(0) }
    val focusedItem = homeRow.firstOrNull { it.appId == focusedAppId } ?: homeRow.firstOrNull()
    val androidShown = remember(androidGames, androidView) {
        val opened = AndroidGames.lastOpened(context)
        when (androidView.sort) {
            TabSort.NAME_ASC -> androidGames.sortedBy { it.label.lowercase() }
            TabSort.NAME_DESC -> androidGames.sortedByDescending { it.label.lowercase() }
            TabSort.DATE_ADDED -> androidGames.sortedWith(compareByDescending<AndroidGame> { it.installedAt }.thenBy { it.label.lowercase() })
            TabSort.LAST_PLAYED -> androidGames.sortedWith(compareByDescending<AndroidGame> { opened[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
        }
    }
    // Emulator and Android games have no store art: as soon as a tab has games, Rift finds each one's cover,
    // wide picture, banner and logo on SteamGridDB (by name), the games on screen first, and they appear one by one.
    LaunchedEffect(emulatorGames, androidGames, artKey, gameNames) {
        if (artKey.isBlank()) return@LaunchedEffect
        val order = withContext(Dispatchers.Default) {
            val emulators = EmulatorLibrary.arrange(context, emulatorGames, emulatorView).map { it.copy(isInstalled = true) }
            val android = androidShown.map { it.toArtItem() }
            (emulators + android).mapIndexed { index, item ->
                // The wide picture is only the first tile's: the first few games of each tab, in case the order changes.
                val kinds = if (index < WIDE_ART_GAMES || index >= emulators.size && index - emulators.size < WIDE_ART_GAMES) {
                    listOf(ArtKind.Capsule, ArtKind.Wide, ArtKind.Hero, ArtKind.Logo)
                } else {
                    listOf(ArtKind.Capsule, ArtKind.Hero, ArtKind.Logo)
                }
                item to kinds
            }
        }
        GameArtStore.autoFill(context, order, artKey)
    }
    // Streamed games the Steam account does not own (Steam Tools...) have no Steam art either: found the same way.
    val hostGames = streamTab?.items.orEmpty().filter { PcStreamingTab.isHostGame(it.appId) }
    LaunchedEffect(hostGames, artKey) {
        if (artKey.isBlank() || hostGames.isEmpty()) return@LaunchedEffect
        GameArtStore.autoFill(context, hostGames.map { it to listOf(ArtKind.Capsule, ArtKind.Wide, ArtKind.Hero, ArtKind.Logo) }, artKey)
    }
    val focusedAndroid = androidShown.firstOrNull { it.packageName == focusedPackage } ?: androidShown.firstOrNull()
    // The focused Android game with the pictures found or picked for it: banner behind, logo over it.
    val focusedAndroidItem = remember(focusedAndroid, pickedArt, gameNames) { focusedAndroid?.let { GameArtStore.apply(context, it.toArtItem()) } }
    // Like PC games (Denis): the row has the latest games and ends in "Open library", not hundreds of covers.
    val streamHomeRow = streamItems.take(HOME_ROW_COUNT)
    val streamLibraryItems = when (streamLibraryTab) {
        STREAM_TAB_INSTALLED -> streamItems.filter { it.isInstalled }
        STREAM_TAB_NOT_INSTALLED -> streamItems.filterNot { it.isInstalled }
        else -> streamItems
    }
    val focusedStream = streamHomeRow.firstOrNull { it.appId == focusedStreamId } ?: streamHomeRow.firstOrNull()
    val cloudHomeRow = cloudItems.take(HOME_ROW_COUNT)
    val focusedCloud = cloudHomeRow.firstOrNull { it.appId == focusedCloudId } ?: cloudHomeRow.firstOrNull()
    val emulatorHomeRow = emulatorItems.take(HOME_ROW_COUNT)
    val focusedEmulator = emulatorHomeRow.firstOrNull { it.appId == focusedEmulatorId } ?: emulatorHomeRow.firstOrNull()
    // Coming back (from Settings, a game page, AI Help...) the lists load again, and the cover that had focus may
    // not be in the first batch. Until it shows up the focus waits instead of landing on whatever is first (and
    // then being remembered as the place); if it never shows up (the game is gone) the first cover takes over.
    val pcSaved = focusedAppId == null || homeRow.any { it.appId == focusedAppId }
    val androidSaved = focusedPackage == null || androidShown.any { it.packageName == focusedPackage }
    val streamSaved = focusedStreamId == null || streamHomeRow.any { it.appId == focusedStreamId }
    val cloudSaved = focusedCloudId == null || cloudHomeRow.any { it.appId == focusedCloudId }
    val emulatorSaved = focusedEmulatorId == null || emulatorHomeRow.any { it.appId == focusedEmulatorId }
    // The library's games: every system, or the one picked with LB/RB.
    val emulatorLibraryItems = remember(emulatorItems, emulatorGames, emulatorLibraryTab) {
        val tab = emulatorLibraryTab
        if (tab == null) {
            emulatorItems
        } else {
            val ids = emulatorGames.filter { it.platformId == tab }.map { it.appId }.toSet()
            emulatorItems.filter { it.appId in ids }
        }
    }

    fun openGame(item: LibraryItem, forStream: Boolean = false) {
        lastOpenedItem = item
        openedForStream = forStream
        openedForCloud = CloudLibrary.isCloud(item.appId)
        openedAppId = item.appId
    }

    /** Puts focus back on the cover this section had last. */
    fun focusSection(target: RiftSection?) {
        // The game page has its own focus; the home picks its cover again when the page closes.
        if (openedAppId != null || (artItem != null || metaItem != null)) return
        // In the library, the grid takes it back on the game it had last.
        if (libraryOpen) {
            val items = when (target) {
                RiftSection.CloudStreaming -> cloudItems
                RiftSection.PcStreaming -> streamLibraryItems
                RiftSection.Emulators -> emulatorLibraryItems
                else -> library
            }
            val saved = libraryFocusedId
            libraryPending = when {
                saved == null -> items.firstOrNull()?.appId
                items.any { it.appId == saved } -> saved
                else -> items.firstOrNull()?.appId
            }
            return
        }
        // The cover may be scrolled out of the row (a search changes it), so bring it into view first.
        if (target == RiftSection.PcGames) {
            val index = if (libraryCardFocused) homeRow.size else focusedItem?.let(homeRow::indexOf) ?: -1
            if (index >= 0 && pcRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { pcRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        // A game played from the cloud tab moves to the front of its row.
        if (target == RiftSection.CloudStreaming) {
            val index = if (cloudCardFocused) cloudHomeRow.size else focusedCloud?.let(cloudHomeRow::indexOf) ?: -1
            if (index >= 0 && cloudRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { cloudRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        if (target == RiftSection.Emulators) {
            val index = if (emulatorCardFocused) emulatorHomeRow.size else focusedEmulator?.let(emulatorHomeRow::indexOf) ?: -1
            if (index >= 0 && emulatorRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { emulatorRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        if (target == RiftSection.PcStreaming) {
            val index = if (streamCardFocused) streamHomeRow.size else focusedStream?.let(streamHomeRow::indexOf) ?: -1
            if (index >= 0 && streamRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { streamRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        if (target == RiftSection.AndroidGames) {
            val index = focusedAndroid?.let { androidShown.indexOf(it) } ?: -1
            if (index >= 0 && androidRowState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                homeScope.launch { androidRowState.scrollToItem((index - 1).coerceAtLeast(0)) }
            }
        }
        pendingFocusKey = when (target) {
            RiftSection.PcGames -> if (libraryCardFocused && homeRow.isNotEmpty()) LIBRARY_CARD_KEY else if (pcSaved) focusedItem?.let(::focusKeyFor) else null
            RiftSection.AndroidGames -> if (androidSaved) focusedAndroid?.let(::androidFocusKey) else null
            RiftSection.PcStreaming -> if (streamCardFocused && streamHomeRow.isNotEmpty()) LIBRARY_CARD_KEY else if (streamSaved) focusedStream?.let(::focusKeyFor) else null
            RiftSection.CloudStreaming -> if (cloudCardFocused && cloudHomeRow.isNotEmpty()) LIBRARY_CARD_KEY else if (cloudSaved) focusedCloud?.let(::focusKeyFor) else null
            RiftSection.Emulators -> if (emulatorCardFocused && emulatorHomeRow.isNotEmpty()) LIBRARY_CARD_KEY else if (emulatorSaved) focusedEmulator?.let(::focusKeyFor) else null
            null -> null
        }
    }

    // The side a tab switch goes to, so the new shelf's covers slide in from there; cleared once in.
    var switchDirection by remember { mutableIntStateOf(0) }
    LaunchedEffect(switchDirection) {
        if (switchDirection != 0) {
            delay(SWITCH_ENTRY_MS)
            switchDirection = 0
        }
    }
    // Covers always come in from the right and travel left, like at start (Denis), whichever way the switch goes.
    val switchEntry = if (switchDirection != 0) ShelfEntry.switch(1) else null

    fun selectSection(target: RiftSection) {
        val from = section?.let(sections::indexOf) ?: -1
        val to = sections.indexOf(target)
        if (from >= 0 && to >= 0 && from != to) switchDirection = if (to > from) 1 else -1
        sectionKey = target.key
        focusSection(target)
    }

    fun switchSection(step: Int) {
        if (sections.size < 2 || section == null) return
        val target = sections[(sections.indexOf(section) + step).mod(sections.size)]
        // Switching with LB/RB always lands on the first card of the tab, not where it was left (Denis).
        when (target) {
            RiftSection.PcGames -> { focusedAppId = null; libraryCardFocused = false; homeScope.launch { pcRowState.scrollToItem(0) } }
            RiftSection.PcStreaming -> { focusedStreamId = null; homeScope.launch { streamRowState.scrollToItem(0) } }
            RiftSection.AndroidGames -> { focusedPackage = null; homeScope.launch { androidRowState.scrollToItem(0) } }
            RiftSection.Emulators -> { focusedEmulatorId = null; emulatorCardFocused = false; homeScope.launch { emulatorRowState.scrollToItem(0) } }
            RiftSection.CloudStreaming -> { focusedCloudId = null; cloudCardFocused = false; homeScope.launch { cloudRowState.scrollToItem(0) } }
        }
        selectSection(target)
        // Wrapping round still slides the way the bumper points.
        switchDirection = if (step > 0) 1 else -1
    }

    // The game page opens over the home instead of replacing it, so the home keeps its state, its
    // backdrop and its focus, and both ways are one smooth fade and scale (Denis: the jump was jarring).
    val opened = openedAppId?.let { id -> library.firstOrNull { it.appId == id } ?: lastOpenedItem?.takeIf { it.appId == id } }
    var pageItem by remember { mutableStateOf<LibraryItem?>(null) }
    if (opened != null) pageItem = opened
    val pageState = remember { MutableTransitionState(false) }
    pageState.targetState = opened != null
    // Once the page fully covers the home, the home is not drawn at all.
    val homeCovered = pageState.currentState && pageState.isIdle
    val pageFocus = remember { FocusRequester() }
    var pageFocused by remember { mutableStateOf(false) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // The page takes focus itself (its Play button); if that did not happen, focus goes into it.
    LaunchedEffect(opened != null) {
        if (opened != null) {
            delay(PAGE_FOCUS_CHECK_MS)
            if (!pageFocused) runCatching { pageFocus.requestFocus() }
        }
    }
    fun closePage() {
        val item = opened ?: return
        openedAppId = null
        if (libraryOpen) libraryPending = item.appId else pendingFocusKey = focusKeyFor(item)
    }

    // The library always opens on "All games" and the home row is always all of them.
    fun openLibrary() {
        if (libraryState.currentTab != LibraryTab.ALL) viewModel.onTabChanged(LibraryTab.ALL)
        libraryFocusedId = null
        libraryOpen = true
        libraryTabSwitch++
    }
    // The cloud tab's library: every cloud game, one tab per cloud service.
    fun openCloudLibrary() {
        libraryFocusedId = null
        libraryOpen = true
        libraryTabSwitch++
    }
    // The home streaming library opens on all games too.
    fun openStreamLibrary() {
        streamLibraryTab = STREAM_TAB_ALL
        libraryFocusedId = null
        libraryOpen = true
        libraryTabSwitch++
    }
    // The Emulators tab's library: every game of every system, one tab per system.
    fun openEmulatorLibrary() {
        emulatorLibraryTab = null
        libraryFocusedId = null
        libraryOpen = true
        libraryTabSwitch++
    }
    fun closeLibrary() {
        libraryOpen = false
        if (viewModel.state.value.currentTab != LibraryTab.ALL) viewModel.onTabChanged(LibraryTab.ALL)
    }

    // Rift's home row shows the latest games: GameNative's "recently played" order (installed first,
    // then last played) becomes the default once; a sort picked later in the filters is kept.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("rift_prefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean(RECENT_SORT_DEFAULT_KEY, false)) {
            if (viewModel.state.value.currentSortOption == SortOption.INSTALLED_FIRST) {
                viewModel.onSortOptionChanged(SortOption.RECENTLY_PLAYED)
            }
            prefs.edit().putBoolean(RECENT_SORT_DEFAULT_KEY, true).apply()
        }
    }

    // B on the home does nothing, like Big Picture (and a HOME launcher has nowhere to go back to).
    BackHandler { }

    // Search: a quiet icon in the top bar; typing filters the row through GameNative's library search.
    // With a gamepad: A on the icon starts typing, B (or Down) hides the keyboard and goes back to the
    // row with the results, B again closes the search. The field only takes focus while typing.
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchTyping by remember { mutableStateOf(false) }
    // Up from the row reaches the search and the avatar only; the tabs are LB/RB's.
    val searchFocus = remember { FocusRequester() }
    val avatarFocus = remember { FocusRequester() }
    var topFocused by remember { mutableStateOf(false) }
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
    fun openArt(item: LibraryItem) {
        if (RiftPrefs.of(context).steamGridDbKey.isBlank()) {
            // No key yet: the settings page that takes it.
            RiftSettingsSection.request(RiftSettingsSection.Artwork)
            onNavigateRoute(PluviaScreen.Settings.route)
            return
        }
        // The options panel (if that is where this came from) is done; its game screen goes away.
        homeScope.launch { optionsItem = null }
        artToken++
        artItem = item
    }
    val latestOpenArt = rememberUpdatedState<(LibraryItem) -> Unit> { openArt(it) }
    val artOpener = remember<(LibraryItem) -> Unit> { { item -> latestOpenArt.value(item) } }
    // Start on the other tabs: the short menu of that tab (null = closed); the last list stays for the fade-out.
    var startMenuItems by remember { mutableStateOf<List<RiftStartMenuItem>?>(null) }
    // The game being renamed: its id, the name it has now and its own name.
    var renameTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var libraryFiltersOpen by remember { mutableStateOf(false) }
    var filterGroupIndex by remember { mutableStateOf<Int?>(null) }
    var lastFilterItems by remember { mutableStateOf<List<RiftStartMenuItem>>(emptyList()) }
    LaunchedEffect(libraryOpen) { if (!libraryOpen) { libraryFiltersOpen = false; filterGroupIndex = null } }
    // The library's own menus hand the cursor back to the game it was on when they close.
    val libraryMenuOpen = libraryFiltersOpen || startMenuItems != null
    var wasLibraryMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(libraryMenuOpen) {
        if (wasLibraryMenuOpen && !libraryMenuOpen && libraryOpen && optionsItem == null && (artItem == null && metaItem == null) && openedAppId == null) {
            libraryPending = libraryFocusedId
        }
        wasLibraryMenuOpen = libraryMenuOpen
    }
    var lastStartMenuItems by remember { mutableStateOf<List<RiftStartMenuItem>>(emptyList()) }
    if (startMenuItems != null) lastStartMenuItems = startMenuItems!!
    // Hiding a game: the cursor moves to the next card (the one before it if this was the last) before the card
    // goes, so the focus is never left on a card that is no longer there. The last game of a library closes it.
    fun hideGame(appId: String, name: String) {
        fun neighbour(ids: List<String>): String? {
            val i = ids.indexOf(appId)
            return if (i < 0) null else ids.getOrNull(i + 1) ?: ids.getOrNull(i - 1)
        }
        val next: String? = when (section) {
            RiftSection.AndroidGames -> neighbour(androidShown.map { it.toArtItem().appId })?.let { id ->
                androidShown.firstOrNull { it.toArtItem().appId == id }?.also { focusedPackage = it.packageName }?.let(::androidFocusKey)
            }
            RiftSection.PcGames -> neighbour((if (libraryOpen) library else homeRow).map { it.appId })?.also { focusedAppId = it }
            RiftSection.PcStreaming -> neighbour((if (libraryOpen) streamLibraryItems else streamHomeRow).map { it.appId })?.also { focusedStreamId = it }
            RiftSection.CloudStreaming -> neighbour((if (libraryOpen) cloudItems else cloudHomeRow).map { it.appId })?.also { focusedCloudId = it }
            RiftSection.Emulators -> neighbour((if (libraryOpen) emulatorLibraryItems else emulatorHomeRow).map { it.appId })?.also { focusedEmulatorId = it }
            else -> null
        }
        RiftHiddenGames.hide(context, appId, name)
        if (next == null) {
            if (libraryOpen) closeLibrary()
            return
        }
        fun assert() {
            if (libraryOpen) {
                libraryFocusedId = next
                libraryPending = next
            } else {
                pendingFocusKey = next
            }
        }
        assert()
        // Again once the menu that hid it has faded out and given its focus back.
        homeScope.launch {
            delay(HIDE_REFOCUS_MS)
            assert()
        }
    }
    SideEffect { optionsOnly.onHide = ::hideGame }
    fun openMetaPicker(item: LibraryItem, platformId: String?) {
        metaToken++
        metaPlatform = platformId
        metaItem = item
    }
    // "Update game info": every source again; when nothing (new) turns up, the list to choose from opens.
    fun refreshMeta(item: LibraryItem, platformId: String?) {
        if (RiftAutoMeta.isPinned(context, item.appId)) {
            openMetaPicker(item, platformId)
            return
        }
        SnackbarManager.show(context.getString(R.string.rift_meta_refreshing))
        homeScope.launch {
            // A PC game's page shows the stores' own data first: they are asked again as well, and the page reads it anew.
            app.gamenative.data.StoreDetailsRepository.clearCache()
            val meta = withContext(Dispatchers.IO) { RiftAutoMeta.refresh(context, item, platformId) }
            RiftAutoMeta.notifyChanged()
            if (meta != null || section == RiftSection.PcGames) {
                SnackbarManager.show(context.getString(R.string.rift_meta_refreshed))
            } else {
                openMetaPicker(item, platformId)
            }
        }
    }
    // The same game-info and name lines on GameNative's own game screens (PC games).
    val latestRefreshMeta = rememberUpdatedState<(LibraryItem) -> Unit> { refreshMeta(it, null) }
    val latestOpenMeta = rememberUpdatedState<(LibraryItem) -> Unit> { openMetaPicker(it, null) }
    val latestRename = rememberUpdatedState<(LibraryItem) -> Unit> {
        renameTarget = Triple(it.appId, it.name, RiftGameNames.original(context, it.appId, it.name))
    }
    val infoActions = remember {
        dev.rift.launcher.meta.RiftInfoActions(
            refresh = { latestRefreshMeta.value(it) },
            choose = { latestOpenMeta.value(it) },
            rename = { latestRename.value(it) },
        )
    }
    fun otherTabMenu(picked: LibraryItem? = null): List<RiftStartMenuItem>? {
        fun line(@androidx.annotation.StringRes label: Int, icon: ImageVector, action: () -> Unit) =
            RiftStartMenuItem(context.getString(label), icon = icon) { startMenuItems = null; action() }
        val cancel = line(R.string.rift_menu_cancel, Icons.Default.Close) {}
        fun favorite(appId: String, name: String): RiftStartMenuItem {
            val on = appId in favoriteIds
            return line(if (on) R.string.option_remove_from_favorites else R.string.option_add_to_favorites, if (on) Icons.Default.Star else Icons.Default.StarOutline) {
                toggleFavorite(context, appId, name)
            }
        }
        fun properties(open: () -> Unit) = line(R.string.rift_menu_properties, Icons.Default.Settings) {
            RiftPropertiesRequest.request()
            open()
        }
        return when (section) {
            RiftSection.AndroidGames -> focusedAndroid?.let { game ->
                val art = game.toArtItem()
                listOf(
                    line(R.string.rift_menu_play, Icons.Default.PlayArrow) { AndroidGames.launch(context, game) },
                    line(R.string.rift_option_change_art, Icons.Default.ImageIcon) { openArt(art) },
                    favorite(art.appId, game.label),
                    line(R.string.rift_option_rename, Icons.Default.Edit) { renameTarget = Triple(art.appId, art.name, game.label) },
                    line(R.string.rift_menu_refresh_meta, Icons.Default.Refresh) { refreshMeta(game.toPageItem(), null) },
                    line(R.string.rift_menu_pick_meta, Icons.Default.SearchFilled) { openMetaPicker(game.toPageItem(), null) },
                    line(R.string.rift_menu_hide, Icons.Default.VisibilityOff) { hideGame(art.appId, game.label) },
                    properties { openGame(game.toPageItem()) },
                    cancel,
                )
            }
            RiftSection.PcStreaming -> (picked ?: focusedStream)?.let { item ->
                listOf(
                    line(R.string.rift_menu_stream, Icons.Default.Cast) { openGame(item, forStream = true) },
                    line(R.string.rift_option_change_art, Icons.Default.ImageIcon) { openArt(item) },
                    favorite(item.appId, item.name),
                    line(R.string.rift_option_rename, Icons.Default.Edit) {
                        renameTarget = Triple(item.appId, item.name, RiftGameNames.original(context, item.appId, item.name))
                    },
                    line(R.string.rift_menu_refresh_meta, Icons.Default.Refresh) { refreshMeta(item, null) },
                    line(R.string.rift_menu_pick_meta, Icons.Default.SearchFilled) { openMetaPicker(item, null) },
                    line(R.string.rift_menu_hide, Icons.Default.VisibilityOff) { hideGame(item.appId, item.name) },
                    cancel,
                )
            }
            RiftSection.Emulators -> (picked ?: focusedEmulator)?.let { item ->
                listOf(
                    line(R.string.rift_menu_play, Icons.Default.PlayArrow) { EmulatorLibrary.game(item.appId)?.let { EmulatorLibrary.launch(context, it) } },
                    line(R.string.rift_option_change_art, Icons.Default.ImageIcon) { openArt(item) },
                    favorite(item.appId, item.name),
                    line(R.string.rift_option_rename, Icons.Default.Edit) {
                        renameTarget = Triple(item.appId, item.name, EmulatorLibrary.game(item.appId)?.name ?: item.name)
                    },
                    line(R.string.rift_menu_refresh_meta, Icons.Default.Refresh) { refreshMeta(item, EmulatorLibrary.game(item.appId)?.platformId) },
                    line(R.string.rift_menu_pick_meta, Icons.Default.SearchFilled) { openMetaPicker(item, EmulatorLibrary.game(item.appId)?.platformId) },
                    line(R.string.rift_menu_hide, Icons.Default.VisibilityOff) { hideGame(item.appId, item.name) },
                    properties { openGame(item) },
                    cancel,
                )
            }
            RiftSection.CloudStreaming -> (picked ?: focusedCloud)?.let { item ->
                listOf(
                    line(R.string.rift_menu_play, Icons.Default.PlayArrow) { openGame(item) },
                    line(R.string.rift_option_change_art, Icons.Default.ImageIcon) { openArt(item) },
                    favorite(item.appId, item.name),
                    line(R.string.rift_option_rename, Icons.Default.Edit) {
                        renameTarget = Triple(item.appId, item.name, RiftGameNames.original(context, item.appId, item.name))
                    },
                    line(R.string.rift_menu_refresh_meta, Icons.Default.Refresh) { refreshMeta(item, null) },
                    line(R.string.rift_menu_pick_meta, Icons.Default.SearchFilled) { openMetaPicker(item, null) },
                    line(R.string.rift_menu_hide, Icons.Default.VisibilityOff) { hideGame(item.appId, item.name) },
                    cancel,
                )
            }
            else -> null
        }
    }
    // B on the home: every installed Android app in a grid, Beacon style.
    var appsOpen by rememberSaveable { mutableStateOf(false) }
    val appsFocus = remember { FocusRequester() }
    var appsFocused by remember { mutableStateOf(false) }
    // Load the app list ahead (after start-up), so B opens the grid at once.
    LaunchedEffect(Unit) {
        delay(APPS_PRELOAD_DELAY_MS)
        withContext(Dispatchers.IO) { runCatching { AndroidGames.launchable(context) } }
    }
    var backDownHere by remember { mutableStateOf(false) }
    var panelHasFocus by remember { mutableStateOf(false) }
    var panelFocusAsk by remember { mutableIntStateOf(0) }
    LaunchedEffect(panelOpen) { if (!panelOpen) panelHasFocus = false }
    // The last tile of a row ("Open library") is not a game: Start/Y have nothing to open on it.
    val onServiceCard = when (section) {
        RiftSection.PcGames -> libraryCardFocused && homeRow.isNotEmpty()
        RiftSection.PcStreaming -> streamCardFocused && streamHomeRow.isNotEmpty()
        RiftSection.CloudStreaming -> cloudCardFocused && cloudHomeRow.isNotEmpty()
        RiftSection.Emulators -> emulatorCardFocused && emulatorHomeRow.isNotEmpty()
        else -> false
    }
    val overlayOpen = panelOpen || systemMenuOpen || gameOptionsOpen || startMenuItems != null || appsOpen || libraryOpen || (artItem != null || metaItem != null)
    RiftLayerSound(panelOpen)
    RiftLayerSound(systemMenuOpen)
    RiftLayerSound(gameOptionsOpen)
    RiftLayerSound(startMenuItems != null)
    RiftLayerSound(appsOpen)
    RiftLayerSound(libraryOpen)
    RiftLayerSound((artItem != null || metaItem != null))
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
        // Back from a stream the game's page is still open: the focus goes into it.
        if (openedAppId != null && (artItem == null && metaItem == null) && !appsOpen) {
            if (!pageFocused) runCatching { pageFocus.requestFocus() }
            return
        }
        // Behind the apps grid the covers must not take focus; the grid keeps its own.
        if (appsOpen || openedAppId != null || (artItem != null || metaItem != null)) return
        // Across a frame, so the cover sees its focus request change even if it was the last one.
        pendingFocusKey = null
        libraryPending = null
        homeScope.launch {
            withFrameNanos { }
            focusSection(section)
        }
    }
    LaunchedEffect(Unit) { if (openedAppId == null) runCatching { rootFocus.requestFocus() } }
    // Safety net at the activity level (GameNative's key event bus sees every key first): any press
    // while nothing in this screen has focus puts focus back on a cover and is not passed on.
    // The listener lives across recompositions: it calls the latest versions of these.
    val latestClosePage = rememberUpdatedState<() -> Unit> { closePage() }
    val latestRefocus = rememberUpdatedState<() -> Unit> { refocusCover() }
    val latestLayerOpen = rememberUpdatedState<() -> Boolean> {
        systemMenuOpen || searchOpen || viewModel.state.value.isOptionsPanelOpen
    }
    DisposableEffect(composeView) {
        val listener: (AndroidEvent.KeyEvent) -> Boolean = { androidEvent ->
            val ev = androidEvent.event
            val isBackKey = ev.keyCode == KeyEvent.KEYCODE_BUTTON_B || ev.keyCode == KeyEvent.KEYCODE_BACK ||
                ev.keyCode == KeyEvent.KEYCODE_ESCAPE
            if (ev.keyCode == KeyEvent.KEYCODE_BACK) {
                // System Back (the edge swipe without predictive back, or a Back key): every layer
                // closes through its BackHandler. GameNative's activity swallows BACK while Steam
                // stays alive, so it is sent to the back dispatcher from here.
                if (ev.action == KeyEvent.ACTION_UP && !ev.isCanceled) backDispatcher?.onBackPressed()
                true
            } else if ((artItem != null || metaItem != null)) {
                // The art picker has its own keys.
                false
            } else if (appsOpen && !appsFocused && isBackKey) {
                // B with the apps grid open but its focus gone (the focused tile scrolled away): no key
                // reaches the grid, so the first B closes it from here (Denis: it took a second B).
                if (ev.action == KeyEvent.ACTION_UP) appsOpen = false
                true
            } else if (appsOpen && !appsFocused && ev.action == KeyEvent.ACTION_DOWN) {
                if (!composeView.hasFocus()) composeView.requestFocus()
                runCatching { appsFocus.requestFocus() }
                true
            } else if (openedAppId != null && !pageFocused && isBackKey) {
                // B on the game page with focus somewhere else: still back to the home.
                if (ev.action == KeyEvent.ACTION_UP) latestClosePage.value()
                true
            } else if (openedAppId != null && !pageFocused && ev.action == KeyEvent.ACTION_DOWN) {
                runCatching { pageFocus.requestFocus() }
                true
            } else if (ev.action == KeyEvent.ACTION_DOWN && !composeView.hasFocus() && composeView.hasWindowFocus()) {
                latestRefocus.value()
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
        var lastResume = 0L
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                // Coming back from a game gives two resumes within a second: one refocus is enough, and
                // a layer that is open (the drawer, the panels, the search) keeps the focus it has.
                val now = SystemClock.elapsedRealtime()
                val twice = now - lastResume < 1500
                lastResume = now
                if (!twice && !latestLayerOpen.value()) latestRefocus.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Home pressed while Rift is the home app: every layer closes, back to the row of covers.
    val latestHomePressed = rememberUpdatedState<() -> Unit> {
        if (searchOpen) closeSearch()
        if (viewModel.state.value.isOptionsPanelOpen) viewModel.onOptionsPanelToggle(false)
        systemMenuOpen = false
        appsOpen = false
        if (libraryOpen) closeLibrary()
        openedAppId = null
        refocusCover()
    }
    DisposableEffect(Unit) {
        val onHome: (AndroidEvent.HomePressed) -> Unit = { latestHomePressed.value() }
        PluviaApp.events.on<AndroidEvent.HomePressed, Unit>(onHome)
        onDispose { PluviaApp.events.off<AndroidEvent.HomePressed, Unit>(onHome) }
    }
    // What the footer blurs: the screen behind it, recorded once per frame and drawn again under the footer.
    val backdrop = rememberGraphicsLayer()
    // Just the backdrop art, for the glass behind the selected tab (the top bar cannot blur itself).
    val heroLayer = rememberGraphicsLayer()
    // The panel's layout choice belongs to GameNative's library screen; Rift only keeps it in sync.
    var libraryLayout by remember { mutableStateOf(PrefManager.libraryLayout) }
    // A new sort or filter reorders the covers, so the one that had focus may be gone from the screen (and
    // with it the focus, leaving the gamepad dead). Every tab starts again from its first cover.
    // One rule for every tab and the library: after any sort or filter change the rows start over and the first
    // cover has focus. The panel is still open then, and the reorder can drop focus on some other cover that would
    // become "the last one", so the start is enforced again when the panel closes.
    var lastViewKey by remember { mutableStateOf(filterKey) }
    var viewChanged by remember { mutableStateOf(false) }
    suspend fun startOver() {
        focusedAppId = null
        focusedStreamId = null
        focusedPackage = null
        focusedCloudId = null
        libraryCardFocused = false
        cloudCardFocused = false
        libraryFocusedId = null
        pcRowState.scrollToItem(0)
        streamRowState.scrollToItem(0)
        androidRowState.scrollToItem(0)
        cloudRowState.scrollToItem(0)
    }
    LaunchedEffect(filterKey) {
        if (filterKey == lastViewKey) return@LaunchedEffect
        lastViewKey = filterKey
        viewChanged = true
        startOver()
        if (!overlayOpen) {
            focusSection(section)
            viewChanged = false
        }
    }
    LaunchedEffect(overlayOpen) {
        if (!overlayOpen) {
            if (viewChanged) startOver()
            focusSection(section)
            // The panel slides out while the row may still be re-sorting; ask once more so focus never stays nowhere.
            delay(PANEL_EXIT_REFOCUS_MS)
            if (!overlayOpen) {
                if (viewChanged) startOver()
                focusSection(section)
                viewChanged = false
            }
        }
    }
    val blur by animateDpAsState(if (overlayOpen) 28.dp else 0.dp, tween(300), label = "backdropBlur")
    // A menu (or the art picker) over a library blurs the library itself, like the home under its menus.
    val libraryBlur by animateDpAsState(if (libraryOpen && (libraryMenuOpen || gameOptionsOpen || artItem != null || metaItem != null)) 28.dp else 0.dp, tween(300), label = "libraryBlur")

    val rightStick = rememberRightStick()
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    RiftTheme {
      CompositionLocalProvider(LocalRightStick provides rightStick) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (homeCovered) 0f else 1f }
                .background(RiftColors.Background)
                .riftKeySounds(panelKeys = false)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (optionsItem != null && optionsOnly.panelOpen) return@onPreviewKeyEvent false
                    // The filters panel is open but holds no focus (after a touch, or while it slides in): the press
                    // must not move the covers under the blur; take it and ask the panel to grab focus.
                    if (panelOpen && !panelHasFocus && event.type == KeyEventType.KeyDown &&
                        native.keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER
                    ) {
                        panelFocusAsk++
                        return@onPreviewKeyEvent true
                    }
                    // The Start menu takes its own keys (B closes only it).
                    if (startMenuItems != null) return@onPreviewKeyEvent false
                    if (optionsItem != null && event.type == KeyEventType.KeyDown) optionsItem = null
                    if (appsOpen || libraryOpen || (artItem != null || metaItem != null)) return@onPreviewKeyEvent false
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
                                if (!selectLongPressed && !systemMenuOpen && section != null) {
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
                    // Up from the row goes to the search or the avatar (the tabs are LB/RB's); Down comes back.
                    if (native.keyCode == KeyEvent.KEYCODE_DPAD_UP && section != null && !searchTyping) {
                        if (!topFocused) {
                            val target = if (section == RiftSection.PcGames || RiftSection.PcGames in sections) searchFocus else avatarFocus
                            runCatching { target.requestFocus() }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (native.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && topFocused && !searchTyping) {
                        refocusCover()
                        return@onPreviewKeyEvent true
                    }
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            switchSection(-1)
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            switchSection(1)
                            true
                        }
                        // Start: a small menu in the middle, its own lines on every tab. On PC games it is
                        // GameNative's options logic behind the menu; Y does the same there, and elsewhere
                        // goes straight to the art.
                        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                            val start = event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BUTTON_Y
                            if (onServiceCard) {
                                // "Open library" is not a game: no menu.
                            } else if (section == RiftSection.PcGames) {
                                focusedItem?.let {
                                    optionsOnly.panelOpen = true
                                    optionsToken++
                                    optionsItem = it
                                }
                            } else if (!start && section == RiftSection.PcStreaming) {
                                focusedStream?.let(::openArt)
                            } else if (!start && section == RiftSection.AndroidGames) {
                                // Android games have no options panel either: Y picks their art.
                                focusedAndroid?.let { openArt(it.toArtItem()) }
                            } else if (!start && section == RiftSection.Emulators) {
                                // Emulator games the same: Y picks their art.
                                focusedEmulator?.let(::openArt)
                            } else if (start) {
                                startMenuItems = otherTabMenu()
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
                        .drawWithContent {
                            heroLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(heroLayer)
                        }
                        .graphicsLayer {
                            val p = intro.value
                            alpha = p
                            scaleX = 1.12f - 0.12f * p
                            scaleY = 1.12f - 0.12f * p
                        },
                ) {
                    if (section == RiftSection.AndroidGames && focusedAndroidItem?.heroImageUrl.isNullOrEmpty()) AndroidBackdrop(focusedAndroid) else HeroBackdrop(
                        item = when (section) {
                            RiftSection.PcStreaming -> focusedStream
                            RiftSection.CloudStreaming -> focusedCloud
                            RiftSection.Emulators -> focusedEmulator
                            RiftSection.AndroidGames -> focusedAndroidItem
                            else -> focusedItem.takeIf { section == RiftSection.PcGames }
                        },
                    )
                }

                // Black glass on the home blurs the backdrop art (not the row itself, which is drawn here).
                CompositionLocalProvider(LocalGlassBackdrop provides heroLayer) {
                // Without the footer the whole content sits one footer's height lower: the column is made taller
                // than the screen by that much (its lowest part is off screen) instead of ending above the glass.
                val hintBarShift = with(LocalDensity.current) { if (showHintBar) 0 else FooterHeight.roundToPx() }
                Column(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val taller = constraints.copy(minHeight = constraints.maxHeight + hintBarShift, maxHeight = constraints.maxHeight + hintBarShift)
                            val placeable = measurable.measure(taller)
                            layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
                        }
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (ready) 1f else 0f },
                ) {
                    Box(modifier = Modifier.graphicsLayer { alpha = intro.value }) {
                    TopBar(
                        glass = heroLayer,
                        sections = sections,
                        selected = section,
                        onSelect = ::selectSection,
                        onStep = ::switchSection,
                        searchFocus = searchFocus,
                        avatarFocus = avatarFocus,
                        modifier = Modifier.onFocusChanged { topFocused = it.hasFocus },
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
                        } else if (RiftSection.PcGames in sections) {
                            // On another tab the search belongs to the PC games: it takes you there and opens.
                            SearchState(
                                open = false,
                                typing = false,
                                query = "",
                                onOpen = {
                                    selectSection(RiftSection.PcGames)
                                    viewModel.onIsSearching(true)
                                    searchOpen = true
                                    searchTyping = true
                                },
                                onQuery = {},
                                onDone = {},
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
                            AndroidHero(item = focusedAndroidItem, modifier = Modifier.weight(1f))
                            AndroidShelf(
                                games = androidShown,
                                listState = androidRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = { if (androidSaved) focusedPackage = it.packageName },
                                onOpen = { openGame(it.toPageItem()) },
                                entry = switchEntry,
                            )
                        }
                        // The PC games tab's screen, with the Steam games of the paired PCs; A streams the game.
                        section == RiftSection.PcStreaming && streamTab != null -> {
                            HeroLogo(item = focusedStream, modifier = Modifier.weight(1f).graphicsLayer { alpha = intro.value })
                            PcGamesShelf(
                                shown = streamHomeRow,
                                entry = switchEntry,
                                onOpenLibrary = ::openStreamLibrary,
                                onLibraryFocused = { streamCardFocused = true },
                                libraryState = libraryState,
                                listState = streamRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = {
                                    if (streamSaved) focusedStreamId = it.appId
                                    streamCardFocused = false
                                },
                                onOpen = { openGame(it, forStream = true) },
                                onNearEnd = {},
                                badges = false,
                                dimUninstalled = streamUninstalledMode == UninstalledMode.DIM,
                            )
                        }
                        // The Emulators tab: the games of the systems set up in settings, the ones played last first;
                        // A starts the game in the emulator picked for its system.
                        section == RiftSection.Emulators -> {
                            HeroLogo(item = focusedEmulator, modifier = Modifier.weight(1f).graphicsLayer { alpha = intro.value })
                            PcGamesShelf(
                                shown = emulatorHomeRow,
                                entry = if (!introPlayed) ShelfEntry.Intro else switchEntry,
                                entryStarted = introPlayed || introRunning,
                                onOpenLibrary = ::openEmulatorLibrary,
                                onLibraryFocused = { emulatorCardFocused = true },
                                libraryState = libraryState,
                                listState = emulatorRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = {
                                    if (emulatorSaved) focusedEmulatorId = it.appId
                                    emulatorCardFocused = false
                                },
                                onOpen = { item -> openGame(item) },
                                onNearEnd = {},
                                badges = false,
                            )
                        }
                        // The cloud tab: Game Pass cloud games, the ones played from Rift first; A opens the game page.
                        section == RiftSection.CloudStreaming -> {
                            // The row's logos are looked up while it is on screen (the store has none).
                            LaunchedEffect(cloudHomeRow.map { it.appId }) {
                                CloudLogos.prefetch(context, cloudHomeRow.mapNotNull { CloudLibrary.game(it.appId) })
                            }
                            HeroLogo(item = focusedCloud, modifier = Modifier.weight(1f).graphicsLayer { alpha = intro.value })
                            PcGamesShelf(
                                shown = cloudHomeRow,
                                entry = if (!introPlayed) ShelfEntry.Intro else switchEntry,
                                entryStarted = introPlayed || introRunning,
                                onOpenLibrary = ::openCloudLibrary,
                                onLibraryFocused = { cloudCardFocused = true },
                                libraryState = libraryState,
                                listState = cloudRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = {
                                    if (cloudSaved) focusedCloudId = it.appId
                                    cloudCardFocused = false
                                },
                                onOpen = { openGame(it) },
                                onNearEnd = {},
                                badges = false,
                            )
                        }
                        // Still loading with nothing cached: stay black rather than show a spinner.
                        library.isEmpty() && libraryState.isLoading -> Spacer(modifier = Modifier.weight(1f))
                        else -> {
                            // Big Picture style: only the big logo of the focused game over its art.
                            HeroLogo(item = focusedItem, modifier = Modifier.weight(1f).graphicsLayer { alpha = intro.value })
                            PcGamesShelf(
                                shown = homeRow,
                                entry = if (!introPlayed) ShelfEntry.Intro else switchEntry,
                                entryStarted = introPlayed || introRunning,
                                onOpenLibrary = ::openLibrary,
                                onLibraryFocused = { libraryCardFocused = true },
                                libraryState = libraryState,
                                dimUninstalled = uninstalledMode == UninstalledMode.DIM,
                                listState = pcRowState,
                                cardHeight = cardHeight,
                                pendingFocusKey = pendingFocusKey,
                                onFocusConsumed = { pendingFocusKey = null },
                                onFocused = {
                                    if (pcSaved) focusedAppId = it.appId
                                    libraryCardFocused = false
                                },
                                // A opens the game page; games never start straight from the home (Denis).
                                onOpen = { openGame(it) },
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
              }
                if (showHintBar) Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(FooterHeight).graphicsLayer { alpha = intro.value }) {
                    FrostedGlass(backdrop = backdrop, modifier = Modifier.matchParentSize())
                    HintsBar(showFilters = section != null, showOptions = !onServiceCard)
                }
            }

            val tabGroups = when (section) {
                RiftSection.PcStreaming -> tabFilterGroups(streamView) {
                    streamView = it
                    RiftTabFilters.set(context, RiftSection.PcStreaming, it)
                } + uninstalledGroup(streamUninstalledMode) {
                    RiftUninstalledMode.set(context, RiftSection.PcStreaming, it)
                }
                RiftSection.AndroidGames -> tabFilterGroups(androidView) {
                    androidView = it
                    RiftTabFilters.set(context, RiftSection.AndroidGames, it)
                }
                RiftSection.Emulators -> tabFilterGroups(emulatorView) {
                    emulatorView = it
                    RiftTabFilters.set(context, RiftSection.Emulators, it)
                }
                RiftSection.CloudStreaming -> tabFilterGroups(cloudView) {
                    cloudView = it
                    RiftTabFilters.set(context, RiftSection.CloudStreaming, it)
                }
                else -> null
            }
            // Select in a library: a small menu with the filters and sort of that tab (the Select panel's groups).
            val libraryFilterGroups: List<TabFilterGroup> = if (section == RiftSection.PcGames) {
                listOf(
                    TabFilterGroup(
                        label = stringResource(R.string.options_sort_by),
                        value = stringResource(libraryState.currentSortOption.displayTextRes),
                        icon = Icons.Outlined.SwapVert,
                        options = SortOption.entries.map { option ->
                            TabFilterOption(stringResource(option.displayTextRes), libraryState.currentSortOption == option) {
                                viewModel.onSortOptionChanged(option)
                            }
                        },
                    ),
                    uninstalledGroup(uninstalledMode) { RiftUninstalledMode.set(context, RiftSection.PcGames, it) },
                )
            } else {
                tabGroups.orEmpty()
            }
            // In every library: Select opens the filters, Start the game's menu, Y the art of the game under the cursor.
            fun libraryKey(code: Int, items: List<LibraryItem>): Boolean {
                val item = items.firstOrNull { it.appId == libraryFocusedId }
                return when (code) {
                    KeyEvent.KEYCODE_BUTTON_SELECT -> {
                        if (libraryFilterGroups.isNotEmpty()) {
                            filterGroupIndex = null
                            libraryFiltersOpen = true
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> {
                        if (item != null) {
                            if (section == RiftSection.PcGames) {
                                optionsOnly.panelOpen = true
                                optionsToken++
                                optionsItem = item
                            } else {
                                startMenuItems = otherTabMenu(item)
                            }
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        item?.let(::openArt)
                        true
                    }
                    else -> false
                }
            }
            Box(modifier = Modifier.fillMaxSize().blur(libraryBlur)) {
            // Big Picture's library: all the PC games in a grid, one tab per store with games.
            val libraryTabs = pcLibraryTabs(libraryState)
            RiftLibraryScreen(
                isOpen = libraryOpen && section != RiftSection.CloudStreaming && section != RiftSection.PcStreaming && section != RiftSection.Emulators,
                glass = heroLayer,
                tabs = libraryTabs,
                selectedKey = libraryState.currentTab.name,
                onSelectTab = { key ->
                    LibraryTab.entries.firstOrNull { it.name == key }?.let {
                        if (it != viewModel.state.value.currentTab) {
                            viewModel.onTabChanged(it)
                            libraryTabSwitch++
                        }
                    }
                },
                onDismiss = ::closeLibrary,
                keepFocusInside = { !libraryFiltersOpen && startMenuItems == null && openedAppId == null && optionsItem == null && (artItem == null && metaItem == null) },
                onKeyDown = { code -> libraryKey(code, library) },
            ) {
                PcLibraryGrid(
                    items = library,
                    dimUninstalled = uninstalledMode == UninstalledMode.DIM,
                    loading = libraryState.isLoading,
                    tabSwitch = libraryTabSwitch,
                    libraryState = libraryState,
                    pendingFocusKey = libraryPending,
                    onFocusConsumed = { libraryPending = null },
                    onWantFocus = { libraryPending = it },
                    onFocused = { libraryFocusedId = it.appId },
                    onOpen = { openGame(it) },
                    onNearEnd = {
                        if (libraryState.currentPaginationPage < libraryState.lastPaginationPage) {
                            viewModel.onPageChange(1)
                        }
                    },
                )
            }
            // The home streaming library: the PC's Steam games, all of them or by whether the PC has them
            // installed (Xbox and PlayStation join later as their own tabs).
            val streamLibraryTabs = buildList {
                add(LibraryTabSpec(STREAM_TAB_ALL, stringResource(R.string.rift_library_all)))
                if (streamItems.any { it.isInstalled }) add(LibraryTabSpec(STREAM_TAB_INSTALLED, stringResource(R.string.rift_stream_library_installed)))
                if (streamItems.any { !it.isInstalled }) add(LibraryTabSpec(STREAM_TAB_NOT_INSTALLED, stringResource(R.string.rift_stream_library_not_installed)))
            }
            RiftLibraryScreen(
                isOpen = libraryOpen && section == RiftSection.PcStreaming,
                glass = heroLayer,
                tabs = streamLibraryTabs,
                selectedKey = streamLibraryTab,
                onSelectTab = { key ->
                    if (key != streamLibraryTab) {
                        streamLibraryTab = key
                        libraryTabSwitch++
                    }
                },
                onDismiss = ::closeLibrary,
                keepFocusInside = { !libraryFiltersOpen && startMenuItems == null && openedAppId == null && (artItem == null && metaItem == null) },
                onKeyDown = { code -> libraryKey(code, streamLibraryItems) },
            ) {
                PcLibraryGrid(
                    items = streamLibraryItems,
                    dimUninstalled = streamUninstalledMode == UninstalledMode.DIM,
                    loading = false,
                    tabSwitch = libraryTabSwitch,
                    libraryState = libraryState,
                    pendingFocusKey = libraryPending,
                    onFocusConsumed = { libraryPending = null },
                    onWantFocus = { libraryPending = it },
                    onFocused = { libraryFocusedId = it.appId },
                    onOpen = { openGame(it, forStream = true) },
                    onNearEnd = {},
                    badges = false,
                    waitForReload = false,
                )
            }
            // The cloud tab's library: one tab per cloud service (GeForce NOW joins Xbox Cloud Gaming later).
            val cloudLibraryTabs = listOf(LibraryTabSpec(CLOUD_XCLOUD_TAB, stringResource(R.string.rift_cloud_service_game_pass)))
            RiftLibraryScreen(
                isOpen = libraryOpen && section == RiftSection.CloudStreaming,
                glass = heroLayer,
                tabs = cloudLibraryTabs,
                selectedKey = CLOUD_XCLOUD_TAB,
                onSelectTab = {},
                onDismiss = ::closeLibrary,
                keepFocusInside = { !libraryFiltersOpen && startMenuItems == null && openedAppId == null },
                onKeyDown = { code -> libraryKey(code, cloudItems) },
            ) {
                PcLibraryGrid(
                    items = cloudItems,
                    loading = false,
                    tabSwitch = libraryTabSwitch,
                    libraryState = libraryState,
                    pendingFocusKey = libraryPending,
                    onFocusConsumed = { libraryPending = null },
                    onWantFocus = { libraryPending = it },
                    onFocused = { libraryFocusedId = it.appId },
                    onOpen = { openGame(it) },
                    onNearEnd = {},
                    badges = false,
                    waitForReload = false,
                )
            }
            // The Emulators tab's library: one tab per system with games, after "All games" (LB/RB switch them).
            val emulatorLibraryTabs = emulatorLibraryTabs(emulatorGames)
            RiftLibraryScreen(
                isOpen = libraryOpen && section == RiftSection.Emulators,
                glass = heroLayer,
                tabs = emulatorLibraryTabs,
                selectedKey = emulatorLibraryTab ?: EMULATOR_ALL_TAB,
                onSelectTab = { key ->
                    emulatorLibraryTab = key.takeIf { it != EMULATOR_ALL_TAB }
                    libraryTabSwitch++
                },
                onDismiss = ::closeLibrary,
                keepFocusInside = { !libraryFiltersOpen && startMenuItems == null && openedAppId == null },
                onKeyDown = { code -> libraryKey(code, emulatorItems) },
            ) {
                PcLibraryGrid(
                    items = emulatorLibraryItems,
                    loading = false,
                    tabSwitch = libraryTabSwitch,
                    libraryState = libraryState,
                    pendingFocusKey = libraryPending,
                    onFocusConsumed = { libraryPending = null },
                    onWantFocus = { libraryPending = it },
                    onFocused = { libraryFocusedId = it.appId },
                    onOpen = { item -> openGame(item) },
                    onNearEnd = {},
                    badges = false,
                    waitForReload = false,
                )
            }
            }
            // The filters menu of a library: the tab's groups, then the lines of the group picked. A pick applies
            // at once, closes the menu and sends the cursor back to the first game.
            val openGroup = filterGroupIndex?.let { libraryFilterGroups.getOrNull(it) }
            val filterItems = if (openGroup != null) {
                openGroup.options.map { option ->
                    RiftStartMenuItem((if (option.selected) "\u2713  " else "") + option.text) {
                        option.onPick()
                        libraryFiltersOpen = false
                        filterGroupIndex = null
                        libraryTabSwitch++
                    }
                }
            } else {
                libraryFilterGroups.mapIndexed { index, group ->
                    RiftStartMenuItem(group.label + ": " + group.value, chevron = true) { filterGroupIndex = index }
                }
            }
            if (libraryFiltersOpen) lastFilterItems = filterItems
            RiftStartMenu(
                isOpen = libraryFiltersOpen,
                items = lastFilterItems,
                onDismiss = { if (filterGroupIndex != null) filterGroupIndex = null else libraryFiltersOpen = false },
                listKey = filterGroupIndex,
            )
            RiftGlassTheme {
                LibraryOptionsPanel(
                    isOpen = panelOpen,
                    onDismiss = { viewModel.onOptionsPanelToggle(false) },
                    selectedFilters = libraryState.appInfoSortType,
                    onFilterChanged = viewModel::onFilterChanged,
                    currentSortOption = libraryState.currentSortOption,
                    onSortOptionChanged = viewModel::onSortOptionChanged,
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
                    tabGroups = tabGroups,
                    focusAsk = panelFocusAsk,
                    onFocusChanged = { panelHasFocus = it },
                    extraGroup = when (section) {
                        RiftSection.PcGames -> uninstalledGroup(uninstalledMode) {
                            RiftUninstalledMode.set(context, RiftSection.PcGames, it)
                        }
                        else -> null
                    },
                    panelShape = RectangleShape,
                    panelShadowElevation = 0.dp,
                    showCloseButton = false,
                )
                optionsItem?.let { item ->
                    key(optionsToken) {
                        CompositionLocalProvider(LocalRiftOptionsOnly provides optionsOnly, LocalRiftArtPicker provides artOpener, dev.rift.launcher.meta.LocalRiftInfoActions provides infoActions) {
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
                RiftAppsGrid(
                    isOpen = appsOpen,
                    onDismiss = { appsOpen = false },
                    focusRequester = appsFocus,
                    onFocusChanged = { appsFocused = it },
                )
                dev.rift.launcher.assistant.optimize.RiftOptimizeHost()
                RiftStartMenu(
                    isOpen = startMenuItems != null,
                    items = lastStartMenuItems,
                    onDismiss = { startMenuItems = null },
                )
                renameTarget?.let { (appId, current, original) ->
                    RenameGameDialog(
                        initial = current,
                        onSave = { name ->
                            RiftGameNames.rename(context, appId, name, original)
                            renameTarget = null
                        },
                        onDismiss = { renameTarget = null },
                    )
                }
                RiftSystemMenu(
                    isOpen = systemMenuOpen,
                    onDismiss = { systemMenuOpen = false },
                    onNavigateRoute = { route ->
                        systemMenuOpen = false
                        if (route == PluviaScreen.Support.route) {
                            // "AI Help" opens Rift's assistant (the user's own provider) instead of GameNative's support,
                            // about the game under the cursor when it is installed.
                            val games = library.filter { it.isInstalled }.map { ChatGame(it.appId, it.name) }
                            AssistantChat.open(games, focusedItem?.let { ChatGame(it.appId, it.name) })
                            onNavigateRoute(RIFT_ASSISTANT_ROUTE)
                        } else {
                            onNavigateRoute(route)
                        }
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
    pageItem?.let { item ->
        AnimatedVisibility(
            visibleState = pageState,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = PAGE_START_SCALE),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = PAGE_START_SCALE),
            modifier = Modifier
                .fillMaxSize()
                // Focus stays on the page while it is open; on the way out the home takes it back.
                .focusRequester(pageFocus)
                .onFocusChanged { pageFocused = it.hasFocus }
                .focusProperties { this.onExit = { if (openedAppId != null) cancelFocusChange() } }
                .focusGroup()
                // Last resort: a B the page itself did not take still goes back to the home.
                .onKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    val back = code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_ESCAPE
                    if (back && event.type == KeyEventType.KeyDown && openedAppId != null) {
                        closePage()
                        true
                    } else {
                        false
                    }
                },
        ) {
            BackHandler(enabled = openedAppId != null) { closePage() }
            // From the PC streaming tab the page streams the game; the page stays open, so the stream
            // ends back on the game's page, and B from there returns to the same cover.
            val streamAction = remember(item.appId, openedForStream, streamTab) {
                if (openedForStream && streamTab != null) {
                    RiftStreamAction {
                        sounds.play(RiftSound.Launch)
                        streamTab.launch(context, item)
                    }
                } else {
                    null
                }
            }
            if (EmulatorLibrary.isEmulatorGame(item.appId)) {
                // An emulator game's page; the page stays open while the emulator runs, like a PC game's.
                RiftGamePage(
                    item = item,
                    originalName = EmulatorLibrary.game(item.appId)?.name ?: item.name,
                    onPlay = {
                        sounds.play(RiftSound.Launch)
                        EmulatorLibrary.game(item.appId)?.let { EmulatorLibrary.launch(context, it) }
                    },
                    onBack = { closePage() },
                    onChangeArt = { openArt(item) },
                    onRefreshMeta = { refreshMeta(item, EmulatorLibrary.game(item.appId)?.platformId) },
                    onPickMeta = { openMetaPicker(item, EmulatorLibrary.game(item.appId)?.platformId) },
                    platformId = EmulatorLibrary.game(item.appId)?.platformId,
                )
            } else if (PcStreamingTab.isHostGame(item.appId) && streamTab != null) {
                // A streamed game the Steam account does not own: Rift's own page, Play streams it.
                RiftGamePage(
                    item = item,
                    originalName = streamTab.hostName(item) ?: item.name,
                    onPlay = {
                        sounds.play(RiftSound.Launch)
                        streamTab.launch(context, item)
                    },
                    onBack = { closePage() },
                    onChangeArt = { openArt(item) },
                    onRefreshMeta = { refreshMeta(item, null) },
                    onPickMeta = { openMetaPicker(item, null) },
                )
            } else if (item.appId.startsWith("ANDROID_")) {
                RiftGamePage(
                    item = item,
                    originalName = androidGames.firstOrNull { "ANDROID_" + it.packageName == item.appId }?.label ?: item.name,
                    onPlay = {
                        sounds.play(RiftSound.Launch)
                        AndroidGames.launch(context, AndroidGame(item.appId.removePrefix("ANDROID_"), item.name))
                    },
                    onBack = { closePage() },
                    onChangeArt = { openArt(item) },
                    onRefreshMeta = { refreshMeta(item, null) },
                    onPickMeta = { openMetaPicker(item, null) },
                )
            } else if (openedForCloud && cloudTab != null) {
                // A cloud game's page; like a stream, the page closes first so the game ends back on its cover.
                CloudGamePage(
                    item = item,
                    onPlay = {
                        sounds.play(RiftSound.Launch)
                        closePage()
                        cloudTab.launch(context, item)
                    },
                    onBack = { closePage() },
                    onChangeArt = { openArt(item) },
                    onRename = { renameTarget = Triple(item.appId, item.name, RiftGameNames.original(context, item.appId, item.name)) },
                    onRefreshMeta = { refreshMeta(item, null) },
                    onPickMeta = { openMetaPicker(item, null) },
                )
            } else {
            CompositionLocalProvider(LocalRiftStreamAction provides streamAction) {
            CompositionLocalProvider(LocalRiftArtPicker provides artOpener, dev.rift.launcher.meta.LocalRiftInfoActions provides infoActions) {
            LibraryDetailPane(
                libraryItem = item,
                onClickPlay = { asContainer ->
                    sounds.play(RiftSound.Launch)
                    onClickPlay(item.appId, asContainer)
                },
                onTestGraphics = { onTestGraphics(item.appId) },
                onPlayWithDiagnostics = { onPlayWithDiagnostics(item.appId) },
                onAiDebugRun = { onAiDebugRun(item.appId) },
                onBack = { closePage() },
            )
            }
            }
            }
        }
    }
        RiftTheme {
            dev.rift.launcher.meta.RiftMetaPicker(
                item = metaItem,
                platformId = metaPlatform,
                openToken = metaToken,
                onDismiss = {
                    metaItem = null
                    if (openedAppId != null) runCatching { pageFocus.requestFocus() }
                },
                onChosen = {
                    metaItem = null
                    if (openedAppId != null) runCatching { pageFocus.requestFocus() }
                    SnackbarManager.show(context.getString(R.string.rift_meta_chosen))
                },
            )
        }
        // The art picker lives above everything, the game page included.
        RiftTheme {
            RiftArtPicker(
                item = artItem,
                openToken = artToken,
                glass = heroLayer,
                onDismiss = {
                    artItem = null
                    // Over the game page, focus goes back into the page; over the home the covers take it.
                    if (openedAppId != null) runCatching { pageFocus.requestFocus() }
                },
                onOpenSettings = {
                    artItem = null
                    RiftSettingsSection.request(RiftSettingsSection.Artwork)
                    onNavigateRoute(PluviaScreen.Settings.route)
                },
            )
        }
    }

    // Focus a cover once the section has something to show, so a gamepad works without touching the screen.
    LaunchedEffect(section, library.isNotEmpty(), androidGames.isNotEmpty(), streamItems.isNotEmpty(), cloudItems.isNotEmpty(), emulatorItems.isNotEmpty(), pcSaved, androidSaved, streamSaved, cloudSaved, emulatorSaved) {
        if (pendingFocusKey == null) focusSection(section)
    }
    // The remembered cover never came back (the game is gone): after a grace the first cover takes the focus.
    val currentSaved = when (section) {
        RiftSection.PcGames -> pcSaved
        RiftSection.AndroidGames -> androidSaved
        RiftSection.PcStreaming -> streamSaved
        RiftSection.CloudStreaming -> cloudSaved
        RiftSection.Emulators -> emulatorSaved
        null -> true
    }
    LaunchedEffect(section, currentSaved) {
        if (currentSaved) return@LaunchedEffect
        delay(SOURCES_GRACE_MS)
        when (section) {
            RiftSection.PcGames -> focusedAppId = null
            RiftSection.AndroidGames -> focusedPackage = null
            RiftSection.PcStreaming -> focusedStreamId = null
            RiftSection.CloudStreaming -> focusedCloudId = null
            RiftSection.Emulators -> focusedEmulatorId = null
            null -> Unit
        }
    }
}

private fun focusKeyFor(item: LibraryItem): String = item.appId

/** The game page grows from slightly smaller than the screen and shrinks back to it. */
private const val PAGE_START_SCALE = 0.94f
private const val PAGE_FOCUS_CHECK_MS = 250L
private const val PRELOAD_COVER_PX = 1000
private const val PRELOAD_BACKDROP_PX = 2560

/** Games in the home row before the "Open library" card, like Big Picture. */
private const val HOME_ROW_COUNT = 20
private const val LIBRARY_CARD_KEY = "rift:library"
private const val CLOUD_XCLOUD_TAB = "xcloud"
private const val STREAM_TAB_ALL = "all"
private const val STREAM_TAB_INSTALLED = "installed"
private const val STREAM_TAB_NOT_INSTALLED = "not_installed"
private const val EMULATOR_ALL_TAB = "all"

/** The Emulators library's tabs: all games first, then each system that has games. */
@Composable
private fun emulatorLibraryTabs(games: List<dev.rift.launcher.emulators.EmulatorGame>): List<LibraryTabSpec> {
    val all = LibraryTabSpec(EMULATOR_ALL_TAB, stringResource(R.string.rift_library_all))
    val systems = games.map { it.platformId }.distinct().mapNotNull { id ->
        dev.rift.launcher.settings.EmulatorCatalog.platform(id)?.let { LibraryTabSpec(id, stringResource(it.nameRes)) }
    }
    return listOf(all) + systems
}
private const val RECENT_SORT_DEFAULT_KEY = "home_recent_sort_default_v1"

/** The library's tabs for PC games: all games first, then each store that has games. */
@Composable
private fun pcLibraryTabs(state: LibraryState): List<LibraryTabSpec> {
    val counts = mapOf(
        LibraryTab.STEAM to state.steamCount,
        LibraryTab.GOG to state.gogCount,
        LibraryTab.EPIC to state.epicCount,
        LibraryTab.AMAZON to state.amazonCount,
        LibraryTab.LOCAL to state.localCount,
    )
    val all = LibraryTabSpec(LibraryTab.ALL.name, stringResource(R.string.rift_library_all))
    val stores = counts.filter { (tab, count) -> count > 0 && tab in state.visibleLibraryTabs }
        .map { (tab, _) -> LibraryTabSpec(tab.name, stringResource(tab.labelResId)) }
    return listOf(all) + stores
}

/** The library grid of PC games: the home's covers without reflections, focus kept across tab switches. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PcLibraryGrid(
    items: List<LibraryItem>,
    loading: Boolean,
    tabSwitch: Int,
    libraryState: LibraryState,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onWantFocus: (String) -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
    badges: Boolean = true,
    dimUninstalled: Boolean = false,
    // The PC library reloads its list on a tab switch; a list already in memory has nothing to wait for.
    waitForReload: Boolean = true,
) {
    val gridState = rememberLazyGridState()
    val currentItems by rememberUpdatedState(items)
    val currentLoading by rememberUpdatedState(loading)
    // The entrance starts once per opening or tab switch, when the new list is in; later page loads
    // (more games as the grid scrolls) do not start it again.
    var entryReady by remember(tabSwitch) { mutableStateOf(false) }
    // On opening and after a tab switch: wait for the new list, then focus its first game.
    LaunchedEffect(tabSwitch) {
        if (waitForReload) withTimeoutOrNull(TAB_RELOAD_WAIT_MS) { snapshotFlow { currentLoading }.first { it } }
        snapshotFlow { currentLoading }.first { !it }
        entryReady = true
        val first = currentItems.firstOrNull() ?: return@LaunchedEffect
        gridState.scrollToItem(0)
        onWantFocus(first.appId)
    }
    if (items.isEmpty()) {
        if (!loading) {
            Text(
                text = stringResource(R.string.rift_filter_empty),
                color = RiftColors.OnSurfaceVariant,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 24.dp),
            )
        }
        return
    }
    val context = LocalContext.current
    // Opening and every tab switch: the covers rise in one after another once the list is there,
    // on one clock for the grid (a tile composed late or reused can never stay unseen).
    val clock = key(tabSwitch) { rememberShelfClock(ShelfEntry.Library, started = entryReady) }
    // The focused row stays whole above the footer and below the tabs, not flush with the screen's edge.
    val edgeMargin = with(LocalDensity.current) { 48.dp.toPx() }
    val hintBar = HintBarState.shown
    val bottomMargin = with(LocalDensity.current) { ((if (hintBar) FooterHeight else 0.dp) + 48.dp).toPx() }
    val spec = remember(edgeMargin, bottomMargin) { KeepInView(edgeMargin, bottomMargin) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(LibraryCoverWidth),
        state = gridState,
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = if (hintBar) 120.dp else 120.dp - FooterHeight),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.appId }) { index, item ->
            val urls = portraitCoverUrls(item)
            val url = urls.firstOrNull { it.isNotEmpty() }.orEmpty()
            BoxWithConstraints(modifier = Modifier.shelfEntry(ShelfEntry.Library, index, clock)) {
                CoverCard(
                    height = maxWidth * 1.5f,
                    paletteKey = url,
                    loadPalette = { CoverColors.palette(context, url) },
                    requestFocus = pendingFocusKey == item.appId,
                    onFocusRequested = onFocusConsumed,
                    onFocused = {
                        onFocused(item)
                        if (index >= items.size - LIBRARY_LOAD_MORE_THRESHOLD) onNearEnd()
                    },
                    onClick = { onOpen(item) },
                    cover = {
                        GameCoverArt(item = item, urls = urls, dimmed = dimUninstalled && !item.isInstalled)
                    },
                    badge = { if (badges) CoverBadges(item, libraryState) },
                    reflection = false,
                )
            }
        }
    }
    }
}

private val LibraryCoverWidth = 150.dp
private val StoreBadgeSize = 26.dp

/**
 * A cover's picture. Never a title on the cover (Denis: the art already has it); a game with none shows
 * the card's plain dark surface until its art is found. A game not installed is dimmed by black over the art, so nothing
 * under it shows through and the card's rounded corners stay as they are while it grows.
 */
@Composable
private fun BoxScope.GameCoverArt(item: LibraryItem, urls: List<String>, dimmed: Boolean, wide: Boolean = false) {
    CoverImage(urls = urls, contentDescription = item.name, modifier = Modifier.fillMaxSize(), lateUrls = remember(item.appId, wide) { storeCoverLookup(item, portrait = !wide) })
    if (dimmed) Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = UNINSTALLED_DIM)))
}

/** How much black covers a game that is not installed (the art keeps about 40 %). */
private const val UNINSTALLED_DIM = 0.6f

/** How many games at the top of a tab also get a wide picture (only the first tile is wide). */
private const val WIDE_ART_GAMES = 12
private const val LIBRARY_LOAD_MORE_THRESHOLD = 18
private const val TAB_RELOAD_WAIT_MS = 1_500L

/** How long a tab switch counts as one, for the new shelf's entrance. */
private const val SWITCH_ENTRY_MS = 700L

/** Steam's library header art ratio (460×215), used for the first, wide tile. */
private const val WIDE_TILE_RATIO = 460f / 215f

/**
 * GameNative's own card badges, kept small and along the bottom edge (a cover's title is usually at its
 * top, and the badges covered it): compatibility verdict bottom left, store icon bottom right.
 */
@Composable
internal fun BoxScope.CoverBadges(item: LibraryItem, libraryState: LibraryState) {
    val community = libraryState.communityCompatibilityFor(item)
    val status = libraryState.compatibilityMap[item.name]
    val badgeModifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
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
    // Only the store's icon, on a round dark disc (Denis): whether a game is installed shows
    // through the filters and the not-installed choice, not a dot here.
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .size(StoreBadgeSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        // Store icons take the content colour; white so they read on the dark disc.
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
    entry: ShelfEntry?,
    libraryState: LibraryState,
    listState: LazyListState,
    cardHeight: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onFocused: (LibraryItem) -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onNearEnd: () -> Unit,
    badges: Boolean = true,
    dimUninstalled: Boolean = false,
    emptyMessage: String = stringResource(R.string.rift_filter_empty),
    onOpenLibrary: (() -> Unit)? = null,
    onLibraryFocused: () -> Unit = {},
    entryStarted: Boolean = true,
) {
    // One entrance clock for the shelf (app start, tab switch).
    val clock = rememberShelfClock(entry, entryStarted)
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
                text = emptyMessage,
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
            // Preferred art first, the other kinds as fallbacks when one is missing or fails to load.
            val urls = if (wide) {
                wideCoverUrls(item)
            } else {
                portraitCoverUrls(item)
            }
            val url = urls.firstOrNull { it.isNotEmpty() }.orEmpty()
            val context = LocalContext.current
            // Entrance: the covers on screen roll in one after another (app start, tab switch).
            Box(modifier = Modifier.shelfEntry(entry, index, clock)) {
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
                    GameCoverArt(item = item, urls = urls, dimmed = dimUninstalled && !item.isInstalled, wide = wide)
                },
                badge = { if (badges) CoverBadges(item, libraryState) },
            )
            }
        }
        if (onOpenLibrary != null) {
            item(key = LIBRARY_CARD_KEY) {
                Box(modifier = Modifier.shelfEntry(entry, shown.size, clock)) {
                    LibraryCard(
                        height = cardHeight,
                        requestFocus = pendingFocusKey == LIBRARY_CARD_KEY,
                        onFocusRequested = onFocusConsumed,
                        onFocused = onLibraryFocused,
                        onClick = onOpenLibrary,
                    )
                }
            }
        }
    }
}

/**
 * The last tile of the home row: opens the whole library (Big Picture's "Open library"). Black glass
 * over the backdrop art, no grey and no white outline (Denis); focus as on the covers, with the glass's own rim.
 */
@Composable
private fun LibraryCard(
    height: Dp,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    CoverCard(
        height = height,
        paletteKey = LIBRARY_CARD_KEY,
        loadPalette = { BlackGlass.RimColors },
        surface = Color.Transparent,
        requestFocus = requestFocus,
        onFocusRequested = onFocusRequested,
        onFocused = onFocused,
        onClick = onClick,
        cover = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .blackGlass()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = RiftColors.OnSurface,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.rift_library_open),
                    color = RiftColors.OnSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
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
    entry: ShelfEntry?,
) {
    // One entrance clock for the shelf (app start, tab switch).
    val clock = rememberShelfClock(entry)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    RiftRow(
        state = listState,
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 18.dp, bottom = RowBottomGap),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        itemsIndexed(games, key = { _, game -> game.packageName }) { index, game ->
            val icon = rememberAppIcon(game.packageName)
            // Like every tab: the first tile is wide, the rest are 2:3 covers.
            val wide = index == 0
            val version by GameArtStore.version.collectAsStateWithLifecycle()
            val item = remember(game.packageName, version) { GameArtStore.apply(context, game.toArtItem()) }
            val urls = if (wide) wideCoverUrls(item) else portraitCoverUrls(item)
            val hasArt = urls.any { it.isNotBlank() }
            Box(modifier = Modifier.shelfEntry(entry, index, clock)) {
            CoverCard(
                height = cardHeight,
                aspectRatio = if (wide) WIDE_TILE_RATIO else 2f / 3f,
                paletteKey = urls.firstOrNull { it.isNotEmpty() } ?: "android:${game.packageName}",
                loadPalette = {
                    val url = urls.firstOrNull { it.isNotEmpty() }
                    if (url != null) CoverColors.palette(context, url) else AndroidGames.icon(context, game.packageName)?.let(CoverColors::palette)
                },
                requestFocus = pendingFocusKey == androidFocusKey(game),
                onFocusRequested = onFocusConsumed,
                onFocused = {
                    onFocused(game)
                },
                onClick = { onOpen(game) },
                cover = {
                    if (hasArt) CoverImage(urls = urls, contentDescription = game.label, modifier = Modifier.fillMaxSize())
                    // The app's icon until a cover is found (or when there is none): its own colours behind it, no name.
                    if (!hasArt && icon != null) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.45f },
                            )
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = game.label,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .then(if (wide) Modifier.fillMaxHeight(0.6f) else Modifier.fillMaxWidth(0.6f))
                                    .aspectRatio(1f)
                                    .clip(RiftShapes.card),
                            )
                        }
                    }
                },
            )
            }
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
private fun AndroidHero(item: LibraryItem?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (item == null) return@Column
        // The logo picked or found for the game; its name in the title style when it has none.
        GameLogo(item = item)
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

/** Share of the screen height the banner covers; below it the background and the shelf take over. */
private const val HERO_HEIGHT_FRACTION = 0.66f

/** Fades the element to transparent toward its bottom edge, starting at [from] of its height. */
private fun Modifier.fadeOut(from: Float): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(from to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

/** A soft blur that grows toward the bottom of the banner and fades out at its very edge. */
private fun Modifier.fadeBand(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0.46f to Color.Transparent, 0.76f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
    .blur(18.dp, BlurredEdgeTreatment.Unbounded)

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
        // The banner fills the width and the top part of the screen only, so a wide hero keeps its
        // proportions (no heavy zoom into the middle); its lower edge fades into the shelf area, first
        // into a soft blur, then into the background.
        Box(modifier = Modifier.fillMaxSize()) {
            if (heroUrl.isNotEmpty()) {
                // Without the hint footer the banner, and so its blur band, reaches one footer's height lower,
                // like the rows below it.
                val shift = with(LocalDensity.current) { if (HintBarState.shown) 0 else FooterHeight.roundToPx() }
                val zoomed = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = (constraints.maxHeight * HERO_HEIGHT_FRACTION).toInt() + shift
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                    }
                CoilImage(
                    modifier = zoomed.fadeOut(from = 0.6f),
                    imageModel = { heroUrl },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, contentDescription = null),
                    loading = {},
                    failure = {},
                )
                // The same picture blurred, visible only toward the bottom of the banner.
                CoilImage(
                    modifier = zoomed.fadeBand(),
                    imageModel = { heroUrl },
                    imageOptions = ImageOptions(contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, contentDescription = null),
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
    glass: GraphicsLayer,
    sections: List<RiftSection>,
    selected: RiftSection?,
    onSelect: (RiftSection) -> Unit,
    onStep: (Int) -> Unit,
    search: SearchState?,
    searchFocus: FocusRequester,
    avatarFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Big Picture style: the section tabs between the bumpers, the search in the middle of what is left, the
    // status icons in the right corner. The corner is measured first and keeps its room; the tabs shrink.
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    val minSearch = with(LocalDensity.current) { 240.dp.roundToPx() }
    val rowGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val pad = with(LocalDensity.current) { ScreenPadding.roundToPx() }
    // The bumper's picture sits a little inside its touch box; its edge, not the box, lines up with the covers.
    val bumperInset = with(LocalDensity.current) { 8.dp.roundToPx() }
    Layout(
        modifier = modifier.fillMaxWidth().padding(top = ScreenPadding, bottom = 18.dp),
        content = {
            BumperButton(GamepadButton.LB.iconRes, "LB") { onStep(-1) }
            Box { SectionTabs(glass = glass, sections = sections, selected = selected, onSelect = onSelect) }
            BumperButton(GamepadButton.RB.iconRes, "RB") { onStep(1) }
            Box { if (search != null) SearchField(search, searchFocus) }
            StatusIcons(avatarFocus)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val icons = measurables[4].measure(loose)
        val lb = measurables[0].measure(loose)
        val rb = measurables[2].measure(loose)
        val inner = constraints.maxWidth - 2 * pad
        val iconsX = constraints.maxWidth - pad - icons.width
        // The tabs get what the bumpers and the corner leave (plus a little room for the search).
        val tabsMax = (inner + bumperInset - lb.width - rb.width - icons.width - 3 * gap).coerceAtLeast(0)
        val tabsOneRow = measurables[1].measure(loose.copy(maxWidth = tabsMax))
        val leftEnd = pad - bumperInset + lb.width + gap + tabsOneRow.width + gap + rb.width
        val free = (iconsX - gap - (leftEnd + gap)).coerceAtLeast(0)
        if (free >= minSearch || search == null) {
            // One row: bumpers and tabs, the search in the middle of what is left, the corner.
            val searchBox = measurables[3].measure(loose.copy(maxWidth = free))
            val height = maxOf(icons.height, lb.height, tabsOneRow.height, searchBox.height)
            layout(constraints.maxWidth, height) {
                // Everything hangs from the same top line, so the tabs, the search and the avatar share the inset.
                var x = pad - bumperInset
                lb.placeRelative(x, 0); x += lb.width + gap
                tabsOneRow.placeRelative(x, 0); x += tabsOneRow.width + gap
                rb.placeRelative(x, 0)
                val centre = leftEnd + gap + free / 2
                searchBox.placeRelative(centre - searchBox.width / 2, 0)
                icons.placeRelative(iconsX, 0)
            }
        } else {
            // Too many tabs for one row with a search of a decent width: the search keeps the middle of the first
            // row with the corner, the tabs (all of them, between the bumpers) take a row of their own below.
            val tabs = measurables[1].measure(loose.copy(maxWidth = (inner + bumperInset - lb.width - rb.width - 2 * gap).coerceAtLeast(0)))
            val searchFree = (iconsX - gap - (pad + gap)).coerceAtLeast(0)
            val searchBox = measurables[3].measure(loose.copy(maxWidth = searchFree))
            val firstRow = maxOf(icons.height, searchBox.height)
            val secondRow = maxOf(lb.height, tabs.height)
            layout(constraints.maxWidth, firstRow + rowGapPx + secondRow) {
                val centre = pad + gap + searchFree / 2
                searchBox.placeRelative(centre - searchBox.width / 2, (firstRow - searchBox.height) / 2)
                icons.placeRelative(iconsX, (firstRow - icons.height) / 2)
                var x = pad - bumperInset
                val y = firstRow + rowGapPx
                lb.placeRelative(x, y + (secondRow - lb.height) / 2); x += lb.width + gap
                tabs.placeRelative(x, y + (secondRow - tabs.height) / 2); x += tabs.width + gap
                rb.placeRelative(x, y + (secondRow - rb.height) / 2)
            }
        }
    }
}

/** Search in the middle of the top bar: a black glass pill, an icon and the query (or "Search"). */
@Composable
private fun SearchField(search: SearchState, pillFocus: FocusRequester) {
    if (!search.typing) {
        // Not typing: a plain pill with the query; A opens the keyboard again.
        PillButton(
            onClick = search.onOpen,
            background = { focused -> if (focused) RiftAccent.current.brush else solid(Color.Transparent) },
            modifier = Modifier.widthIn(max = 320.dp).height(RiftSizes.button).clip(RiftShapes.full).blackGlass(RiftShapes.full),
            focusRequester = pillFocus,
        ) { focused ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.rift_search), tint = if (focused) RiftAccent.current.onAccent else RiftColors.OnSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Text(
                    text = search.query.ifEmpty { stringResource(R.string.rift_search) },
                    color = if (focused) RiftAccent.current.onAccent else if (search.query.isEmpty()) RiftColors.OnSurfaceVariant else RiftColors.OnSurface,
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
            .widthIn(max = 320.dp)
            .height(RiftSizes.button)
            .clip(RiftShapes.full)
            .blackGlass(RiftShapes.full)
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

/** Where a tab and its title sit on screen, to place the glass under it. */
private data class TabPlace(val tab: Offset, val tabSize: Size, val text: Offset, val textSize: Size)

/**
 * The section tabs. The selected one sits in a small capsule of liquid glass (the backdrop art,
 * blurred, with a light tint and a bright rim), which slides to the next tab on LB/RB with the
 * expressive spatial spring. No line under the title: the capsule shows the selection (Denis).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SectionTabs(
    glass: GraphicsLayer,
    sections: List<RiftSection>,
    selected: RiftSection?,
    onSelect: (RiftSection) -> Unit,
) {
    val context = LocalContext.current
    val customNames = remember { RiftTabPrefs.names(context) }
    // The tabs switch with LB/RB (or touch); the d-pad never lands on them.
    Box(modifier = Modifier.focusProperties { canFocus = false }) {
        GlassTabs(
            glass = glass,
            tabs = sections,
            selected = selected,
            title = { customNames[it.key] ?: stringResource(it.titleRes) },
            onSelect = onSelect,
        )
    }
}

/** Tabs with the sliding glass capsule; the home's sections and the library's sources. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun <T> GlassTabs(
    glass: GraphicsLayer,
    tabs: List<T>,
    selected: T?,
    title: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val places = remember { mutableStateMapOf<T, TabPlace>() }
    var origin by remember { mutableStateOf<Offset?>(null) }
    val capsuleX = remember { Animatable(0f) }
    val capsuleW = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val target = selected?.let { places[it] }
    val base = origin
    LaunchedEffect(target, base) {
        if (target == null || base == null) return@LaunchedEffect
        val cx = target.tab.x - base.x
        if (!placed) {
            capsuleX.snapTo(cx); capsuleW.snapTo(target.tabSize.width)
            placed = true
            return@LaunchedEffect
        }
        launch { capsuleX.animateTo(cx, spec) }
        launch { capsuleW.animateTo(target.tabSize.width, spec) }
    }
    val show = placed && target != null && base != null
    Box(
        modifier = Modifier.onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        if (show) {
            val top = target!!.tab.y - base!!.y
            val height = target.tabSize.height
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val w = capsuleW.value.roundToInt().coerceAtLeast(1)
                        val p = measurable.measure(Constraints.fixed(w, height.roundToInt()))
                        layout(0, 0) { p.place(capsuleX.value.roundToInt(), top.roundToInt()) }
                    }
                    .clip(RiftShapes.full)
                    .drawWithContent {
                        drawContent()
                        // The glass's soft highlight on its upper half.
                        drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.55f to Color.Transparent))
                    }
                    .graphicsLayer {
                        clip = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = BlurEffect(16.dp.toPx(), 16.dp.toPx(), TileMode.Clamp)
                        }
                    }
                    .drawBehind {
                        translate(left = -(base.x + capsuleX.value), top = -(base.y + top)) { drawLayer(glass) }
                        drawRect(Color.White.copy(alpha = 0.10f))
                    },
            )
        }
        // The d-pad enters the row on the chosen tab; Left/Right then choose the next one at once, so exactly
        // one tab is ever lit (the chosen one) and no A press is needed.
        val requesters = remember(tabs) { tabs.associateWith { FocusRequester() } }
        var tabsFocused by remember { mutableStateOf(false) }
        // A choice made elsewhere (the bumpers) takes the focus along, so the lit tab is always the focused one.
        LaunchedEffect(selected) {
            if (tabsFocused) selected?.let { runCatching { requesters[it]?.requestFocus() } }
        }
        Row(
            modifier = Modifier
                .onFocusChanged { tabsFocused = it.hasFocus }
                .focusProperties { enter = { selected?.let { requesters[it] } ?: FocusRequester.Default } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                SectionTab(
                    title = title(tab),
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    onPlaced = { places[tab] = it },
                    focusRequester = requesters.getValue(tab),
                    onStep = { step ->
                        val next = tabs.getOrNull(index + step) ?: return@SectionTab false
                        onSelect(next)
                        runCatching { requesters.getValue(next).requestFocus() }
                        true
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onPlaced: (TabPlace) -> Unit,
    focusRequester: FocusRequester,
    onStep: (Int) -> Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Tab and title report their places in either order; the place is known once both have.
    var tab by remember { mutableStateOf<Pair<Offset, Size>?>(null) }
    var text by remember { mutableStateOf<Pair<Offset, Size>?>(null) }
    fun report() {
        val t = tab ?: return
        val x = text ?: return
        onPlaced(TabPlace(t.first, t.second, x.first, x.second))
    }
    Box(
        modifier = Modifier
            .height(TabHeight)
            .onGloballyPositioned {
                tab = it.positionInRoot() to Size(it.size.width.toFloat(), it.size.height.toFloat())
                report()
            }
            .clip(RiftShapes.full)
            .then(if (focused) Modifier.background(RiftAccent.current.brush) else Modifier)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val step = when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@onPreviewKeyEvent false
                }
                // Both halves of the press belong to the step; at the ends the press moves on as usual.
                if (event.type == KeyEventType.KeyDown) onStep(step) else true
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (focused) RiftAccent.current.onAccent else if (selected) RiftColors.OnSurface else RiftColors.OnSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.onGloballyPositioned { coords ->
                text = coords.positionInRoot() to Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                report()
            },
        )
    }
}

private val TabHeight = 42.dp

@Composable
internal fun BumperButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
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
    // A logo the user picked in the art picker comes first.
    val picked = item.customLogoUrl
    val logoUrl by produceState<String?>(if (picked.isNotEmpty()) picked else if (isSteam) null else "", item.appId, picked) {
        value = when {
            picked.isNotEmpty() -> picked
            isSteam -> withContext(Dispatchers.IO) { GameLogos.steamLogoUrl(item.gameId) }
            else -> ""
        }
    }
    val url = logoUrl ?: return
    LaunchedEffect(item.appId, url) { Timber.tag("RiftArt").i("Logo for %s: %s", item.appId, url.ifEmpty { "none (name shown)" }) }
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
            } else if (!CloudLibrary.isCloud(item.appId)) {
                // A cloud game shows no name in its place: its logo comes in once it is found.
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

/** The hint icons are 28 dp; below them the screen inset, above them a little air. */
private val FooterHeight = 28.dp + ScreenPadding + 12.dp

/** Whether the hint footer is on, read by the library grids to keep their bottom space in step. */
private object HintBarState {
    var shown by androidx.compose.runtime.mutableStateOf(true)
}
private const val STARTUP_SETTLE_MS = 2500L
private const val FIRST_IMAGES_TIMEOUT_MS = 3500L
private const val FIRST_IMAGES_COUNT = 7
private const val APPS_PRELOAD_DELAY_MS = 4000L
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
private fun HintsBar(showFilters: Boolean, showOptions: Boolean = true, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(FooterHeight)
            .drawBehind { drawLine(Color.White.copy(alpha = 0.12f), Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .padding(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        // GameNative's own Kenney prompts (coloured face buttons), with its A/B X/Y swap setting.
        // Menus on the left, game actions on the right, like Big Picture; the tabs explain LB/RB.
        val swap = PrefManager.swapFaceButtons
        fun face(button: GamepadButton, swapped: GamepadButton) = if (swap) swapped.iconRes else button.iconRes
        if (showFilters) Hint(listOf(GamepadButton.SELECT.iconRes), stringResource(R.string.rift_hint_filters))
        Hint(listOf(InputIcons.Xbox.guide), stringResource(R.string.rift_hint_menu))
        Spacer(modifier = Modifier.weight(1f))
        if (showFilters && showOptions) Hint(listOf(face(GamepadButton.Y, GamepadButton.X)), stringResource(R.string.rift_hint_options))
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
internal class KeepInView(private val margin: Float, private val endMargin: Float = margin) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = margin
        val end = containerSize - endMargin
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

/** Loads what the home shows first (backdrop, the first covers) into Coil's cache, in parallel. */
private suspend fun preloadFirstImages(context: android.content.Context, library: List<LibraryItem>) = coroutineScope {
    val first = library.take(FIRST_IMAGES_COUNT)
    // Decoded into the memory cache, so the covers are on screen the moment the curtain lifts, but
    // bounded (a cover is never drawn bigger than this), so no full-size bitmaps pile up at start.
    val requests = buildList {
        first.firstOrNull()?.let { add(it.heroImageUrl.ifEmpty { it.headerImageUrl.ifEmpty { it.capsuleImageUrl } } to PRELOAD_BACKDROP_PX) }
        first.forEachIndexed { index, item ->
            val urls = if (index == 0) wideCoverUrls(item) else portraitCoverUrls(item)
            add(urls.firstOrNull { it.isNotEmpty() }.orEmpty() to PRELOAD_COVER_PX)
        }
    }.filter { it.first.isNotEmpty() }.distinctBy { it.first }
    requests.map { (url, px) ->
        async(Dispatchers.IO) {
            runCatching { context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(px).build()) }
        }
    }.awaitAll()
}

/** The filters panel's groups for a tab other than PC games: its sort and, for streaming, the scope. */
@Composable
private fun tabFilterGroups(view: TabView, onChange: (TabView) -> Unit): List<TabFilterGroup> = buildList {
    add(
        TabFilterGroup(
            label = stringResource(R.string.options_sort_by),
            value = stringResource(view.sort.titleRes),
            icon = Icons.Outlined.SwapVert,
            options = TabSort.entries.map { sort ->
                TabFilterOption(stringResource(sort.titleRes), view.sort == sort) { onChange(view.copy(sort = sort)) }
            },
        ),
    )
}

/** The PC games filters group that picks how games that are not installed are shown. */
@Composable
private fun uninstalledGroup(mode: UninstalledMode, onChange: (UninstalledMode) -> Unit): TabFilterGroup {
    fun titleRes(m: UninstalledMode) = when (m) {
        UninstalledMode.DIM -> R.string.rift_uninstalled_dim
        UninstalledMode.HIDE -> R.string.rift_uninstalled_hide
        UninstalledMode.SHOW -> R.string.rift_uninstalled_show
    }
    return TabFilterGroup(
        label = stringResource(R.string.rift_uninstalled_title),
        value = stringResource(titleRes(mode)),
        icon = Icons.Outlined.FilterList,
        options = UninstalledMode.entries.map { m ->
            TabFilterOption(stringResource(titleRes(m)), mode == m) { onChange(m) }
        },
    )
}

/** Which source tabs the home had when it was last left, so they are back on the first frame. */
private object RiftTabsMemory {
    var stream = false
    var cloud = false
    var emulators = false
}

private const val SOURCES_GRACE_MS = 2500L

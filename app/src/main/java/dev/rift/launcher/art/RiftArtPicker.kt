package dev.rift.launcher.art

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.events.AndroidEvent
import app.gamenative.data.GameSource
import app.gamenative.data.LibraryItem
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.home.CoverCard
import dev.rift.launcher.home.CoverImage
import dev.rift.launcher.home.LibraryTabSpec
import dev.rift.launcher.home.viewGap
import dev.rift.launcher.home.RiftLibraryScreen
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Set by the home screen while it can show the art picker: GameNative's game options then get a
 * "Change art" entry that calls it with the game.
 */
val LocalRiftArtPicker = staticCompositionLocalOf<((LibraryItem) -> Unit)?> { null }

private val ScreenPadding = 48.dp
private const val LOAD_MORE_THRESHOLD = 12
private const val RESET_KEY = -1L
private const val FOCUS_TRIES = 20
private const val RETRY_FEEDBACK_MS = 600L
private const val FOCUS_RETRY_MS = 120L

/** Pictures of one [ArtKind] for the game being edited: what was loaded so far and whether more may follow. */
private class ArtFeed {
    var options by mutableStateOf<List<ArtOption>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<ArtError?>(null)
    var done by mutableStateOf(false)
    var page = 0
}

/** One open picker: the key, the game as SteamGridDB knows it, and the feeds of the tabs visited so far. */
private class ArtSession(val item: LibraryItem, val key: String) {
    private val feeds = mutableMapOf<ArtKind, ArtFeed>()
    private var path: String? = null
    private var triedByName = false
    private val game = SteamGridDbClient.Game(
        steamAppId = item.gameId.takeIf { item.gameSource == GameSource.STEAM && it > 0 },
        name = item.name,
    )

    fun feed(kind: ArtKind): ArtFeed = feeds.getOrPut(kind) { ArtFeed() }

    suspend fun loadMore(kind: ArtKind) {
        val feed = feed(kind)
        if (feed.loading || feed.done) return
        feed.loading = true
        feed.error = null
        val started = System.currentTimeMillis()
        try {
            val loaded = fetch(kind, feed.page)
            val known = feed.options.mapTo(HashSet()) { it.id }
            val fresh = loaded.filter { it.id !in known }
            if (loaded.isEmpty()) feed.done = true
            feed.options = feed.options + fresh
            feed.page++
        } catch (e: ArtError) {
            // A failure that comes back at once would look like a retry that did nothing.
            delay((RETRY_FEEDBACK_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            feed.error = e
        } finally {
            feed.loading = false
        }
    }

    private suspend fun fetch(kind: ArtKind, page: Int): List<ArtOption> {
        val first = path ?: SteamGridDbClient.resolve(key, game).also { path = it }
        return try {
            SteamGridDbClient.options(key, first, kind, page)
        } catch (e: ArtError) {
            // A Steam game SteamGridDB does not list by its id may still be there under its name.
            if (e !== ArtError.NotFound || triedByName || game.steamAppId == null) throw e
            triedByName = true
            val byName = SteamGridDbClient.resolve(key, game, byName = true).also { path = it }
            SteamGridDbClient.options(key, byName, kind, page)
        }
    }
}

/**
 * The art picker, built like the Decky Loader SteamGridDB plugin and Big Picture's library: the picture
 * types are tabs between LB and RB, below them a grid of what SteamGridDB has for the game. A takes a
 * picture for the game (saved on the device and used on the home screen, in the library and on the game
 * page), B closes. [item] is null while closed; [openToken] changes with every opening, so a reopened picker starts afresh.
 */
@Composable
fun RiftArtPicker(
    item: LibraryItem?,
    openToken: Int,
    glass: GraphicsLayer,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Kept through the closing animation.
    var shown by remember { mutableStateOf<LibraryItem?>(null) }
    if (item != null) shown = item
    val current = shown ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val open = item != null
    // After a touch the window is in touch mode, where nothing takes gamepad focus: leave it on opening.
    LaunchedEffect(open, openToken) { if (open) view.requestFocusFromTouch() }
    key(openToken) {
        val session = remember { ArtSession(current, dev.rift.launcher.settings.RiftPrefs.of(context).steamGridDbKey) }
        var kindIndex by remember { mutableIntStateOf(0) }
        val kinds = ArtKind.entries
        val tabs = kinds.map { LibraryTabSpec(it.key, stringResource(it.titleRes)) }
        // Whether a picture (or the status below the tabs) holds focus, and a counter that asks the grid to take it.
        var contentFocused by remember { mutableStateOf(false) }
        var focusAsk by remember { mutableIntStateOf(0) }
        val latestDismiss = rememberUpdatedState(onDismiss)
        fun step(by: Int) {
            kindIndex = (kindIndex + by).mod(kinds.size)
        }
        val latestStep = rememberUpdatedState<(Int) -> Unit> { step(it) }
        val latestFocused = rememberUpdatedState(contentFocused)
        // The picker reads the gamepad itself, before Compose: a screen that was last touched has no focus,
        // and without focus no key would ever reach it. LB/RB switch the tabs and B closes whatever has focus;
        // a direction or A with nothing focused brings focus to the first picture.
        DisposableEffect(open) {
            if (!open) return@DisposableEffect onDispose { }
            var backDown = false
            val listener: (AndroidEvent.KeyEvent) -> Boolean = { androidEvent ->
                val ev = androidEvent.event
                val down = ev.action == KeyEvent.ACTION_DOWN
                when (ev.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                        if (down && ev.repeatCount == 0) latestStep.value(if (ev.keyCode == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE -> {
                        if (down) backDown = true
                        if (ev.action == KeyEvent.ACTION_UP && backDown) {
                            backDown = false
                            latestDismiss.value()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    -> if (latestFocused.value) {
                        false
                    } else {
                        if (down) focusAsk++
                        true
                    }
                    else -> false
                }
            }
            PluviaApp.events.on<AndroidEvent.KeyEvent, Boolean>(listener)
            onDispose { PluviaApp.events.off<AndroidEvent.KeyEvent, Boolean>(listener) }
        }
        RiftLibraryScreen(
            isOpen = open,
            glass = glass,
            tabs = tabs,
            selectedKey = kinds[kindIndex].key,
            onSelectTab = { key -> kinds.indexOfFirst { it.key == key }.takeIf { it >= 0 }?.let { kindIndex = it } },
            onDismiss = onDismiss,
            keepFocusInside = { true },
        ) {
            ArtGrid(
                session = session,
                kind = kinds[kindIndex],
                focusAsk = focusAsk,
                onFocusChange = { contentFocused = it },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ArtGrid(
    session: ArtSession,
    kind: ArtKind,
    focusAsk: Int,
    onFocusChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    // A fresh grid, scroll position and focus for every tab.
    key(kind) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val feed = session.feed(kind)
        val appId = session.item.appId
        val artVersion by GameArtStore.version.collectAsState()
        val hasOwn = remember(artVersion) { GameArtStore.get(context, appId, kind).isNotEmpty() }
        // The picture taken in this session, so it shows as chosen; the one being saved shows a spinner.
        var chosenId by remember { mutableStateOf<Long?>(null) }
        var savingId by remember { mutableStateOf<Long?>(null) }
        var pendingFocus by remember { mutableStateOf<Long?>(null) }

        var hasFocus by remember { mutableStateOf(false) }
        val focusNow = rememberUpdatedState(hasFocus)
        LaunchedEffect(Unit) { session.loadMore(kind) }
        // Focus the first picture once there is one, and again on request; a request that did not take
        // (the window was still in touch mode) is repeated for a moment.
        LaunchedEffect(feed.options.isNotEmpty(), focusAsk) {
            val first = feed.options.firstOrNull() ?: return@LaunchedEffect
            repeat(FOCUS_TRIES) {
                if (focusNow.value) return@LaunchedEffect
                pendingFocus = first.id
                delay(FOCUS_RETRY_MS)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged {
                    hasFocus = it.hasFocus
                    onFocusChange(it.hasFocus)
                },
        ) {
            Text(
                text = session.item.name,
                color = RiftColors.OnSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                if (feed.options.isEmpty()) {
                    ArtStatus(
                        feed = feed,
                        focusAsk = focusAsk,
                        onRetry = { scope.launch { session.loadMore(kind) } },
                        onOpenSettings = onOpenSettings,
                    )
                    return@Box
                }
                val gridState = rememberLazyGridState()
                val cell = cellWidth(kind)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cell),
                    state = gridState,
                    contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalArrangement = Arrangement.spacedBy(30.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (hasOwn) {
                        item(key = RESET_KEY) {
                            BoxWithConstraints(modifier = Modifier.viewGap()) {
                                ArtTile(
                                    kind = kind,
                                    width = maxWidth,
                                    focusKey = RESET_KEY,
                                    pendingFocus = pendingFocus,
                                    onFocusConsumed = { pendingFocus = null },
                                    onClick = {
                                        GameArtStore.reset(context, appId, kind)
                                        chosenId = null
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(Icons.Rounded.RestartAlt, null, tint = RiftColors.OnSurface, modifier = Modifier.size(34.dp))
                                        Text(
                                            text = stringResource(R.string.rift_art_default),
                                            color = RiftColors.OnSurface,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(feed.options, key = { _, option -> option.id }) { index, option ->
                        BoxWithConstraints(modifier = Modifier.viewGap()) {
                            ArtTile(
                                kind = kind,
                                width = maxWidth,
                                focusKey = option.id,
                                pendingFocus = pendingFocus,
                                onFocusConsumed = { pendingFocus = null },
                                onFocused = {
                                    if (index >= feed.options.size - LOAD_MORE_THRESHOLD) scope.launch { session.loadMore(kind) }
                                },
                                onClick = {
                                    if (savingId != null) return@ArtTile
                                    savingId = option.id
                                    scope.launch {
                                        GameArtStore.pick(context, appId, kind, option.url)
                                        chosenId = option.id
                                        savingId = null
                                    }
                                },
                                badge = {
                                    when (option.id) {
                                        savingId -> Box(
                                            modifier = Modifier.fillMaxSize().background(RiftColors.Background.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center,
                                        ) { CircularProgressIndicator(color = RiftColors.Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp)) }
                                        chosenId -> Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(RiftColors.Primary),
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Rounded.Check, null, tint = RiftColors.OnPrimary, modifier = Modifier.size(18.dp)) }
                                    }
                                },
                            ) {
                                ArtImage(kind = kind, url = option.thumb)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun cellWidth(kind: ArtKind): Dp = when (kind) {
    ArtKind.Capsule -> 160.dp
    ArtKind.Wide -> 300.dp
    ArtKind.Hero -> 440.dp
    ArtKind.Logo -> 280.dp
    ArtKind.Icon -> 130.dp
}

@Composable
private fun ArtTile(
    kind: ArtKind,
    width: Dp,
    focusKey: Long,
    pendingFocus: Long?,
    onFocusConsumed: () -> Unit,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    badge: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    cover: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    CoverCard(
        height = width / kind.ratio,
        aspectRatio = kind.ratio,
        paletteKey = "art:$focusKey",
        loadPalette = { null },
        requestFocus = pendingFocus == focusKey,
        onFocusRequested = onFocusConsumed,
        onFocused = onFocused,
        onClick = onClick,
        cover = cover,
        badge = badge,
        reflection = false,
    )
}

/** Logos and icons are transparent and shown whole on a quiet backdrop; the rest fill their tile. */
@Composable
private fun ArtImage(kind: ArtKind, url: String) {
    if (kind == ArtKind.Logo || kind == ArtKind.Icon) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(RiftColors.SurfaceContainerHigh, RiftColors.Surface)))
                .padding(14.dp),
        ) {
            CoilImage(
                modifier = Modifier.fillMaxSize(),
                imageModel = { url },
                imageOptions = ImageOptions(contentScale = ContentScale.Fit, contentDescription = null),
                loading = {},
                failure = {},
            )
        }
    } else {
        CoverImage(urls = listOf(url), contentDescription = null)
    }
}

/** Loading, nothing found or an error, always with something focusable so a gamepad is never stranded. */
@Composable
private fun ArtStatus(feed: ArtFeed, focusAsk: Int, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val error = feed.error
    val focus = remember { FocusRequester() }
    val message = when {
        error === ArtError.BadKey -> stringResource(R.string.rift_art_bad_key)
        error === ArtError.NotFound -> stringResource(R.string.rift_art_not_found)
        error != null -> stringResource(R.string.rift_art_network)
        feed.done -> stringResource(R.string.rift_art_empty)
        else -> null
    }
    var focused by remember { mutableStateOf(false) }
    val focusedNow = rememberUpdatedState(focused)
    LaunchedEffect(message, focusAsk) {
        repeat(FOCUS_TRIES) {
            if (focusedNow.value) return@LaunchedEffect
            runCatching { focus.requestFocus() }
            delay(FOCUS_RETRY_MS)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding).onFocusChanged { focused = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (message == null) {
            // Loading: a quiet spinner on a focusable box, so the keys of the picker still work.
            Box(modifier = Modifier.focusRequester(focus).focusable()) {
                CircularProgressIndicator(color = RiftColors.OnSurfaceVariant, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            }
        } else {
            Text(
                text = message,
                color = RiftColors.OnSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 18.dp),
            )
            when {
                error === ArtError.BadKey -> RiftPillButton(
                    text = stringResource(R.string.rift_art_open_settings),
                    onClick = onOpenSettings,
                    primary = true,
                    modifier = Modifier.focusRequester(focus),
                )
                error != null && error !== ArtError.NotFound -> RiftPillButton(
                    text = stringResource(R.string.rift_art_retry),
                    onClick = onRetry,
                    primary = true,
                    modifier = Modifier.focusRequester(focus),
                )
                else -> Box(modifier = Modifier.focusRequester(focus).focusable())
            }
        }
    }
}

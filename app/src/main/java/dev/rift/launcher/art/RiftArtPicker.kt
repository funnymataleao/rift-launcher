package dev.rift.launcher.art

import android.view.KeyEvent
import dev.rift.launcher.ui.theme.RiftAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
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
import androidx.compose.ui.focus.focusProperties
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
import dev.rift.launcher.home.RiftLibraryScreen
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.RiftSounds
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * Rift: what SteamGridDB answered, for the whole run: each game's path and each tab's pictures so far. Its API is
 * slow (about two seconds an answer), so a picker opened again for the same game shows them at once.
 */
private object ArtMemory {
    class Saved(val options: List<ArtOption>, val page: Int, val done: Boolean)
    val paths = java.util.concurrent.ConcurrentHashMap<String, String>()
    val feeds = java.util.concurrent.ConcurrentHashMap<String, Saved>()
    fun key(appId: String, kind: ArtKind) = "$appId|${kind.name}"
}

/** One open picker: the key, the game as SteamGridDB knows it, and the feeds of the tabs visited so far. */
private class ArtSession(val item: LibraryItem, val key: String) {
    private val feeds = mutableMapOf<ArtKind, ArtFeed>()
    private var path: String? = ArtMemory.paths[item.appId]
    // The game is looked up once, however many tabs ask at the same time.
    private val pathLock = kotlinx.coroutines.sync.Mutex()
    private var triedByName = false
    private val game = SteamGridDbClient.Game(
        steamAppId = item.gameId.takeIf { item.gameSource == GameSource.STEAM && it > 0 },
        name = item.name,
    )

    fun feed(kind: ArtKind): ArtFeed = feeds.getOrPut(kind) {
        ArtFeed().apply {
            ArtMemory.feeds[ArtMemory.key(item.appId, kind)]?.let { saved ->
                options = saved.options
                page = saved.page
                done = saved.done
            }
        }
    }

    suspend fun loadMore(kind: ArtKind) {
        val feed = feed(kind)
        if (feed.loading || feed.done) return
        feed.loading = true
        feed.error = null
        val started = System.currentTimeMillis()
        try {
            // Steam's own picture first: a game too new for SteamGridDB still has it.
            val steam = if (feed.page == 0) steamOptions(kind) else emptyList()
            val loaded = try {
                fetch(kind, feed.page)
            } catch (e: ArtError) {
                if (steam.isEmpty()) throw e
                // Steam's picture is shown; SteamGridDB's are only missing (no key, game unknown) or come on a retry.
                if (e !== ArtError.Network) feed.done = true
                feed.options = (steam + feed.options).distinctBy { it.id }
                ArtMemory.feeds[ArtMemory.key(item.appId, kind)] = ArtMemory.Saved(feed.options, feed.page, feed.done)
                return
            }
            val known = feed.options.mapTo(HashSet()) { it.id }
            val fresh = (steam + loaded).filter { it.id !in known }
            if (loaded.isEmpty()) feed.done = true
            feed.options = feed.options + fresh
            feed.page++
            ArtMemory.feeds[ArtMemory.key(item.appId, kind)] = ArtMemory.Saved(feed.options, feed.page, feed.done)
        } catch (e: ArtError) {
            // A failure that comes back at once would look like a retry that did nothing.
            delay((RETRY_FEEDBACK_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
            feed.error = e
        } finally {
            feed.loading = false
        }
    }

    /** Steam's own picture of [kind] for a Steam game, as a pick (its full size saved, the small one shown). */
    private suspend fun steamOptions(kind: ArtKind): List<ArtOption> {
        val steamId = game.steamAppId ?: return emptyList()
        val urls = withContext(Dispatchers.IO) { SteamStoreArt.assets(steamId) }?.of(kind).orEmpty()
        val shown = urls.firstOrNull() ?: return emptyList()
        // The larger copy when Steam has one (a "_2x" file); ids below zero never clash with SteamGridDB's.
        val full = urls.firstOrNull { it.substringBefore('?').contains("_2x.") } ?: shown
        return listOf(ArtOption(id = StandardArt.STEAM_OPTION_ID - kind.ordinal, thumb = shown, url = full, width = 0, height = 0))
    }

    private suspend fun fetch(kind: ArtKind, page: Int): List<ArtOption> {
        val first = pathLock.withLock { path ?: SteamGridDbClient.resolve(key, game).also { remember(it) } }
        return try {
            SteamGridDbClient.options(key, first, kind, page)
        } catch (e: ArtError) {
            // A Steam game SteamGridDB does not list by its id may still be there under its name.
            if (e !== ArtError.NotFound || game.steamAppId == null) throw e
            val byName = pathLock.withLock {
                if (triedByName) path ?: throw e
                else {
                    triedByName = true
                    SteamGridDbClient.resolve(key, game, byName = true).also { remember(it) }
                }
            }
            SteamGridDbClient.options(key, byName, kind, page)
        }
    }

    private fun remember(found: String) {
        path = found
        ArtMemory.paths[item.appId] = found
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
        // Rift: every tab's first page at once, in parallel, so switching tabs does not wait for SteamGridDB.
        LaunchedEffect(session) {
            kotlinx.coroutines.coroutineScope { kinds.forEach { kind -> launch { session.loadMore(kind) } } }
        }
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
        // Y: Steam's default art for this game only (like SteamGridDB's "apply default art").
        val scope = rememberCoroutineScope()
        var applyingStandard by remember { mutableStateOf(false) }
        fun applyStandard() {
            if (applyingStandard) return
            applyingStandard = true
            scope.launch {
                val set = try { StandardArt.apply(context, current) } finally { applyingStandard = false }
                if (set) RiftSounds.play(context, RiftSound.Apply)
                app.gamenative.ui.util.SnackbarManager.show(
                    context.getString(if (set) R.string.rift_art_standard_done else R.string.rift_art_standard_none),
                )
            }
        }
        val latestStandard = rememberUpdatedState<() -> Unit> { applyStandard() }
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
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (down && ev.repeatCount == 0) latestStandard.value()
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
            headerAction = { StandardArtPill(busy = applyingStandard, onClick = ::applyStandard) },
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
        // The picture in use (kept by the store, so it shows as chosen on every visit); the one being saved shows a spinner.
        val chosenId = remember(artVersion) { GameArtStore.currentOptionId(context, appId, kind).takeIf { it != 0L } }
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
                            BoxWithConstraints {
                                ArtTile(
                                    kind = kind,
                                    width = maxWidth,
                                    focusKey = RESET_KEY,
                                    pendingFocus = pendingFocus,
                                    onFocusConsumed = { pendingFocus = null },
                                    onClick = {
                                        GameArtStore.reset(context, appId, kind)
                                        RiftSounds.play(context, RiftSound.Apply)
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
                        BoxWithConstraints {
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
                                    // The pick belongs to the app, not to this tab: switching tabs or leaving does not cancel it.
                                    val job = GameArtStore.pickAsync(context, appId, kind, option.url, option.id)
                                    scope.launch {
                                        job.join()
                                        savingId = null
                                        // The check mark shows it too, but while going through the tabs it is the chime that tells.
                                        RiftSounds.play(context, RiftSound.Apply)
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
                                                .background(RiftAccent.current.brush),
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

/**
 * The header's "Default covers" pill, in the game page's hint style: a translucent half-round pill with the
 * Y button and its label. The gamepad uses Y itself, so the pill is for touch only and never takes focus.
 */
@Composable
private fun StandardArtPill(busy: Boolean, onClick: () -> Unit) {
    val button = if (app.gamenative.PrefManager.swapFaceButtons) app.gamenative.ui.component.GamepadButton.X else app.gamenative.ui.component.GamepadButton.Y
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .height(RiftSizes.button)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(color = RiftColors.OnSurface, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(button.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.rift_art_standard),
            color = RiftColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

package dev.rift.launcher.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Settings
import app.gamenative.ui.component.GamepadButton
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftMotion
import dev.rift.launcher.ui.theme.RiftShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B on the home, Beacon style: every installed Android app in a grid over the blurred home.
 * A opens an app, B closes the grid. Same app list as the Android games picker in settings.
 * [focusRequester] puts focus back into the grid, [onFocusChanged] says whether it has it: a tile
 * scrolled far away can take the focus with it, and then the home sends keys here itself.
 */
@Composable
fun RiftAppsGrid(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChanged: (Boolean) -> Unit = {},
) {
    BackHandler(enabled = isOpen) { onDismiss() }
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn() + scaleIn(initialScale = 0.96f, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        AppsGridContent(onDismiss = onDismiss, focusRequester = focusRequester, onFocusChanged = onFocusChanged)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AppsGridContent(onDismiss: () -> Unit, focusRequester: FocusRequester, onFocusChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    // Opens on the cached list at once; the fresh one replaces it when ready.
    val apps by produceState(AndroidGames.cachedLaunchable) {
        value = withContext(Dispatchers.IO) { runCatching { AndroidGames.launchable(context) }.getOrDefault(emptyList()) }
    }
    val lastOpened by produceState(emptyMap<String, Long>()) {
        value = withContext(Dispatchers.IO) { AndroidGames.lastOpened(context) }
    }
    var sort by remember { mutableStateOf(AppsSort.load(context)) }
    fun setSort(option: AppsSort) {
        sort = option
        AppsSort.save(context, sort)
    }
    fun nextSort() = setSort(AppsSort.entries[(sort.ordinal + 1) % AppsSort.entries.size])
    val firstFocus = remember { FocusRequester() }
    // The d-pad enters the row of orders on the chosen one; Left/Right choose the neighbour at once.
    val pillFocus = remember { AppsSort.entries.associateWith { FocusRequester() } }
    var pillsFocused by remember { mutableStateOf(false) }
    fun stepSort(step: Int, wrap: Boolean): Boolean {
        val all = AppsSort.entries
        val raw = sort.ordinal + step
        val index = if (wrap) raw.mod(all.size) else raw
        val next = all.getOrNull(index) ?: return false
        setSort(next)
        if (pillsFocused) runCatching { pillFocus.getValue(next).requestFocus() }
        return true
    }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Search: A on the field starts typing; B (or Down) hides the keyboard and goes back to the
    // grid, B again closes the grid.
    var query by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    fun stopTyping() {
        typing = false
        keyboard?.hide()
        runCatching { firstFocus.requestFocus() }.onFailure { runCatching { searchFocus.requestFocus() } }
    }
    BackHandler(enabled = typing) { stopTyping() }
    LaunchedEffect(apps) { if (!apps.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }
    val shown = remember(apps, query, sort, lastOpened) {
        apps?.let { all ->
            val found = if (query.isBlank()) all else all.filter { it.label.contains(query.trim(), ignoreCase = true) }
            when (sort) {
                AppsSort.RECENT -> found.sortedWith(
                    compareByDescending<AndroidGame> { lastOpened[it.packageName] ?: 0L }.thenByDescending { it.installedAt },
                )
                AppsSort.NEWEST -> found.sortedByDescending { it.installedAt }
                AppsSort.NAME -> found.sortedBy { it.label.lowercase() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            // Focus stays inside the grid: scrolling past the edge must not land on the home behind it.
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                // X changes the order, LB/RB step through the orders like the tabs of the home.
                if (code == KeyEvent.KEYCODE_BUTTON_X && !typing) {
                    if (event.type == KeyEventType.KeyUp) nextSort()
                    return@onPreviewKeyEvent true
                }
                if (!typing && (code == KeyEvent.KEYCODE_BUTTON_L1 || code == KeyEvent.KEYCODE_BUTTON_R1)) {
                    if (event.type == KeyEventType.KeyDown) stepSort(if (code == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1, wrap = true)
                    return@onPreviewKeyEvent true
                }
                val leaveField = typing && code == KeyEvent.KEYCODE_DPAD_DOWN
                if (code != KeyEvent.KEYCODE_BUTTON_B && !leaveField) return@onPreviewKeyEvent false
                // Both halves of the press are consumed so focus never falls out.
                if (event.type == KeyEventType.KeyUp) {
                    if (typing) stopTyping() else if (!leaveField) onDismiss()
                }
                true
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 36.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rift_apps_title),
                    color = RiftColors.OnSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                BumperButton(GamepadButton.LB.iconRes, "LB") { stepSort(-1, wrap = true) }
                Row(
                    modifier = Modifier
                        .onFocusChanged { pillsFocused = it.hasFocus }
                        .focusProperties { enter = { pillFocus.getValue(sort) } }
                        .focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppsSort.entries.forEachIndexed { index, option ->
                        SortPill(
                            text = stringResource(option.labelRes),
                            selected = option == sort,
                            focusRequester = pillFocus.getValue(option),
                            onClick = { setSort(option) },
                            onStep = { step -> stepSort(step, wrap = false) },
                        )
                    }
                }
                BumperButton(GamepadButton.RB.iconRes, "RB") { stepSort(1, wrap = true) }
                Spacer(modifier = Modifier.width(16.dp))
                AppSearchField(
                    query = query,
                    typing = typing,
                    focusRequester = searchFocus,
                    onStart = { typing = true },
                    onQuery = { query = it },
                    onDone = ::stopTyping,
                )
                Spacer(modifier = Modifier.width(10.dp))
                SettingsButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                })
            }
            Spacer(modifier = Modifier.height(20.dp))
            val list = shown
            if (list == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(color = RiftColors.OnSurface) }
            } else {
                val density = LocalDensity.current
                val spec = remember(density) { KeepInView(with(density) { 48.dp.toPx() }, with(density) { 100.dp.toPx() }) }
                CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    // Room above and below, so the first and last rows (and a focused, slightly larger tile) show whole.
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.packageName }) { app ->
                        AppTile(
                            app = app,
                            focusRequester = if (app == list.first()) firstFocus else null,
                            onClick = { AndroidGames.launch(context, app) },
                        )
                    }
                }
                }
            }
        }
    }
}

/** The apps grid's order, kept between openings (rift_prefs / apps_sort). */
enum class AppsSort(val labelRes: Int) {
    RECENT(R.string.rift_apps_sort_recent),
    NEWEST(R.string.rift_apps_sort_newest),
    NAME(R.string.rift_apps_sort_name),
    ;

    companion object {
        private const val KEY = "apps_sort"
        fun load(context: android.content.Context): AppsSort =
            runCatching { valueOf(AndroidGames.prefs(context).getString(KEY, null) ?: RECENT.name) }.getOrDefault(RECENT)
        fun save(context: android.content.Context, sort: AppsSort) {
            AndroidGames.prefs(context).edit().putString(KEY, sort.name).apply()
        }
    }
}

/**
 * One of the orders as a pill. Exactly one is lit: the chosen one (accent fill, dark text), and the d-pad only
 * ever rests on the chosen one, so the focus and the choice cannot disagree. Left/Right choose the neighbour.
 */
@Composable
private fun SortPill(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onStep: (Int) -> Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, RiftMotion.focusSpring(), label = "sortPillScale")
    val accent = RiftAccent.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RiftShapes.full)
            .background(if (selected) accent.brush else SolidColor(Color.White.copy(alpha = 0.08f)))
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
            .padding(horizontal = 22.dp),
    ) {
        Text(
            text = text,
            color = if (selected) accent.onAccent else RiftColors.OnSurface,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** A round gear on the same translucent plate as the order pills: opens Android's settings. */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, RiftMotion.focusSpring(), label = "gearScale")
    val accent = RiftAccent.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (focused) accent.brush else SolidColor(Color.White.copy(alpha = 0.08f)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            Icons.Rounded.Settings,
            contentDescription = null,
            tint = if (focused) accent.onAccent else RiftColors.OnSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AppSearchField(
    query: String,
    typing: Boolean,
    focusRequester: FocusRequester,
    onStart: () -> Unit,
    onQuery: (String) -> Unit,
    onDone: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(typing) { if (typing) runCatching { fieldFocus.requestFocus() } }
    Row(
        modifier = Modifier
            .width(360.dp)
            .height(52.dp)
            .clip(RiftShapes.full)
            .background(if (focused && !typing) RiftAccent.current.brush else SolidColor(Color.White.copy(alpha = if (typing) 0.14f else 0.08f)))
            .focusRequester(focusRequester)
            .then(if (typing) Modifier else Modifier.clickable(interactionSource = interaction, indication = null, onClick = onStart))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = if (focused && !typing) RiftAccent.current.onAccent else RiftColors.OnSurfaceVariant, modifier = Modifier.size(20.dp))
        if (typing) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = RiftColors.OnSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(RiftColors.OnSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
            )
        } else {
            Text(
                text = query.ifEmpty { stringResource(R.string.rift_search) },
                color = if (focused) RiftAccent.current.onAccent else if (query.isEmpty()) RiftColors.OnSurfaceVariant else RiftColors.OnSurface,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AppTile(app: AndroidGame, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "appScale")
    val icon by produceState(AndroidGames.iconCached(app.packageName), app.packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { AndroidGames.icon(context, app.packageName) }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RiftShapes.card)
            .background(if (focused) RiftAccent.current.brush else SolidColor(Color.White.copy(alpha = 0.04f)))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            icon?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(64.dp)) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = app.label,
            color = if (focused) RiftAccent.current.onAccent else RiftColors.OnSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            // One line for every tile, so all tiles are the same height.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

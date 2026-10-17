package dev.rift.launcher.meta

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import dev.rift.launcher.settings.NoteText
import dev.rift.launcher.settings.RiftDialog
import dev.rift.launcher.settings.RiftInputPlate
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.sound.RiftSound
import dev.rift.launcher.sound.RiftSounds
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FOCUS_TRIES = 20
private const val FOCUS_RETRY_MS = 120L
private const val MAX_QUERY_LENGTH = 80
private const val SNIPPET_CHARS = 170

/** Where a search stands. */
private sealed interface PickerState {
    data object Searching : PickerState
    class Done(val search: MetaSearch) : PickerState
}

/**
 * Choosing a game's information by hand, for a game the sources did not find by themselves or found wrongly:
 * every source's hits for the game's name (which can be edited) as a list of title, system, year and the start
 * of the description. A takes one: it is saved for the game and nothing replaces it by itself. B closes.
 * [item] is null while closed; [openToken] changes with every opening, so a reopened picker starts afresh.
 */
@Composable
fun RiftMetaPicker(
    item: LibraryItem?,
    platformId: String?,
    openToken: Int,
    onDismiss: () -> Unit,
    onChosen: () -> Unit,
) {
    // Kept through the closing animation.
    var shown by remember { mutableStateOf<LibraryItem?>(null) }
    if (item != null) shown = item
    val current = shown ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val open = item != null
    LaunchedEffect(open, openToken) { if (open) view.requestFocusFromTouch() }
    key(openToken) {
        val scope = rememberCoroutineScope()
        var query by remember { mutableStateOf(TitleCleaner.clean(current.name)) }
        var state by remember { mutableStateOf<PickerState>(PickerState.Searching) }
        var editing by remember { mutableStateOf(false) }
        var savingRef by remember { mutableStateOf<String?>(null) }
        var contentFocused by remember { mutableStateOf(false) }
        var focusAsk by remember { mutableIntStateOf(0) }
        val latestDismiss = rememberUpdatedState(onDismiss)
        val latestFocused = rememberUpdatedState(contentFocused)
        val latestEditing = rememberUpdatedState(editing)

        LaunchedEffect(query) {
            state = PickerState.Searching
            state = PickerState.Done(
                try {
                    MetaCandidates.search(context, current, platformId, query)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    timber.log.Timber.tag("RiftMeta").w(e, "manual search failed")
                    MetaSearch(emptyList(), unreachable = true)
                },
            )
        }
        // The picker reads the gamepad itself, before Compose (see the art picker): B closes, and a direction or A
        // with nothing focused brings focus back into the list.
        DisposableEffect(open) {
            if (!open) return@DisposableEffect onDispose { }
            var backDown = false
            val listener: (AndroidEvent.KeyEvent) -> Boolean = { androidEvent ->
                val ev = androidEvent.event
                val down = ev.action == KeyEvent.ACTION_DOWN
                when {
                    // The query dialog is a window of its own and takes its keys itself.
                    latestEditing.value -> false
                    ev.keyCode == KeyEvent.KEYCODE_BUTTON_B || ev.keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                        if (down) backDown = true
                        if (ev.action == KeyEvent.ACTION_UP && backDown) {
                            backDown = false
                            latestDismiss.value()
                        }
                        true
                    }
                    ev.keyCode in NAV_KEYS -> if (latestFocused.value) {
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

        AnimatedVisibility(
            visible = open,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            androidx.activity.compose.BackHandler(enabled = open) { onDismiss() }
            val search = (state as? PickerState.Done)?.search
            val candidates = search?.candidates.orEmpty()
            val plateFocus = remember { FocusRequester() }
            val firstRow = remember { FocusRequester() }
            val focusNow = rememberUpdatedState(contentFocused)
            var listFocused by remember { mutableStateOf(false) }
            val listFocusedNow = rememberUpdatedState(listFocused)
            // New results take the focus to the first hit (otherwise to the search field), so a gamepad is never
            // stranded; a press with nothing focused brings it back the same way.
            LaunchedEffect(state, focusAsk) {
                if (state is PickerState.Searching) return@LaunchedEffect
                val wantList = candidates.isNotEmpty()
                repeat(FOCUS_TRIES) {
                    if (if (wantList) listFocusedNow.value else focusNow.value) return@LaunchedEffect
                    runCatching { if (wantList) firstRow.requestFocus() else plateFocus.requestFocus() }
                    delay(FOCUS_RETRY_MS)
                }
            }
            // The screen behind is blurred by the home (it counts this picker as an open layer), like behind the Start
            // menu; a little darker on top, and the list sits in a column in the middle.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .onFocusChanged { contentFocused = it.hasFocus },
                contentAlignment = Alignment.TopCenter,
            ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 820.dp)) {
                Text(
                    text = stringResource(R.string.rift_meta_pick_title, current.name),
                    color = RiftColors.OnSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 40.dp),
                )
                Text(
                    text = stringResource(R.string.rift_meta_pick_hint),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                SearchPlate(
                    query = query,
                    onClick = { editing = true },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).focusRequester(plateFocus),
                )
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when {
                        state is PickerState.Searching -> Status(stringResource(R.string.rift_meta_searching), busy = true)
                        candidates.isEmpty() -> Status(
                            stringResource(if (search?.unreachable == true) R.string.rift_meta_offline else R.string.rift_meta_empty),
                        )
                        else -> LazyColumn(
                            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize().onFocusChanged { listFocused = it.hasFocus },
                        ) {
                            itemsIndexed(candidates, key = { _, c -> c.source + c.ref }) { index, candidate ->
                                CandidateRow(
                                    candidate = candidate,
                                    saving = savingRef == candidate.source + candidate.ref,
                                    modifier = if (index == 0) Modifier.focusRequester(firstRow) else Modifier,
                                    onClick = {
                                        if (savingRef != null) return@CandidateRow
                                        savingRef = candidate.source + candidate.ref
                                        scope.launch {
                                            val meta = MetaCandidates.complete(context, candidate)
                                            RiftAutoMeta.pin(context, current.appId, meta)
                                            timber.log.Timber.tag("RiftMeta").i("%s: chosen by hand: %s \"%s\"", current.name, candidate.source, candidate.title)
                                            savingRef = null
                                            RiftSounds.play(context, RiftSound.Apply)
                                            onChosen()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            }
            if (editing) {
                QueryDialog(
                    initial = query,
                    onSave = { text ->
                        editing = false
                        val next = text.trim()
                        if (next.isNotEmpty() && next != query) query = next
                    },
                    onDismiss = { editing = false },
                )
            }
        }
    }
}

private val NAV_KEYS = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
)

@Composable
private fun SearchPlate(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPlate(focused, RiftShapes.full)
            .selectable(selected = focused, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = plateText(focused), modifier = Modifier.size(22.dp))
        Text(
            text = query,
            color = plateText(focused),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = stringResource(R.string.rift_meta_search_edit), color = plateSubtext(focused), fontSize = 14.sp)
    }
}

@Composable
private fun CandidateRow(candidate: MetaCandidate, saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val source = when (candidate.source) {
        "igdb" -> "IGDB"
        "steam" -> "Steam"
        "play" -> "Google Play"
        else -> stringResource(R.string.rift_meta_src_wikipedia)
    }
    val line = listOfNotNull(
        source,
        candidate.platform.takeIf { it.isNotBlank() && it != source },
        candidate.year?.toString(),
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPlate(focused, RiftShapes.card)
            .selectable(selected = focused, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = candidate.title,
                color = plateText(focused),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = line, color = plateSubtext(focused), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val snippet = candidate.meta.description.replace(Regex("\\s+"), " ").trim()
            if (snippet.isNotEmpty()) {
                Text(
                    text = if (snippet.length > SNIPPET_CHARS) snippet.take(SNIPPET_CHARS).trimEnd() + "…" else snippet,
                    color = plateSubtext(focused),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (saving) {
            CircularProgressIndicator(color = plateText(focused), strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        }
    }
}

/** Searching, nothing found or an error: always a line of text, and the search field above keeps the focus. */
@Composable
private fun Status(text: String, busy: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = RiftColors.OnSurfaceVariant, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            Box(Modifier.size(14.dp))
        }
        Text(text = text, color = RiftColors.OnSurfaceVariant, fontSize = 16.sp)
    }
}

@Composable
private fun QueryDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    RiftDialog(
        title = stringResource(R.string.rift_meta_query_title),
        onDismiss = onDismiss,
        actions = {
            RiftPillButton(text = stringResource(R.string.rift_settings_cancel), onClick = onDismiss)
            RiftPillButton(text = stringResource(R.string.rift_meta_search_go), onClick = { onSave(text) }, primary = true)
        },
    ) {
        RiftInputPlate { focused ->
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(MAX_QUERY_LENGTH) },
                singleLine = true,
                textStyle = TextStyle(color = plateText(focused), fontSize = 17.sp),
                cursorBrush = SolidColor(plateText(focused)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSave(text) }),
                modifier = Modifier.weight(1f).focusRequester(field),
            )
        }
        NoteText(stringResource(R.string.rift_meta_query_hint))
    }
}

/** A black glass plate like the Start menu's; the accent fills it while it has focus. */
@Composable
private fun Modifier.glassPlate(focused: Boolean, shape: Shape): Modifier =
    if (focused) clip(shape).background(RiftAccent.current.brush) else clip(shape).blackGlass(shape)

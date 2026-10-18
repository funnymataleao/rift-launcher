package dev.rift.launcher.assistant.chat

import android.Manifest
import android.app.Activity
import android.speech.RecognizerIntent
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import app.gamenative.R
import app.gamenative.ui.component.NoExtractOutlinedTextField
import app.gamenative.ui.screen.support.MarkdownText
import app.gamenative.ui.screen.support.rememberMarkdown
import dev.rift.launcher.assistant.ai.AiSettings
import dev.rift.launcher.assistant.optimize.ProposalCard
import dev.rift.launcher.settings.AssistantModelPicker
import dev.rift.launcher.settings.AssistantSettings
import dev.rift.launcher.settings.connectionName
import dev.rift.launcher.settings.NoteText
import dev.rift.launcher.settings.RiftChip
import dev.rift.launcher.settings.RiftChoiceDialog
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.settings.RiftRoundButton
import dev.rift.launcher.settings.plateFill
import dev.rift.launcher.settings.plateSubtext
import dev.rift.launcher.settings.plateText
import dev.rift.launcher.ui.theme.RiftAccent
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftShapes
import dev.rift.launcher.ui.theme.RiftTheme

private val ContentMaxWidth = 980.dp

/** The assistant page's place in GameNative's navigation graph. */
const val RIFT_ASSISTANT_ROUTE = "rift_assistant"

/**
 * The assistant page, opened from "AI Help" in the right side panel. Until a provider is connected it shows the same
 * rows as Settings › AI assistant; then a chat about the device and, when one is attached, a game. A proposal in an
 * answer shows as GameNative's suggestion card with Apply and Restore.
 *
 * Gamepad: A presses (on the message field it opens the keyboard), hold Y to talk and let go to send, B goes back
 * (from the connection rows to the chat first, when a provider is connected).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RiftAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AiSettings.of(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val stop = settings.onChange { revision++ }
        onDispose { stop() }
    }
    val ready = remember(revision) { settings.isReady }
    val connections = remember(revision) { settings.connections }
    val active = remember(revision) { settings.active }
    val model = remember(revision) { active?.let(settings::model) }
    var showSetup by rememberSaveable { mutableStateOf(!settings.isReady) }

    val chat by AssistantChat.state.collectAsState()
    val history by AssistantChat.history.collectAsState()
    LaunchedEffect(Unit) { AssistantChat.loadHistory(context) }
    var draft by rememberSaveable { mutableStateOf("") }
    // Screenshots to send with the next message (JPEG, base64).
    var attached by remember { mutableStateOf(emptyList<String>()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ChatImages.encode(context, uri)?.let { attached = attached + it } }
    }
    var pickingGame by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var pickingModel by remember { mutableStateOf(false) }
    var voiceNote by remember { mutableStateOf<String?>(null) }
    val voice = rememberVoiceInput()

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voiceNote = if (granted) null else context.getString(R.string.rift_chat_voice_permission)
    }

    fun send(text: String) {
        if ((text.isBlank() && attached.isEmpty()) || chat.sending) return
        AssistantChat.send(context, text, attached)
        draft = ""
        attached = emptyList()
    }

    // Where the recognizer service can't be used directly: the system's own voice input window.
    val systemVoice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (result.resultCode == Activity.RESULT_OK && text.isNotEmpty()) send(text)
    }

    fun talk() {
        if (voice.listening || chat.sending) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        voiceNote = null
        voice.start(
            onText = { send(it) },
            onProblem = {
                if (it == VoiceInput.Problem.UseSystemDialog) {
                    voiceNote = null
                    runCatching { systemVoice.launch(voice.systemDialogIntent()) }
                        .onFailure { voiceNote = context.getString(R.string.rift_chat_voice_failed) }
                    return@start
                }
                voiceNote = when (it) {
                    VoiceInput.Problem.Language ->
                        context.getString(R.string.rift_chat_voice_language, Locale.getDefault().displayLanguage)
                    else -> context.getString(
                        when (it) {
                            VoiceInput.Problem.Unavailable -> R.string.rift_chat_voice_unavailable
                            VoiceInput.Problem.NotHeard -> R.string.rift_chat_voice_not_heard
                            VoiceInput.Problem.NoPermission -> R.string.rift_chat_voice_no_permission
                            VoiceInput.Problem.Network -> R.string.rift_chat_voice_network
                            VoiceInput.Problem.Busy -> R.string.rift_chat_voice_busy
                            else -> R.string.rift_chat_voice_failed
                        },
                    )
                }
            },
        )
    }

    val inChat = !showSetup && ready
    BackHandler {
        when {
            voice.listening -> voice.stop()
            showSetup && ready -> showSetup = false
            else -> onBack()
        }
    }

    RiftTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RiftColors.Background)
                .statusBarsPadding()
                .displayCutoutPadding()
                .imePadding()
                .onPreviewKeyEvent { event ->
                    if (!inChat || event.key.nativeKeyCode != KeyEvent.KEYCODE_BUTTON_Y) return@onPreviewKeyEvent false
                    // Hold to talk: down starts, up stops and sends; the repeats while holding are swallowed.
                    when (event.type) {
                        KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) talk()
                        KeyEventType.KeyUp -> voice.stop()
                    }
                    true
                },
        ) {
          Row(modifier = Modifier.fillMaxSize()) {
            if (inChat) {
                HistoryRail(
                    history = history,
                    currentId = chat.chatId,
                    onNewChat = {
                        voice.stop()
                        AssistantChat.newChat(context)
                    },
                    onOpen = { AssistantChat.openSaved(context, it) },
                )
            }
          Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxHeight()) {
                Header(
                    subtitle = if (ready && active != null) "${connectionName(active)} · $model" else stringResource(R.string.rift_assistant_not_set),
                    onSubtitle = if (inChat && active != null) {
                        { if (connections.size > 1) switching = true else pickingModel = true }
                    } else {
                        null
                    },
                    inChat = inChat,
                    game = chat.game,
                    onBack = onBack,
                    onPickGame = { pickingGame = true },
                    onSetup = { showSetup = true },
                    onNewChat = {
                        voice.stop()
                        AssistantChat.newChat(context)
                    },
                )
                if (inChat) {
                    ChatPane(
                        chat = chat,
                        draft = if (voice.listening) voice.partial else draft,
                        listening = voice.listening,
                        voiceNote = voiceNote,
                        onDraft = { draft = it },
                        onSend = ::send,
                        onMic = { if (voice.listening) voice.stop() else talk() },
                        attached = attached,
                        onAttach = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRemoveAttached = { index -> attached = attached.filterIndexed { i, _ -> i != index } },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SetupPane(ready = ready, onDone = { showSetup = false }, modifier = Modifier.weight(1f))
                }
            }
          }
          }
        }

        if (pickingGame) {
            val games = chat.games
            RiftChoiceDialog(
                title = stringResource(R.string.rift_chat_pick_game),
                options = listOf(stringResource(R.string.rift_chat_game_none)) + games.map { it.name },
                selectedIndex = games.indexOfFirst { it.appId == chat.game?.appId } + 1,
                onSelect = { AssistantChat.pickGame(if (it == 0) null else games[it - 1]) },
                onDismiss = { pickingGame = false },
            )
        }

        if (switching && active != null) {
            RiftChoiceDialog(
                title = stringResource(R.string.rift_chat_switch),
                options = connections.map { c -> listOfNotNull(connectionName(c), settings.model(c)).joinToString(" · ") } +
                    stringResource(R.string.rift_chat_change_model, connectionName(active)),
                selectedIndex = connections.indexOfFirst { it.id == active.id },
                onSelect = {
                    if (it == connections.size) {
                        pickingModel = true
                    } else {
                        settings.activate(connections[it])
                    }
                },
                onDismiss = { switching = false },
            )
        }
        if (pickingModel && active != null) {
            AssistantModelPicker(connection = active, onDismiss = { pickingModel = false })
        }
    }
}

@Composable
private fun Header(
    subtitle: String,
    onSubtitle: (() -> Unit)?,
    inChat: Boolean,
    game: ChatGame?,
    onBack: () -> Unit,
    onPickGame: () -> Unit,
    onSetup: () -> Unit,
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 12.dp)
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RiftRoundButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, description = stringResource(R.string.back), onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.rift_settings_assistant),
                color = RiftColors.OnSurface,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            if (onSubtitle != null) {
                ModelLine(text = subtitle, onClick = onSubtitle)
            } else {
                Text(
                    text = subtitle,
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (inChat) {
            RiftChip(
                text = game?.let { stringResource(R.string.rift_chat_game, it.name) } ?: stringResource(R.string.rift_chat_game_none),
                selected = false,
                onClick = onPickGame,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            RiftRoundButton(icon = Icons.Rounded.Key, description = stringResource(R.string.rift_chat_setup), onClick = onSetup)
            RiftRoundButton(icon = Icons.Rounded.AddComment, description = stringResource(R.string.rift_chat_new), onClick = onNewChat)
        }
    }
}

/**
 * The provider and model under the title, as a button: it opens the switch between providers and the model list.
 * Filled with the accent and dark text while focused, like every Rift plate; plain grey text otherwise.
 */
@Composable
private fun ModelLine(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            // The text stays in line with the title; the plate reaches past it.
            .offset(x = (-10).dp)
            .plateFill(focused, RiftShapes.full, rest = Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = text,
            color = plateSubtext(focused),
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = plateSubtext(focused),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SetupPane(ready: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { entry.requestFocus() } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(entry)
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        if (!ready) NoteText(stringResource(R.string.rift_chat_setup_intro))
        AssistantSettings()
        if (ready) {
            RiftPillButton(text = stringResource(R.string.rift_chat_to_chat), onClick = onDone)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatPane(
    chat: ChatState,
    draft: String,
    listening: Boolean,
    voiceNote: String?,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    attached: List<String>,
    onAttach: () -> Unit,
    onRemoveAttached: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    LaunchedEffect(chat.entries.size, chat.sending) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (chat.entries.isEmpty()) {
                item(key = "empty") { NoteText(stringResource(R.string.rift_chat_empty)) }
            }
            items(chat.entries, key = { it.id }) { ChatBubble(it) }
            chat.phase?.let { phase ->
                item(key = "working") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text(
                            text = stringResource(
                                if (phase == ChatState.Phase.Collecting) R.string.rift_chat_collecting else R.string.rift_chat_thinking,
                            ),
                            color = RiftColors.OnSurfaceVariant,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        val chips = buildList {
            if (chat.game != null) {
                add(stringResource(R.string.rift_chat_chip_optimize))
                add(stringResource(R.string.rift_chat_chip_slow))
                add(stringResource(R.string.rift_chat_chip_crash))
                add(stringResource(R.string.rift_chat_chip_community))
            }
            add(stringResource(R.string.rift_chat_chip_drivers))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            chips.forEach { chip ->
                RiftChip(text = chip, selected = false, onClick = { onSend(chip) }, enabled = !chat.sending)
            }
        }
        voiceNote?.let {
            Text(
                text = it,
                color = RiftColors.Warning,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 2.dp),
            )
        }
        if (attached.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                attached.forEachIndexed { index, data ->
                    AttachedThumb(data, onRemove = { onRemoveAttached(index) })
                }
            }
        }
        Composer(
            text = draft,
            listening = listening,
            sending = chat.sending,
            field = field,
            onText = onDraft,
            onSend = { onSend(draft) },
            onMic = onMic,
            onAttach = onAttach,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    text: String,
    listening: Boolean,
    sending: Boolean,
    field: FocusRequester,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onAttach: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NoExtractOutlinedTextField(
            value = text,
            onValueChange = onText,
            readOnly = listening,
            placeholder = {
                Text(stringResource(if (listening) R.string.rift_chat_listening else R.string.rift_chat_placeholder))
            },
            maxLines = 4,
            shape = RiftShapes.sheet,
            textStyle = TextStyle(fontSize = 17.sp),
            // The page opens on this field; the keyboard comes up on A or a tap, not on focus.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, showKeyboardOnFocus = false),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            // A text field is never filled with the accent (that is for buttons): dark grey, a lighter grey while it
            // has focus, light text and the accent cursor. No outline.
            interactionSource = fieldInteraction,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = RiftAccent.current.main,
                focusedTextColor = RiftColors.OnSurface,
                unfocusedTextColor = RiftColors.OnSurface,
                focusedPlaceholderColor = RiftColors.OnSurfaceVariant,
                unfocusedPlaceholderColor = RiftColors.OnSurfaceVariant,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RiftShapes.sheet)
                .background(if (fieldFocused) FieldFocusedFill else RiftColors.SurfaceContainerHigh)
                .focusRequester(field)
                .onPreviewKeyEvent { event ->
                    // With the keyboard closed the gamepad moves on; A opens the keyboard (as in GameNative's chat).
                    if (event.type != KeyEventType.KeyDown || imeVisible) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        Key.DirectionCenter, Key.ButtonA -> {
                            keyboard?.show()
                            true
                        }
                        else -> false
                    }
                },
        )
        RiftRoundButton(
            icon = Icons.Rounded.AddPhotoAlternate,
            description = stringResource(R.string.rift_chat_attach),
            onClick = onAttach,
        )
        RiftRoundButton(
            icon = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            description = stringResource(R.string.rift_chat_voice),
            onClick = onMic,
        )
        // Rift: while the answer is being written the button stops it.
        RiftRoundButton(
            icon = if (sending) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
            description = stringResource(if (sending) R.string.rift_chat_stop else R.string.rift_chat_send),
            onClick = { if (sending) AssistantChat.stop() else onSend() },
        )
    }
}

/** One message. Focusable, so the gamepad can scroll back through the conversation. */
@Composable
private fun ChatBubble(entry: ChatEntry) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val mine = entry.fromUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!mine) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = stringResource(if (mine) R.string.rift_chat_you else R.string.rift_chat_assistant),
                color = RiftColors.OnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .plateFill(focused, RiftShapes.card, if (mine) RiftColors.SurfaceContainerHigh else RiftColors.SurfaceContainer)
                .focusable(interactionSource = interaction)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
          Column {
            if (entry.images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    entry.images.forEach { AttachedThumb(it, onRemove = null) }
                }
            }
            when {
                entry.notice -> Text(text = entry.text, color = if (focused) plateText(true) else RiftColors.Warning, fontSize = 16.sp, lineHeight = 22.sp)
                mine -> Text(text = entry.text, color = plateText(focused), fontSize = 17.sp, lineHeight = 23.sp)
                else -> MarkdownText(document = rememberMarkdown(entry.text), color = plateText(focused))
            }
          }
        }
        if (entry.truncated) Small(stringResource(R.string.rift_chat_truncated))
        if (entry.problems.isNotEmpty()) {
            Small(stringResource(R.string.rift_chat_rejected))
            entry.problems.take(3).forEach { Small("• $it") }
        }
        entry.proposal?.let { proposal ->
            Box(modifier = Modifier.widthIn(max = 720.dp)) { ProposalCard(proposal) }
        }
    }
}

@Composable
private fun Small(text: String) {
    Text(text = text, color = RiftColors.OnSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)
}

/** The message field's fill while it has focus: a lighter grey, never the accent. */
private val FieldFocusedFill = Color(0xFF333333)

/** A screenshot attached to a message: a small picture, with a cross while it is still in the composer. */
@Composable
private fun AttachedThumb(data: String, onRemove: (() -> Unit)?) {
    val bitmap = remember(data) { ChatImages.decode(data) }
    Box(modifier = Modifier.size(width = 112.dp, height = 72.dp).clip(RiftShapes.card).background(RiftColors.SurfaceContainerHigh)) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** The chats on the left: "New chat" on top, then the earlier ones, newest first. */
@Composable
private fun HistoryRail(
    history: List<SavedChat>,
    currentId: Long,
    onNewChat: () -> Unit,
    onOpen: (SavedChat) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(RiftColors.SurfaceContainer)
            .focusGroup()
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dev.rift.launcher.settings.RiftPillButton(
            text = stringResource(R.string.rift_chat_new),
            onClick = onNewChat,
            icon = Icons.Rounded.AddComment,
            primary = true,
        )
        Text(
            text = stringResource(R.string.rift_chat_history),
            color = RiftColors.OnSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(history, key = { it.id }) { saved ->
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                val current = saved.id == currentId
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .plateFill(focused, RiftShapes.card, if (current) RiftColors.SurfaceContainerHigh else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = null) { onOpen(saved) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = saved.title,
                        color = plateText(focused),
                        fontSize = 15.sp,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    saved.game?.let {
                        Text(
                            text = it.name,
                            color = if (focused) plateText(true) else RiftColors.OnSurfaceVariant,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

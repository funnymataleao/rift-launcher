package dev.rift.launcher.assistant.chat

import android.content.Context
import app.gamenative.R
import dev.rift.launcher.assistant.DeviceProfile
import dev.rift.launcher.assistant.DeviceProfiler
import dev.rift.launcher.assistant.GameContext
import dev.rift.launcher.assistant.GameContextCollector
import dev.rift.launcher.assistant.ai.AiClient
import dev.rift.launcher.assistant.ai.AiException
import dev.rift.launcher.assistant.ai.AiMessage
import dev.rift.launcher.assistant.ai.AiReply
import dev.rift.launcher.assistant.ai.AiRequest
import dev.rift.launcher.assistant.ai.AiSettings
import dev.rift.launcher.assistant.optimize.CheckedAnswer
import dev.rift.launcher.assistant.optimize.OptimizeGoal
import dev.rift.launcher.assistant.optimize.OptimizeProposal
import dev.rift.launcher.assistant.optimize.Optimizer
import dev.rift.launcher.assistant.optimize.OptimizerPrompt
import dev.rift.launcher.assistant.optimize.SuggestionCheck
import dev.rift.launcher.settings.errorText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** A game the chat can be about. */
data class ChatGame(val appId: String, val name: String)

/**
 * One message on the page. [text] is what the player sees; [wire] is what goes back to the model in later requests
 * (the whole answer, proposal block included). Notices (errors, limits) are shown but never sent.
 */
data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val wire: String = text,
    val proposal: OptimizeProposal? = null,
    /** Why a proposal in the answer was not shown, after one retry. */
    val problems: List<String> = emptyList(),
    val truncated: Boolean = false,
    val notice: Boolean = false,
    val model: String? = null,
)

data class ChatState(
    val entries: List<ChatEntry> = emptyList(),
    val game: ChatGame? = null,
    val games: List<ChatGame> = emptyList(),
    val phase: Phase? = null,
) {
    enum class Phase { Collecting, Thinking }

    val sending: Boolean get() = phase != null
}

/**
 * The conversation on the assistant page. It lives as long as the app does, so leaving the page and coming back
 * keeps it; "New chat" clears it. Requests go straight to the user's own provider with their key.
 */
object AssistantChat {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state

    private var job: Job? = null
    private var profile: DeviceProfile? = null
    /** The attached game's context, collected once per game (community configs come from the network). */
    private var gameContext: Pair<String, GameContext?>? = null
    private var nextId = 1L

    /**
     * Called when the page opens: [games] are the installed PC games to choose from, [focused] the one under the
     * cursor on the home screen. A conversation already going keeps its game.
     */
    fun open(games: List<ChatGame>, focused: ChatGame?) {
        _state.update { current ->
            val keep = current.entries.isNotEmpty() && current.game != null
            current.copy(games = games, game = if (keep) current.game else focused?.takeIf { it in games })
        }
    }

    fun pickGame(game: ChatGame?) {
        if (game?.appId != _state.value.game?.appId) gameContext = null
        _state.update { it.copy(game = game) }
    }

    fun clear() {
        job?.cancel()
        job = null
        gameContext = null
        _state.update { it.copy(entries = emptyList(), phase = null) }
    }

    fun send(context: Context, text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.sending) return
        val app = context.applicationContext
        append(ChatEntry(nextId++, fromUser = true, text = message))
        _state.update { it.copy(phase = ChatState.Phase.Collecting) }
        job = scope.launch {
            val answer = try {
                answer(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "AssistantChat: request failed")
                notice(app.getString(R.string.rift_assistant_error_other, e.javaClass.simpleName))
            }
            append(answer)
            _state.update { it.copy(phase = null) }
        }
    }

    private suspend fun answer(app: Context): ChatEntry {
        val settings = AiSettings.of(app)
        val connection = settings.active ?: return notice(app.getString(R.string.rift_chat_not_configured))
        val provider = connection.type
        val model = settings.model(connection) ?: return notice(app.getString(R.string.rift_chat_not_configured))
        if (settings.overMonthlyLimit()) return notice(app.getString(R.string.rift_optimize_limit))
        val client = try {
            settings.clientFor(connection)
        } catch (e: AiException) {
            return notice(errorText(app, provider, e))
        }

        val device = profile ?: DeviceProfiler.collect(app).also { profile = it }
        val game = _state.value.game
        val gameData = game?.let { attached ->
            gameContext?.takeIf { it.first == attached.appId }?.second
                ?: GameContextCollector.collect(app, attached.appId, device).also { gameContext = attached.appId to it }
        }
        val allowEnv = settings.allowEnvAndArgs
        val turns = _state.value.entries.filterNot { it.notice }.map {
            AiMessage(if (it.fromUser) AiMessage.Role.USER else AiMessage.Role.ASSISTANT, it.wire)
        }
        val messages = ChatPrompt.window(turns, ChatPrompt.context(device, game?.name, gameData, allowEnv))
        _state.update { it.copy(phase = ChatState.Phase.Thinking) }

        val reply = try {
            request(client, settings, model, messages)
        } catch (e: AiException) {
            return notice(errorText(app, provider, e))
        }
        val (prose, block) = ChatPrompt.splitProposal(reply.text)
        var proposal: OptimizeProposal? = null
        var problems = emptyList<String>()
        if (block != null && game != null && gameData != null) {
            var checked = SuggestionCheck.check(block, device, gameData.settings, allowEnv)
            if (checked is CheckedAnswer.Rejected) {
                // One retry with the reasons, as "Optimize" does; only the corrected block is asked for.
                val retry = messages + AiMessage(AiMessage.Role.ASSISTANT, reply.text) +
                    AiMessage(AiMessage.Role.USER, OptimizerPrompt.retryMessage(checked.problems))
                checked = try {
                    val second = request(client, settings, model, retry)
                    SuggestionCheck.check(second.text, device, gameData.settings, allowEnv)
                } catch (e: AiException) {
                    checked
                }
            }
            when (checked) {
                is CheckedAnswer.Proposal -> {
                    proposal = OptimizeProposal(
                        id = System.currentTimeMillis(),
                        appId = game.appId,
                        goal = OptimizeGoal.Balanced,
                        suggestion = checked.suggestion,
                        summary = checked.summary,
                        risk = checked.risk,
                        model = reply.model,
                    )
                    Optimizer.History.record(app, proposal)
                }
                is CheckedAnswer.Rejected -> problems = checked.problems
                is CheckedAnswer.NothingToChange -> Unit
            }
        }
        return ChatEntry(
            id = nextId++,
            fromUser = false,
            text = prose.ifBlank { proposal?.summary.orEmpty() },
            wire = reply.text.ifBlank { "…" },
            proposal = proposal,
            problems = problems,
            truncated = reply.truncated,
            model = reply.model,
        )
    }

    private suspend fun request(client: AiClient, settings: AiSettings, model: String, messages: List<AiMessage>): AiReply =
        client.complete(AiRequest(model, ChatPrompt.SYSTEM, messages, settings.maxOutputTokens)).also(settings::recordUsage)

    private fun notice(text: String) = ChatEntry(nextId++, fromUser = false, text = text, notice = true)

    private fun append(entry: ChatEntry) = _state.update { it.copy(entries = it.entries + entry) }
}

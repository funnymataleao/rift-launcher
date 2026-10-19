package dev.rift.launcher.assistant.optimize

import android.content.Context
import app.gamenative.api.SupportSuggestion
import app.gamenative.ui.screen.support.SupportSuggestionApplier
import app.gamenative.utils.ContainerUtils
import dev.rift.launcher.assistant.DeviceProfiler
import dev.rift.launcher.assistant.GameContextCollector
import dev.rift.launcher.assistant.ai.AiException
import dev.rift.launcher.assistant.ai.AiMessage
import dev.rift.launcher.assistant.ai.AiRequest
import dev.rift.launcher.assistant.ai.AiSettings
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** One suggestion the user can apply, with the id GameNative's applier keeps its rollback snapshot under. */
data class OptimizeProposal(
    val id: Long,
    val appId: String,
    val goal: OptimizeGoal,
    val suggestion: SupportSuggestion,
    val summary: String,
    val risk: String,
    val model: String,
)

sealed interface OptimizeResult {
    data class Proposed(val proposal: OptimizeProposal) : OptimizeResult
    data class NothingToChange(val summary: String) : OptimizeResult
    data object NotConfigured : OptimizeResult
    data object MonthlyLimitReached : OptimizeResult
    data object NoContainer : OptimizeResult
    data class ProviderError(val error: AiException) : OptimizeResult
    /** The model answered twice and both answers failed Rift's checks. */
    data class InvalidAnswer(val problems: List<String>) : OptimizeResult
}

/**
 * "Optimize": collects the device and the game, asks the user's AI provider for settings, checks the answer
 * (one retry with the reasons when it fails), and applies or rolls back through GameNative's own applier.
 * Nothing changes on disk until [apply] is called.
 */
object Optimizer {

    /** Marks Rift's suggestions in GameNative's applied-suggestion record; no GameNative conversation has this id. */
    const val CONVERSATION_ID = "rift-assistant"

    private const val MAX_ATTEMPTS = 2

    suspend fun propose(context: Context, appId: String, goal: OptimizeGoal, userText: String? = null): OptimizeResult {
        val settings = AiSettings.of(context)
        val connection = settings.active ?: return OptimizeResult.NotConfigured
        val model = settings.model(connection) ?: return OptimizeResult.NotConfigured
        if (settings.overMonthlyLimit()) return OptimizeResult.MonthlyLimitReached
        val client = try {
            settings.clientFor(connection)
        } catch (e: AiException) {
            return if (e.kind == AiException.Kind.NO_KEY) OptimizeResult.NotConfigured else OptimizeResult.ProviderError(e)
        }

        val profile = DeviceProfiler.collect(context)
        val game = GameContextCollector.collect(context, appId, profile) ?: return OptimizeResult.NoContainer
        val allowEnv = settings.allowEnvAndArgs
        val messages = mutableListOf(
            AiMessage(AiMessage.Role.USER, OptimizerPrompt.userMessage(goal, profile, game, allowEnv, userText)),
        )

        var problems: List<String> = emptyList()
        repeat(MAX_ATTEMPTS) {
            val reply = try {
                client.complete(AiRequest(model, OptimizerPrompt.SYSTEM, messages, settings.maxOutputTokens))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                return OptimizeResult.ProviderError(e)
            }
            settings.recordUsage(reply)
            val answer = if (reply.truncated) {
                CheckedAnswer.Rejected(listOf("The answer was cut off at the length limit; answer shorter."))
            } else {
                SuggestionCheck.check(reply.text, profile, game.settings, allowEnv)
            }
            when (answer) {
                is CheckedAnswer.Proposal -> return OptimizeResult.Proposed(
                    OptimizeProposal(System.currentTimeMillis(), appId, goal, answer.suggestion, answer.summary, answer.risk, reply.model),
                )
                is CheckedAnswer.NothingToChange -> return OptimizeResult.NothingToChange(answer.summary)
                is CheckedAnswer.Rejected -> {
                    problems = answer.problems
                    Timber.i("Optimizer: attempt rejected for $appId (chars=${reply.text.length}, truncated=${reply.truncated}): $problems")
                    messages += AiMessage(AiMessage.Role.ASSISTANT, reply.text.ifBlank { "{}" })
                    messages += AiMessage(AiMessage.Role.USER, OptimizerPrompt.retryMessage(answer.problems))
                }
            }
        }
        Timber.i("Optimizer: answer rejected twice for $appId: $problems")
        return OptimizeResult.InvalidAnswer(problems)
    }

    /** Applies a proposal; missing components are downloaded by GameNative's applier first. */
    suspend fun apply(
        context: Context,
        proposal: OptimizeProposal,
        onProgress: (Float, String) -> Unit,
    ): SupportSuggestionApplier.Result =
        SupportSuggestionApplier.apply(context, proposal.appId, proposal.id, CONVERSATION_ID, proposal.suggestion, onProgress)

    /** Puts back the settings the proposal replaced, from the snapshot GameNative took when it was applied. */
    suspend fun restore(context: Context, proposal: OptimizeProposal, onProgress: (Float, String) -> Unit): SupportSuggestionApplier.Result =
        SupportSuggestionApplier.restore(context, proposal.appId, proposal.id, CONVERSATION_ID, onProgress)

    /**
     * Rift's own list of the suggestions made for each game, in `<container>/.rift/assistant`, for a later
     * "settings history". Whether one is applied right now is GameNative's snapshot
     * ([SupportSuggestionApplier.hasSnapshot] with the same id).
     */
    object History {

        data class Entry(
            val id: Long,
            val goal: OptimizeGoal,
            val summary: String,
            val model: String,
            val changes: List<SupportSuggestion.Change>,
        )

        private fun dir(context: Context, appId: String): File? =
            if (ContainerUtils.hasContainer(context, appId)) File(ContainerUtils.getContainer(context, appId).rootDir, ".rift/assistant") else null

        suspend fun record(context: Context, proposal: OptimizeProposal) = withContext(Dispatchers.IO) {
            try {
                val dir = dir(context, proposal.appId) ?: return@withContext
                dir.mkdirs()
                File(dir, "${proposal.id}.json").writeText(toJson(proposal).toString())
            } catch (e: Exception) {
                Timber.w(e, "Optimizer: saving history failed")
            }
        }

        suspend fun list(context: Context, appId: String): List<Entry> = withContext(Dispatchers.IO) {
            try {
                dir(context, appId)?.listFiles { f -> f.name.endsWith(".json") }
                    ?.mapNotNull { runCatching { fromJson(JSONObject(it.readText())) }.getOrNull() }
                    ?.sortedByDescending { it.id }
                    .orEmpty()
            } catch (e: Exception) {
                Timber.w(e, "Optimizer: reading history failed")
                emptyList()
            }
        }

        internal fun toJson(proposal: OptimizeProposal): JSONObject = JSONObject().apply {
            put("id", proposal.id)
            put("goal", proposal.goal.id)
            put("summary", proposal.summary)
            put("model", proposal.model)
            put(
                "changes",
                JSONArray().apply {
                    proposal.suggestion.changes.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("key", c.key)
                                c.name?.let { put("name", it) }
                                c.op?.let { put("op", it) }
                                c.to?.let { put("to", it) }
                                c.why?.let { put("why", it) }
                            },
                        )
                    }
                },
            )
        }

        internal fun fromJson(json: JSONObject): Entry {
            val changes = json.optJSONArray("changes") ?: JSONArray()
            return Entry(
                id = json.getLong("id"),
                goal = OptimizeGoal.byId(json.optString("goal")),
                summary = json.optString("summary"),
                model = json.optString("model"),
                changes = List(changes.length()) { i ->
                    val c = changes.getJSONObject(i)
                    fun text(key: String) = if (c.has(key) && !c.isNull(key)) c.getString(key) else null
                    SupportSuggestion.Change(c.getString("key"), text("name"), text("op"), null, text("to"), text("why"))
                },
            )
        }
    }
}

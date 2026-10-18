package dev.rift.launcher.assistant.optimize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.api.SupportApi
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.support.LocalSupportButton
import app.gamenative.ui.screen.support.SuggestionCard
import dev.rift.launcher.assistant.ai.AiSettings
import dev.rift.launcher.settings.RiftDialog
import dev.rift.launcher.settings.RiftPillButton
import dev.rift.launcher.settings.errorText
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * "Optimize" in a game's Start menu. The menu item only asks for the dialog; [RiftOptimizeHost] on the home
 * shows it, so it stays open after the options panel closes.
 */
object RiftOptimizeRequest {

    data class Target(val appId: String, val gameName: String)

    val current = MutableStateFlow<Target?>(null)

    /** The menu item, or null when GameNative's "hide AI features" is on. */
    fun option(gameName: String, appId: String): AppMenuOption? {
        if (PrefManager.hideAiFeatures) return null
        return AppMenuOption(AppOptionMenuType.RiftOptimize, onClick = { current.value = Target(appId, gameName) })
    }
}

@Composable
fun RiftOptimizeHost() {
    val target by RiftOptimizeRequest.current.collectAsState()
    target?.let { OptimizeDialog(it, onDismiss = { RiftOptimizeRequest.current.value = null }) }
}

/**
 * Asks the user's provider for settings and shows the answer in GameNative's own suggestion card, which applies
 * it (downloading missing components) and offers the rollback. B or Close cancels a request still running.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptimizeDialog(target: RiftOptimizeRequest.Target, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var result by remember(target) { mutableStateOf<OptimizeResult?>(null) }

    LaunchedEffect(target) {
        val answer = Optimizer.propose(context, target.appId, OptimizeGoal.Balanced)
        if (answer is OptimizeResult.Proposed) Optimizer.History.record(context, answer.proposal)
        result = answer
    }

    RiftDialog(
        title = stringResource(R.string.rift_optimize_title, target.gameName),
        onDismiss = onDismiss,
        actions = { RiftPillButton(text = stringResource(R.string.rift_optimize_close), onClick = onDismiss) },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val current = result) {
                null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LoadingIndicator(color = RiftColors.Primary, modifier = Modifier.size(40.dp))
                    Body(stringResource(R.string.rift_optimize_working))
                }
                is OptimizeResult.Proposed -> {
                    val proposal = current.proposal
                    if (proposal.summary.isNotBlank()) Body(proposal.summary)
                    ProposalCard(proposal)
                }
                is OptimizeResult.NothingToChange -> {
                    Body(stringResource(R.string.rift_optimize_nothing))
                    if (current.summary.isNotBlank()) Note(current.summary)
                }
                OptimizeResult.NotConfigured -> Body(stringResource(R.string.rift_optimize_not_configured))
                OptimizeResult.MonthlyLimitReached -> Body(stringResource(R.string.rift_optimize_limit))
                OptimizeResult.NoContainer -> Body(stringResource(R.string.rift_optimize_no_container))
                is OptimizeResult.ProviderError -> {
                    val provider = remember { AiSettings.of(context).active?.type }
                    Body(provider?.let { errorText(context, it, current.error) } ?: current.error.kind.name)
                }
                is OptimizeResult.InvalidAnswer -> {
                    Body(stringResource(R.string.rift_optimize_invalid))
                    current.problems.take(3).forEach { Note(it) }
                }
            }
        }
    }
}

/**
 * A checked proposal in GameNative's own suggestion card, which applies it (downloading missing components first)
 * and offers the rollback. Used by this dialog and by the assistant page.
 */
@Composable
internal fun ProposalCard(proposal: OptimizeProposal) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note(stringResource(R.string.rift_optimize_model, proposal.model, riskLabel(proposal.risk)))
        // The card's buttons as Rift's pills: grey, or filled with the accent while focused; no ring, no outline.
        CompositionLocalProvider(LocalSupportButton provides RiftSupportButton) {
            SuggestionCard(
                message = SupportApi.Message(
                    id = proposal.id,
                    kind = "assistant",
                    text = proposal.summary,
                    createdAt = proposal.id,
                    authorName = null,
                    isReport = false,
                    attachments = emptyList(),
                    notice = null,
                    suggestion = proposal.suggestion,
                ),
                suggestion = proposal.suggestion,
                appId = proposal.appId,
                conversationId = Optimizer.CONVERSATION_ID,
                // No test runs are offered (rerun is always false), so this is never called.
                onStartDebugRun = {},
            )
        }
    }
}

private val RiftSupportButton: @Composable (String, Boolean, () -> Unit, Modifier) -> Unit = { text, enabled, onClick, modifier ->
    RiftPillButton(
        text = text,
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
    )
}

@Composable
private fun riskLabel(risk: String): String = when (risk) {
    "low" -> stringResource(R.string.rift_optimize_risk_low)
    "high" -> stringResource(R.string.rift_optimize_risk_high)
    else -> stringResource(R.string.rift_optimize_risk_medium)
}

@Composable
private fun Body(text: String) {
    Text(text = text, color = RiftColors.OnSurface, fontSize = 16.sp, lineHeight = 22.sp)
}

@Composable
private fun Note(text: String) {
    Text(text = text, color = RiftColors.OnSurfaceVariant, fontSize = 13.5.sp, lineHeight = 19.sp)
}

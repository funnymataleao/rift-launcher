// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.assistant

import dev.rift.model.GameId

/**
 * The optional AI assistant (stage 5). Bring your own key: the user picks the provider and
 * pays for it, the feature is off until they set one up.
 */
interface AssistantProvider {
    /** e.g. `openai`, `anthropic`, `openrouter`, or a custom OpenAI-compatible endpoint. */
    val id: String

    suspend fun complete(systemPrompt: String, userPrompt: String): String
}

/**
 * A suggested config change for one game. It is applied to a single launch first and kept only
 * when the user says it helped, which is the rollback.
 */
data class OptimizationProposal(
    val gameId: GameId,
    /** What changes and why, shown to the user before applying. */
    val explanation: String,
    /** Settings to override for one launch, e.g. GameNative's `container_config` keys. */
    val overrides: Map<String, String>,
)

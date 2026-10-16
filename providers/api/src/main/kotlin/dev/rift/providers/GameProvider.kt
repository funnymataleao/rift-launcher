// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers

import dev.rift.model.LaunchSource
import dev.rift.model.LaunchTarget
import dev.rift.model.ProvidedGame
import kotlinx.coroutines.flow.StateFlow

/**
 * A source of games and the code that launches them: installed Android games, PC streaming
 * through Artemis, GameNative, emulators, cloud services.
 *
 * Each provider lives in its own `:providers:*` module and contributes itself to the app with a
 * Hilt `@Binds @IntoSet` binding, so the library and UI never depend on a concrete provider.
 */
interface GameProvider {
    /** Stable id, stored in every [LaunchTarget.providerId] this provider creates. */
    val id: String

    /** The sources this provider can create targets for. */
    val sources: Set<LaunchSource>

    /** Whether the provider is set up. The home screen shows a provider's shelf only when [ProviderStatus.Ready]. */
    val status: StateFlow<ProviderStatus>

    /** Returns the provider's current games. Called on library refresh; may do I/O. */
    suspend fun games(): List<ProvidedGame>

    /** Starts [target], which this provider created. */
    suspend fun launch(target: LaunchTarget): LaunchResult
}

sealed interface ProviderStatus {
    /** Not connected yet: no paired PC, no store login, no emulator chosen. */
    data object NotConfigured : ProviderStatus

    data object Ready : ProviderStatus

    /** Set up but unusable right now, e.g. the PC host is offline. */
    data class Unavailable(val reason: String) : ProviderStatus
}

sealed interface LaunchResult {
    data object Started : LaunchResult

    /** The user has to finish setup first (pair the PC, sign in, install the app). */
    data class NeedsSetup(val reason: String) : LaunchResult

    data class Failed(val message: String, val cause: Throwable? = null) : LaunchResult
}

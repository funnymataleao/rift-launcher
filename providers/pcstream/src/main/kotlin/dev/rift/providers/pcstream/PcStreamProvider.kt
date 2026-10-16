// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers.pcstream

import dev.rift.model.LaunchSource
import dev.rift.model.LaunchTarget
import dev.rift.model.ProvidedGame
import dev.rift.providers.GameProvider
import dev.rift.providers.LaunchResult
import dev.rift.providers.ProviderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Games streamed from the user's PC through the embedded Artemis core: pairing, host apps from the Apollo/Sunshine API, one-tap start of a Steam game on the PC.
 *
 * Connection point only: task 0.3 (Artemis spike), then 1.3 and 1.4 replaces this stub with the real integration.
 * Until then the provider reports [ProviderStatus.NotConfigured], so the home screen hides its shelf.
 */
@Singleton
class PcStreamProvider @Inject constructor() : GameProvider {

    override val id = ID
    override val sources = setOf(LaunchSource.PC_STREAM)
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.NotConfigured).asStateFlow()

    override suspend fun games(): List<ProvidedGame> = emptyList()

    override suspend fun launch(target: LaunchTarget): LaunchResult = LaunchResult.NeedsSetup(NOT_CONNECTED)

    companion object {
        const val ID = "pcstream"
        private const val NOT_CONNECTED = "pcstream is not connected yet"
    }
}

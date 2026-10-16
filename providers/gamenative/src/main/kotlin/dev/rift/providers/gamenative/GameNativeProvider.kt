// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.providers.gamenative

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
 * Windows games run on this device by the forked GameNative engine: Steam, Epic, GOG and Amazon libraries, downloads, containers.
 *
 * Connection point only: task 0.4 (GameNative spike), then 1.8 to 1.10 replaces this stub with the real integration.
 * Until then the provider reports [ProviderStatus.NotConfigured], so the home screen hides its shelf.
 */
@Singleton
class GameNativeProvider @Inject constructor() : GameProvider {

    override val id = ID
    override val sources = setOf(LaunchSource.GAMENATIVE)
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.NotConfigured).asStateFlow()

    override suspend fun games(): List<ProvidedGame> = emptyList()

    override suspend fun launch(target: LaunchTarget): LaunchResult = LaunchResult.NeedsSetup(NOT_CONNECTED)

    companion object {
        const val ID = "gamenative"
        private const val NOT_CONNECTED = "gamenative is not connected yet"
    }
}

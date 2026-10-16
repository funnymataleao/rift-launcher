// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library

import dev.rift.library.db.LibraryDao
import dev.rift.model.Game
import dev.rift.model.LaunchTarget
import dev.rift.model.LibraryMerger
import dev.rift.model.ProvidedGame
import dev.rift.providers.GameProvider
import dev.rift.providers.LaunchResult
import dev.rift.providers.ProviderStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The unified library: every provider's games merged into one list, cached in Room. */
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    providers: Set<@JvmSuppressWildcards GameProvider>,
) {
    private val providers = providers.associateBy { it.id }

    val games: Flow<List<Game>> = dao.observeGames().map { rows -> rows.map { it.toGame() } }

    /** Asks every ready provider for its games and stores the merged result. */
    suspend fun refresh() {
        val results: List<Pair<String, List<ProvidedGame>>> = coroutineScope {
            providers.values
                .filter { it.status.value == ProviderStatus.Ready }
                .map { provider -> async { provider.id to provider.gamesOrNull() } }
                .awaitAll()
                .mapNotNull { (id, games) -> games?.let { id to it } }
        }
        val merged = LibraryMerger.merge(results.flatMap { it.second })
        dao.applyRefresh(
            refreshedProviders = results.mapTo(mutableSetOf()) { it.first },
            games = merged.map { it.toEntity() },
            targets = merged.flatMap { game -> game.targets.map { it.toEntity(game.id) } },
        )
    }

    suspend fun launch(game: Game, target: LaunchTarget? = game.defaultTarget): LaunchResult {
        if (target == null) return LaunchResult.NeedsSetup("No source can start ${game.title}")
        val provider = providers[target.providerId]
            ?: return LaunchResult.Failed("Provider ${target.providerId} is not installed")
        val result = provider.launch(target)
        if (result == LaunchResult.Started) dao.markPlayed(game.id.value, System.currentTimeMillis())
        return result
    }

    /** Remembers the source picked in the Play menu for this game. */
    suspend fun choose(game: Game, target: LaunchTarget) = dao.setPreferredTarget(game.id.value, target.id)

    suspend fun setFavorite(game: Game, favorite: Boolean) = dao.setFavorite(game.id.value, favorite)

    private suspend fun GameProvider.gamesOrNull(): List<ProvidedGame>? = try {
        games()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

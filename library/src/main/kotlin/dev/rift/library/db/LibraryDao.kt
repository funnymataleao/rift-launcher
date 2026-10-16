// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {

    @Transaction
    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    abstract fun observeGames(): Flow<List<GameWithTargets>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertGamesIfAbsent(games: List<GameEntity>)

    @Update(entity = GameEntity::class)
    abstract suspend fun updateMetadata(updates: List<GameMetadataUpdate>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTargets(targets: List<LaunchTargetEntity>)

    @Query("DELETE FROM launch_targets WHERE providerId IN (:providerIds)")
    abstract suspend fun deleteTargetsOf(providerIds: Collection<String>)

    @Query("DELETE FROM games WHERE id NOT IN (SELECT DISTINCT gameId FROM launch_targets)")
    abstract suspend fun deleteGamesWithoutTargets()

    @Query("UPDATE games SET isFavorite = :favorite WHERE id = :gameId")
    abstract suspend fun setFavorite(gameId: String, favorite: Boolean)

    @Query("UPDATE games SET preferredTargetId = :targetId WHERE id = :gameId")
    abstract suspend fun setPreferredTarget(gameId: String, targetId: String?)

    @Query("UPDATE games SET lastPlayedAt = :timestamp WHERE id = :gameId")
    abstract suspend fun markPlayed(gameId: String, timestamp: Long)

    /**
     * Replaces the targets of the providers that refreshed successfully. Providers that failed
     * keep their previous targets, so a PC that is offline does not empty the library.
     */
    @Transaction
    open suspend fun applyRefresh(
        refreshedProviders: Set<String>,
        games: List<GameEntity>,
        targets: List<LaunchTargetEntity>,
    ) {
        insertGamesIfAbsent(games)
        updateMetadata(games.map { GameMetadataUpdate(it.id, it.title, it.coverUrl, it.heroUrl, it.logoUrl, it.iconUrl) })
        deleteTargetsOf(refreshedProviders)
        upsertTargets(targets)
        deleteGamesWithoutTargets()
    }
}

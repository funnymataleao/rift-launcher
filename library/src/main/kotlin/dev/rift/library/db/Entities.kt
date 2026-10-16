// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A game in the unified library. Metadata columns are refreshed from providers, user columns are not. */
@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val coverUrl: String?,
    val heroUrl: String?,
    val logoUrl: String?,
    val iconUrl: String?,
    // User state, kept across refreshes.
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val preferredTargetId: String? = null,
)

@Entity(
    tableName = "launch_targets",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameId"), Index("providerId")],
)
data class LaunchTargetEntity(
    /** `providerId:externalId`, see [dev.rift.model.LaunchTarget.id]. */
    @PrimaryKey val id: String,
    val gameId: String,
    val providerId: String,
    val source: String,
    val externalId: String,
    val label: String,
    val isAvailable: Boolean,
    val extrasJson: String,
)

data class GameWithTargets(
    @Embedded val game: GameEntity,
    @Relation(parentColumn = "id", entityColumn = "gameId")
    val targets: List<LaunchTargetEntity>,
)

/** Metadata-only projection, so a refresh never overwrites user columns. */
data class GameMetadataUpdate(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val heroUrl: String?,
    val logoUrl: String?,
    val iconUrl: String?,
)

// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.library

import dev.rift.library.db.GameEntity
import dev.rift.library.db.GameWithTargets
import dev.rift.library.db.LaunchTargetEntity
import dev.rift.model.Artwork
import dev.rift.model.Game
import dev.rift.model.GameId
import dev.rift.model.LaunchSource
import dev.rift.model.LaunchTarget
import dev.rift.model.MergedGame
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val extrasSerializer = MapSerializer(String.serializer(), String.serializer())

internal fun GameWithTargets.toGame(): Game = Game(
    id = GameId(game.id),
    title = game.title,
    // A source that a newer app version removed is dropped instead of crashing the library.
    targets = targets.mapNotNull { it.toLaunchTarget() },
    artwork = Artwork(game.coverUrl, game.heroUrl, game.logoUrl, game.iconUrl),
    preferredTargetId = game.preferredTargetId,
    isFavorite = game.isFavorite,
    lastPlayedAt = game.lastPlayedAt,
)

private fun LaunchTargetEntity.toLaunchTarget(): LaunchTarget? {
    val launchSource = LaunchSource.entries.firstOrNull { it.name == source } ?: return null
    return LaunchTarget(
        providerId = providerId,
        source = launchSource,
        externalId = externalId,
        label = label,
        isAvailable = isAvailable,
        extras = json.decodeFromString(extrasSerializer, extrasJson),
    )
}

internal fun MergedGame.toEntity() = GameEntity(
    id = id.value,
    title = title,
    coverUrl = artwork.cover,
    heroUrl = artwork.hero,
    logoUrl = artwork.logo,
    iconUrl = artwork.icon,
)

internal fun LaunchTarget.toEntity(gameId: GameId) = LaunchTargetEntity(
    id = id,
    gameId = gameId.value,
    providerId = providerId,
    source = source.name,
    externalId = externalId,
    label = label,
    isAvailable = isAvailable,
    extrasJson = json.encodeToString(extrasSerializer, extras),
)

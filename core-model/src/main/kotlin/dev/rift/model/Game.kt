// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.model

/** Stable id of a game in the unified library. See [LibraryMerger] for how it is derived. */
@JvmInline
value class GameId(val value: String)

data class Artwork(
    val cover: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val icon: String? = null,
) {
    /** Fills the fields this artwork lacks from [other]. */
    fun orElse(other: Artwork): Artwork = Artwork(
        cover = cover ?: other.cover,
        hero = hero ?: other.hero,
        logo = logo ?: other.logo,
        icon = icon ?: other.icon,
    )
}

/**
 * One way to start a game: a source plus whatever the owning provider needs to launch it.
 * A [Game] has one or more of them, which is what the Play button's source menu lists.
 */
data class LaunchTarget(
    /** The provider that created this target and knows how to launch it, e.g. `"android"`. */
    val providerId: String,
    val source: LaunchSource,
    /** Provider-specific id: package name, Steam app id, host app UUID, ROM path. */
    val externalId: String,
    /** Short line under the Play button, e.g. "on PC BEAST" or "Steam · this device". */
    val label: String,
    val isAvailable: Boolean = true,
    /** Extra provider data (host id, emulator package, container config). */
    val extras: Map<String, String> = emptyMap(),
) {
    val id: String get() = "$providerId:$externalId"
}

data class Game(
    val id: GameId,
    val title: String,
    val targets: List<LaunchTarget>,
    val artwork: Artwork = Artwork(),
    /** The target the user picked last time in the Play menu, remembered per game. */
    val preferredTargetId: String? = null,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
) {
    /** What Play starts: the remembered choice if still available, else the highest-priority source. */
    val defaultTarget: LaunchTarget?
        get() = targets.firstOrNull { it.id == preferredTargetId && it.isAvailable }
            ?: targets.filter { it.isAvailable }.minByOrNull { it.source.priority }
}

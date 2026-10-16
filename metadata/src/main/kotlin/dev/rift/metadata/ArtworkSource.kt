// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.metadata

import dev.rift.model.Artwork

/**
 * Finds cover, hero, logo and icon art for a game. Implementations (SteamGridDB with the user's
 * key, libretro thumbnails for ROMs) and the image cache come with task 1.5.
 */
interface ArtworkSource {
    val id: String

    /** @param identities the game's cross-source ids, e.g. `steam:1091500`, which give exact matches. */
    suspend fun find(title: String, identities: Set<String>): Artwork?
}

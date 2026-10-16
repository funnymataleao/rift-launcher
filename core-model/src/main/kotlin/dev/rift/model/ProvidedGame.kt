// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.model

/**
 * A game as one provider sees it. The library merges entries from all providers into [Game]s.
 *
 * @property identities strong cross-source ids in the form `"<namespace>:<id>"`, e.g. `"steam:1091500"`.
 * Two entries sharing any identity are the same game. Entries without identities are matched by title.
 */
data class ProvidedGame(
    val title: String,
    val target: LaunchTarget,
    val identities: Set<String> = emptySet(),
    val artwork: Artwork = Artwork(),
)

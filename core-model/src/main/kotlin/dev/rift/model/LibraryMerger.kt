// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.model

import java.text.Normalizer

data class MergedGame(
    val id: GameId,
    val title: String,
    val artwork: Artwork,
    val targets: List<LaunchTarget>,
)

/**
 * Merges what every provider reports into one entry per game, so a game owned on Steam,
 * set up on the PC host and installed in GameNative shows once with three launch targets.
 *
 * Ids are deterministic: the smallest identity of the group, or `"title:<normalized title>"`,
 * so user state (favorites, chosen source) survives refreshes.
 */
object LibraryMerger {

    fun merge(entries: List<ProvidedGame>): List<MergedGame> {
        val groups = groupByIdentity(entries.filter { it.identities.isNotEmpty() })
        val byTitle = groups.associateByTo(mutableMapOf()) { titleKey(it.first().title) }
        for (entry in entries.filter { it.identities.isEmpty() }) {
            byTitle.getOrPut(titleKey(entry.title)) { mutableListOf<ProvidedGame>().also(groups::add) }
                .add(entry)
        }
        return groups.map(::toMergedGame).sortedBy { it.title.lowercase() }
    }

    fun titleKey(title: String): String {
        val ascii = Normalizer.normalize(title, Normalizer.Form.NFKD).replace(DIACRITICS, "")
        return "title:" + ascii.lowercase().replace(NON_ALNUM, "")
    }

    private fun groupByIdentity(entries: List<ProvidedGame>): MutableList<MutableList<ProvidedGame>> {
        val parent = IntArray(entries.size) { it }
        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) root = parent[root]
            parent[i] = root
            return root
        }
        val firstWithIdentity = mutableMapOf<String, Int>()
        entries.forEachIndexed { index, entry ->
            entry.identities.forEach { identity ->
                val other = firstWithIdentity.putIfAbsent(identity, index)
                if (other != null) parent[find(index)] = find(other)
            }
        }
        return entries.indices.groupBy(::find).values
            .mapTo(mutableListOf()) { indices -> indices.mapTo(mutableListOf()) { entries[it] } }
    }

    private fun toMergedGame(group: List<ProvidedGame>): MergedGame {
        val ordered = group.sortedBy { it.target.source.priority }
        val id = group.flatMap { it.identities }.minOrNull() ?: titleKey(ordered.first().title)
        return MergedGame(
            id = GameId(id),
            // Store-backed entries carry the canonical title; host app names and file names are rougher.
            title = (ordered.firstOrNull { it.identities.isNotEmpty() } ?: ordered.first()).title,
            artwork = ordered.map { it.artwork }.fold(Artwork()) { acc, art -> acc.orElse(art) },
            targets = ordered.map { it.target }.distinctBy { it.id },
        )
    }

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
}

// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryMergerTest {

    private fun entry(
        title: String,
        provider: String,
        source: LaunchSource,
        externalId: String = title,
        identities: Set<String> = emptySet(),
    ) = ProvidedGame(
        title = title,
        target = LaunchTarget(provider, source, externalId, label = provider),
        identities = identities,
    )

    @Test
    fun `entries sharing an identity become one game`() {
        val games = LibraryMerger.merge(
            listOf(
                entry("Cyberpunk 2077", "gamenative", LaunchSource.GAMENATIVE, "1091500", setOf("steam:1091500")),
                entry("Cyberpunk 2077", "pcstream", LaunchSource.PC_STREAM, "uuid-1", setOf("steam:1091500")),
            ),
        )
        assertEquals(1, games.size)
        assertEquals(GameId("steam:1091500"), games.single().id)
        assertEquals(listOf(LaunchSource.PC_STREAM, LaunchSource.GAMENATIVE), games.single().targets.map { it.source })
    }

    @Test
    fun `entries without identities join a game with the same normalized title`() {
        val games = LibraryMerger.merge(
            listOf(
                entry("Cyberpunk 2077", "gamenative", LaunchSource.GAMENATIVE, "1091500", setOf("steam:1091500")),
                entry("CYBERPUNK  2077", "pcstream", LaunchSource.PC_STREAM),
                entry("Pokémon Emerald", "emulators", LaunchSource.EMULATOR),
            ),
        )
        assertEquals(listOf("Cyberpunk 2077", "Pokémon Emerald"), games.map { it.title })
        assertEquals(2, games.first().targets.size)
        assertEquals(GameId("title:pokemonemerald"), games.last().id)
    }

    @Test
    fun `identities chain transitively`() {
        val games = LibraryMerger.merge(
            listOf(
                entry("A", "p1", LaunchSource.NATIVE, identities = setOf("steam:1")),
                entry("A", "p2", LaunchSource.CLOUD_XCLOUD, identities = setOf("steam:1", "xbox:9")),
                entry("A", "p3", LaunchSource.CLOUD_GFN, identities = setOf("xbox:9")),
            ),
        )
        assertEquals(1, games.size)
        assertEquals(3, games.single().targets.size)
    }

    @Test
    fun `default target prefers the remembered choice and falls back to priority`() {
        val pc = LaunchTarget("pcstream", LaunchSource.PC_STREAM, "1", "pc")
        val device = LaunchTarget("gamenative", LaunchSource.GAMENATIVE, "1", "device")
        val game = Game(GameId("g"), "G", targets = listOf(device, pc))
        assertEquals(pc, game.defaultTarget)
        assertEquals(device, game.copy(preferredTargetId = device.id).defaultTarget)
        assertEquals(device, game.copy(targets = listOf(device, pc.copy(isAvailable = false))).defaultTarget)
    }
}

package dev.rift.launcher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorCatalogTest {
    @Test
    fun catalogInitialises() {
        // A missing core list used to throw while the catalog was built and took the whole app down.
        assertTrue(EmulatorCatalog.platforms.size > 40)
    }

    @Test
    fun systemIdsAreUnique() {
        val ids = EmulatorCatalog.platforms.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everySystemHasEmulatorsAndExtensions() {
        EmulatorCatalog.platforms.forEach {
            assertTrue("${it.id} has no emulators", it.emulators.isNotEmpty())
            assertTrue("${it.id} has no file types", it.extensions.isNotEmpty())
        }
    }

    @Test
    fun systemsPlayedByRetroArchHaveCores() {
        EmulatorCatalog.platforms.filter { p -> p.emulators.any { it.kind == LaunchKind.RETROARCH } }.forEach {
            assertTrue("${it.id} has no cores", it.cores.isNotEmpty())
        }
    }
}

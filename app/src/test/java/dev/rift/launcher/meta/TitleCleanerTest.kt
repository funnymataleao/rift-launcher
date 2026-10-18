package dev.rift.launcher.meta

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanerTest {
    @Test
    fun `dotted dump names become words`() {
        assertEquals("Need for Speed Underground 2", TitleCleaner.clean("Need.for.Speed.Underground.2.PS2.2004"))
    }

    @Test
    fun `disc codes regions and trailing articles go`() {
        assertEquals("Gran Turismo 3 - A-Spec", TitleCleaner.clean("Gran Turismo 3 - A-Spec SLUS 213.51."))
        assertEquals("The Terminator", TitleCleaner.clean("Terminator, The (USA) [!]"))
        assertEquals("Sonic 2", TitleCleaner.clean("Sonic 2 (Europe).md"))
    }

    @Test
    fun `real titles with numbers are kept`() {
        assertEquals("Need for Speed 2015", TitleCleaner.clean("Need for Speed 2015"))
        assertEquals("Metroid Prime Remastered", TitleCleaner.clean("Metroid Prime Remastered"))
    }
}

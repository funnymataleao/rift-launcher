package dev.rift.launcher.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamClientAutoModeTest {
    @Test fun picksInstalledExactBuild() {
        assertEquals("proton-10.0-4-x86_64-1", SteamClientAutoMode.selectX64Wine(
            "proton-10.0-4-arm64ec-1",
            listOf("proton-9.0-x86_64", "proton-10.0-4-x86_64-1"),
        ))
    }

    @Test fun suffixedArm64ecFallsBackToInstalledProton9() {
        assertEquals("proton-9.0-x86_64", SteamClientAutoMode.selectX64Wine(
            "proton-10.0-arm64ec-2",
            listOf("proton-9.0-x86_64", "proton-10.0-arm64ec"),
        ))
    }

    @Test fun choosesSameMajorOverOlderAvailableBuild() {
        assertEquals("proton-10.0-4-x86_64-1", SteamClientAutoMode.selectX64Wine(
            "proton-10.0-arm64ec-2",
            listOf("proton-9.0-x86_64", "proton-10.0-4-x86_64-1"),
        ))
    }

    @Test fun doesNotInventOrSwitchMissingWine() {
        assertNull(SteamClientAutoMode.selectX64Wine("proton-10.0-arm64ec-2", listOf("proton-10.0-arm64ec")))
        assertNull(SteamClientAutoMode.selectX64Wine("proton-9.0-x86_64", listOf("proton-9.0-x86_64")))
    }
}

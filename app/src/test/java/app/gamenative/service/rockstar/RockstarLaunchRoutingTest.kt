package app.gamenative.service.rockstar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RockstarLaunchRoutingTest {
    @Test
    fun emulatedSteamRequiresRockstarPreflightEvenIfSteamTypeIsHeadless() {
        assertTrue(RockstarLaunchSupport.requiresPublisherPreflight(
            launchRealSteam = false, launchHeadlessSteam = false, launchBionicSteam = false,
        ))
    }

    @Test
    fun realSteamClientHandlesOwnRockstarLauncher() {
        assertFalse(RockstarLaunchSupport.requiresPublisherPreflight(
            launchRealSteam = true, launchHeadlessSteam = false, launchBionicSteam = false,
        ))
    }

    @Test
    fun headlessAndBionicRequirePreflight() {
        assertTrue(RockstarLaunchSupport.requiresPublisherPreflight(true, true, false))
        assertTrue(RockstarLaunchSupport.requiresPublisherPreflight(false, false, true))
    }
}

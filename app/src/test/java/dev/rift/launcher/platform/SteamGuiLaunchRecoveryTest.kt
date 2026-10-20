package dev.rift.launcher.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGuiLaunchRecoveryTest {
    @Test fun requiresCurrentGameCloudSyncAndWait() {
        val cloud = "[2026-10-20] [AppID 990080] Successfully synced to ChangeNumber 29"
        assertFalse(SteamGuiLaunchRecovery.retryEligible(5_000, cloud, "", 990080))
        assertTrue(SteamGuiLaunchRecovery.retryEligible(23_000, cloud, "", 990080))
        assertFalse(SteamGuiLaunchRecovery.retryEligible(23_000, cloud, "", 241100))
    }

    @Test fun neverSendsAnotherRequestIfSteamAlreadyStartedGame() {
        val cloud = "[AppID 990080] Successfully synced to ChangeNumber 29"
        val running = "[2026-10-20] Game 990080 adding PID 1256 as a tracked process"
        assertFalse(SteamGuiLaunchRecovery.retryEligible(30_000, cloud, running, 990080))
    }

    @Test fun usesOfficialSteamClientRatherThanGameExecutable() {
        assertTrue(SteamGuiLaunchRecovery.steamLaunchCommand(990080).contains("steam.exe' -applaunch 990080"))
        assertFalse(SteamGuiLaunchRecovery.steamLaunchCommand(990080).contains("HogwartsLegacy.exe"))
    }

    @Test fun distinguishesSteamControllerCloudConflictFromGameSync() {
        val log = "[2026-10-20] [AppID 241100] Sync conflict for file prefs.vdf"
        assertTrue(SteamGuiLaunchRecovery.controllerCloudConflict(log))
        assertFalse(SteamGuiLaunchRecovery.controllerCloudConflict("[AppID 990080] Successfully synced"))
    }

    @Test fun doesNotTreatSteamControllerSyncAsGameSync() {
        val conflict = "[AppID 241100] AutoCloud done. Watching 1 files"
        assertFalse(SteamGuiLaunchRecovery.cloudReady(conflict, 990080))
        assertFalse(SteamGuiLaunchRecovery.retryEligible(90_000, conflict, "", 990080))
    }
}

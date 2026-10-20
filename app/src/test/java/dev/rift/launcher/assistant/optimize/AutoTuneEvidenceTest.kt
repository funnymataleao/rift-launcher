package dev.rift.launcher.assistant.optimize

import dev.rift.launcher.assistant.RunRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTuneEvidenceTest {
    private fun run(
        id: Long,
        driver: String,
        fps: Double?,
        p99: Double? = 25.0,
        seconds: Long = 300,
        exit: String = "quick_menu",
        thermal: Int = 0,
    ) = RunRecord(
        endedAt = id,
        sessionSeconds = seconds,
        avgFps = fps,
        mainWindowClass = "samplegame.exe",
        mainWindowActiveSeconds = seconds,
        frameP99Ms = p99,
        exitReason = exit,
        thermalPeak = thermal,
        settings = mapOf("graphicsDriverVersion" to driver, "wineVersion" to "proton-9.0-x86_64"),
    )

    @Test fun noWinnerFromOneRun() {
        val evidence = AutoTuneAnalyzer.analyze(listOf(run(1, "v1", 80.0)), mapOf("graphicsDriverVersion" to "v1"))
        assertNull(evidence.recommendedFromHistory)
        assertEquals("preliminary", evidence.profiles.first().readiness)
    }

    @Test fun skipsShortAndThermalRunsForBenchmark() {
        val evidence = AutoTuneAnalyzer.analyze(
            listOf(run(1, "v1", 120.0, seconds = 45), run(2, "v1", 90.0, thermal = 3)),
            mapOf("graphicsDriverVersion" to "v1"),
        )
        assertNull(evidence.recommendedFromHistory)
        assertEquals(0, evidence.profiles.first().measuredRuns)
        assertEquals(1, evidence.profiles.first().thermallyLimitedRuns)
    }

    @Test fun stableRepeatableProfileBeatsOneFastButCrashingRun() {
        val runs = listOf(
            run(1, "safe", 44.0, p99 = 33.0),
            run(2, "safe", 46.0, p99 = 31.0),
            run(3, "fast", 90.0),
            run(4, "fast", null, seconds = 12, exit = "guest_terminated"),
        )
        val evidence = AutoTuneAnalyzer.analyze(runs, mapOf("graphicsDriverVersion" to "fast"))
        val best = evidence.profiles[evidence.recommendedFromHistory!!]
        assertEquals("safe", best.settings["graphicsDriverVersion"])
        assertEquals("repeatable", best.readiness)
        assertTrue(evidence.profiles.first().isCurrent)
        assertEquals(1, evidence.profiles.first().earlyExits)
    }

    @Test fun preferBetterFramePacingWhenAverageFpsSimilar() {
        val runs = listOf(
            run(1, "jittery", 60.0, 90.0),
            run(2, "jittery", 61.0, 95.0),
            run(3, "steady", 56.0, 26.0),
            run(4, "steady", 58.0, 28.0),
        )
        val evidence = AutoTuneAnalyzer.analyze(runs, mapOf("graphicsDriverVersion" to "steady"))
        assertEquals("steady", evidence.profiles[evidence.recommendedFromHistory!!].settings["graphicsDriverVersion"])
    }

    @Test fun steamWindowFpsIsNotGameplayEvidence() {
        val launcher = run(1, "broken", 0.3).copy(mainWindowClass = "steam.exe")
        val explorer = run(2, "broken", 18.0).copy(mainWindowClass = "explorer.exe")
        val data = AutoTuneAnalyzer.analyze(listOf(launcher, explorer), mapOf("graphicsDriverVersion" to "broken"))
        assertEquals("insufficient", data.profiles.first().readiness)
        assertEquals(0, data.profiles.first().measuredRuns)
        assertNull(data.recommendedFromHistory)
    }

    @Test fun doesNotBenchmarkIfGameWindowWasNeverFocused() {
        val silent = run(1, "broken", 80.0).copy(mainWindowActiveSeconds = 0)
        val data = AutoTuneAnalyzer.analyze(listOf(silent), emptyMap())
        assertNull(data.profiles.first().medianFps)
    }

    @Test fun noHistoryProducesNoWinner() {
        val evidence = AutoTuneAnalyzer.analyze(emptyList(), emptyMap())
        assertTrue(evidence.profiles.isEmpty())
        assertNull(evidence.recommendedFromHistory)
    }
}

package dev.rift.launcher.assistant

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunHistoryTest {

    private fun runJson(fps: Double, seconds: Long, driver: String, exit: String = "quick_menu", thermal: Int = 0): String =
        JSONObject()
            .put("avg_fps", fps)
            .put("session_length", seconds)
            .put("frame_p99_ms", 1000.0 / fps * 2)
            .put("fps_by_5min", org.json.JSONArray(listOf(fps.toInt(), fps.toInt())))
            .put("thermal_peak", thermal)
            .put("time_to_throttle_s", if (thermal >= 2) 300 else JSONObject.NULL)
            .put("exit_reason", exit)
            .put(
                "container_config",
                JSONObject()
                    .put("graphicsDriver", "turnip")
                    .put("graphicsDriverVersion", driver)
                    .put("dxwrapper", "dxvk")
                    .put("executablePath", "game.exe")
                    .put("screenSize", "1280x720")
                    .toString(),
            )
            .toString()

    @Test
    fun parsesGameNativeRunRecord() {
        val run = RunHistory.parse("1700000000000.json", runJson(58.5, 900, "26.0.0", thermal = 2))!!
        assertEquals(1_700_000_000_000L, run.endedAt)
        assertEquals(58.5, run.avgFps!!, 0.001)
        assertEquals(900L, run.sessionSeconds)
        assertEquals(listOf(58, 58), run.fpsBy5Min)
        assertEquals(300L, run.timeToThrottleSeconds)
        assertEquals("26.0.0", run.settings["graphicsDriverVersion"])
        assertNull("only settings a suggestion may change are kept", run.settings["screenSize"])
    }

    @Test
    fun skipsFilesThatAreNotRunRecords() {
        assertNull(RunHistory.parse("notes.json", runJson(60.0, 600, "26.0.0")))
    }

    @Test
    fun comparesLatestSettingsWithThePreviousOnes() {
        val runs = listOf(
            RunHistory.parse("1.json", runJson(40.0, 600, "25.1.0")),
            RunHistory.parse("2.json", runJson(44.0, 600, "25.1.0")),
            RunHistory.parse("3.json", runJson(60.0, 600, "26.0.0")),
            RunHistory.parse("4.json", runJson(5.0, 20, "26.0.0", exit = "guest_terminated")),
        ).map { it!! }
        val comparison = RunHistory.compareLatest(runs)!!
        assertEquals(2, comparison.before.runs)
        assertEquals(42.0, comparison.before.medianFps!!, 0.001)
        assertEquals("short sessions don't count for FPS", 60.0, comparison.after.medianFps!!, 0.001)
        assertEquals(1, comparison.after.earlyExits)
        assertEquals(42.9, comparison.fpsChangePercent!!, 0.001)
        assertEquals(listOf(GameSettings.Change("graphicsDriverVersion", "25.1.0", "26.0.0")), comparison.changes)
    }

    @Test
    fun noComparisonWhileSettingsNeverChanged() {
        val runs = listOf(
            RunHistory.parse("1.json", runJson(40.0, 600, "25.1.0"))!!,
            RunHistory.parse("2.json", runJson(41.0, 600, "25.1.0"))!!,
        )
        assertNull(RunHistory.compareLatest(runs))
        assertTrue(RunHistory.summarize(runs).runs == 2)
    }

    @Test
    fun medianOfEvenAndOddLists() {
        assertEquals(2.0, RunHistory.median(listOf(3.0, 1.0, 2.0))!!, 0.0)
        assertEquals(2.5, RunHistory.median(listOf(4.0, 1.0, 2.0, 3.0))!!, 0.0)
        assertNull(RunHistory.median(emptyList()))
    }
}

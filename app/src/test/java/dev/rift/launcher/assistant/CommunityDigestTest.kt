package dev.rift.launcher.assistant

import app.gamenative.api.CommunityConfigDevice
import app.gamenative.api.CommunityConfigRun
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityDigestTest {

    private val now = CommunityDigester.parseTime("2026-10-17T00:00:00Z")!!

    private fun run(
        id: Long,
        rating: Int,
        driver: String,
        deviceId: Int,
        fps: Double? = 50.0,
        createdAt: String = "2026-10-01T00:00:00Z",
        notes: String = "",
    ) = CommunityConfigRun(
        id = id,
        rating = rating,
        averageFps = fps,
        tags = listOf("playable"),
        notes = notes,
        config = buildJsonObject {
            put("graphicsDriver", JsonPrimitive("turnip"))
            put("graphicsDriverVersion", JsonPrimitive(driver))
            put("dxwrapper", JsonPrimitive("dxvk"))
            put("useLegacyDRM", JsonPrimitive(false))
        },
        createdAt = createdAt,
        appVersion = "1.0",
        sessionLengthSeconds = 600,
        gameStore = "STEAM",
        device = CommunityConfigDevice(deviceId, "NP05J", "Adreno (TM) 830", "16", "SM8750"),
    )

    @Test
    fun groupsByRuntimeAndRanksByWeight() {
        val digest = CommunityDigester.digest(
            listOf(
                run(1, 5, "26.0.0", deviceId = 7, fps = 60.0),
                run(2, 4, "26.0.0", deviceId = 9, fps = 50.0),
                run(3, 5, "25.1.0", deviceId = 9),
                run(3, 5, "25.1.0", deviceId = 9),
            ),
            sameDeviceIds = setOf(7),
            now = now,
        )
        assertEquals("duplicates from two queries count once", 3, digest.totalRuns)
        assertEquals(2, digest.groups.size)
        val top = digest.groups.first()
        assertEquals("26.0.0", top.runtime["graphicsDriverVersion"])
        assertEquals(2, top.runs)
        assertEquals(1, top.sameDeviceRuns)
        assertEquals(1L, top.bestRunId)
        assertEquals(55.0, top.avgFps!!, 0.001)
        assertEquals("false", top.settings["useLegacyDRM"])
        val driverShares = digest.consensus.getValue("graphicsDriverVersion")
        assertEquals("26.0.0", driverShares.first().value)
        assertEquals(100, driverShares.sumOf { it.percent })
    }

    @Test
    fun olderAndOtherDeviceRunsWeighLess() {
        val fresh = CommunityDigester.weight(run(1, 5, "x", deviceId = 7), setOf(7), now)
        val otherDevice = CommunityDigester.weight(run(1, 5, "x", deviceId = 8), setOf(7), now)
        val yearOld = CommunityDigester.weight(run(1, 5, "x", deviceId = 7, createdAt = "2025-10-01T00:00:00Z"), setOf(7), now)
        assertTrue(otherDevice < fresh)
        assertTrue(yearOld < fresh / 3)
        assertEquals(0.0, CommunityDigester.weight(run(1, 0, "x", deviceId = 7), setOf(7), now), 0.0)
    }

    @Test
    fun notesAreShortenedToOneLine() {
        assertEquals("works great with turnip", CommunityDigester.shortNote("  works great\n\nwith   turnip "))
        assertEquals(200, CommunityDigester.shortNote("a".repeat(500))!!.length)
        assertEquals(null, CommunityDigester.shortNote("   "))
    }

    @Test
    fun parsesServerTimestamps() {
        assertEquals(
            CommunityDigester.parseTime("2026-04-23T18:55:11Z")!! + 115,
            CommunityDigester.parseTime("2026-04-23T18:55:11.115263+00:00"),
        )
    }
}

package dev.rift.launcher.assistant

import app.gamenative.api.CommunityGpuCompatibility
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProfilerTest {

    @Test
    fun decodesPackedVulkanVersion() {
        val packed = (1 shl 22) or (3 shl 12) or 284
        assertEquals("1.3.284", DeviceProfiler.decodeVulkanVersion(packed))
        assertNull(DeviceProfiler.decodeVulkanVersion(0))
    }

    @Test
    fun namesKnownSnapdragonParts() {
        assertEquals("Snapdragon 8 Elite", DeviceProfiler.socMarketingName("SM8750"))
        assertEquals("Snapdragon 8 Elite", DeviceProfiler.socMarketingName("sm8750-ac"))
        assertEquals("Snapdragon 8 Gen 3", DeviceProfiler.socMarketingName("SM8650"))
        assertNull(DeviceProfiler.socMarketingName("MT6989"))
    }

    @Test
    fun communityClassNamesMatchGameNativeClasses() {
        assertEquals("adreno_elite", DeviceProfiler.communityClassName(CommunityGpuCompatibility.ADRENO_ELITE))
        assertEquals("unknown", DeviceProfiler.communityClassName(CommunityGpuCompatibility.UNKNOWN))
    }

    @Test
    fun profileJsonRoundTrips() {
        val versions = DeviceProfile.ComponentVersions(ready = listOf("System"), downloadable = listOf("turnip26.0.0_R8"))
        val profile = DeviceProfile(
            collectedAt = 1L,
            hardware = DeviceProfile.Hardware("REDMAGIC", "NP05J", "QTI", "SM8750", "Snapdragon 8 Elite", "16", 36, "modern", "bionic"),
            gpu = DeviceProfile.Gpu("Adreno (TM) 830", "Qualcomm", "OpenGL ES 3.2", "1.3.284", "adreno_elite"),
            memory = DeviceProfile.Memory(16000, 9000),
            storage = DeviceProfile.Storage(500000, 120000),
            display = DeviceProfile.Display(2400, 1504, 165, listOf(165, 120, 60), 320),
            power = DeviceProfile.Power(84, true, 36, 0, 0.4f),
            components = DeviceProfile.Components(versions, versions, versions, versions, versions, versions, versions),
        )
        assertEquals(profile, Json.decodeFromString(DeviceProfile.serializer(), profile.toJson()))
    }
}

package dev.rift.launcher.assistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What Rift knows about the device: the hardware profile the AI assistant reads before it proposes container
 * settings, and what the "Device" settings page shows. Collected by [DeviceProfiler], kept plain and
 * serializable so it can be copied as JSON and later sent to a model as-is.
 */
@Serializable
data class DeviceProfile(
    val collectedAt: Long,
    val hardware: Hardware,
    val gpu: Gpu,
    val memory: Memory,
    val storage: Storage,
    val display: Display,
    val power: Power,
    val components: Components,
) {
    @Serializable
    data class Hardware(
        val manufacturer: String,
        val model: String,
        val socManufacturer: String,
        val socModel: String,
        /** Marketing name of the chip, such as "Snapdragon 8 Elite", when [socModel] is a known code. */
        val socName: String?,
        val androidVersion: String,
        val sdk: Int,
        /** "modern" or "legacy": which GameNative build flavour this APK is. */
        val build: String,
        /** Container variant games run in on this build: "bionic" or "glibc". */
        val containerVariant: String,
    )

    @Serializable
    data class Gpu(
        val renderer: String,
        val vendor: String,
        val glVersion: String,
        /** Highest Vulkan API the system driver reports, such as "1.3.284", or null when unknown. */
        val vulkanApi: String?,
        /** GPU class GameNative's community configs are matched by: adreno_elite, adreno_standard, other, unknown. */
        val communityClass: String,
    )

    @Serializable
    data class Memory(val totalMb: Long, val availableMb: Long)

    @Serializable
    data class Storage(val totalMb: Long, val freeMb: Long)

    @Serializable
    data class Display(
        val widthPx: Int,
        val heightPx: Int,
        val refreshHz: Int,
        val supportedRefreshHz: List<Int>,
        val densityDpi: Int,
    )

    @Serializable
    data class Power(
        val batteryPercent: Int?,
        val charging: Boolean?,
        val batteryTempC: Int?,
        /** [android.os.PowerManager] thermal status, 0 (none) to 6 (shutdown). */
        val thermalStatus: Int?,
        /** Forecast thermal headroom in 10 s, 1.0 means throttling starts. */
        val thermalHeadroom: Float?,
    )

    /** Container components: what is ready on the device and what GameNative's manifest can download. */
    @Serializable
    data class Components(
        val drivers: ComponentVersions,
        val wine: ComponentVersions,
        val dxvk: ComponentVersions,
        val vkd3d: ComponentVersions,
        val box64: ComponentVersions,
        val wowBox64: ComponentVersions,
        val fexcore: ComponentVersions,
    )

    @Serializable
    data class ComponentVersions(val ready: List<String>, val downloadable: List<String>)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}

/** A device in GameNative's community database that matches this one, so reports from it count the most. */
@Serializable
data class CommunityDeviceMatch(val id: Int, val model: String, val gpu: String, val soc: String)

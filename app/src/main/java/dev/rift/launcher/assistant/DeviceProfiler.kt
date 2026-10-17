package dev.rift.launcher.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.view.Display
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.api.CommunityConfigService
import app.gamenative.api.CommunityGpuCompatibility
import app.gamenative.api.canonicalCommunityGpu
import app.gamenative.api.communityGpuCompatibility
import app.gamenative.utils.ManifestComponentHelper
import app.gamenative.utils.ManifestContentTypes
import app.gamenative.utils.ManifestEntry
import com.winlator.container.Container
import com.winlator.core.GPUInformation
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Builds a [DeviceProfile] from what Android and GameNative already know. Nothing here runs a driver or a
 * Vulkan instance in Rift's process: the GPU name comes from GameNative's cached GL query, the Vulkan level
 * from the system feature list, and component lists from the same helpers GameNative's container dialog uses.
 */
object DeviceProfiler {

    suspend fun collect(context: Context): DeviceProfile = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        DeviceProfile(
            collectedAt = System.currentTimeMillis(),
            hardware = hardware(),
            gpu = gpu(app),
            memory = memory(app),
            storage = storage(app),
            display = display(app),
            power = power(app),
            components = components(app),
        )
    }

    /** Devices in GameNative's community database that match this one. Needs the network; empty when offline. */
    suspend fun matchCommunityDevices(profile: DeviceProfile): List<CommunityDeviceMatch> = try {
        val gpu = profile.gpu.renderer.takeIf { canonicalCommunityGpu(it).isNotEmpty() }.orEmpty()
        CommunityConfigService.shared.findDevices(
            manufacturer = profile.hardware.manufacturer,
            model = profile.hardware.model,
            gpu = gpu,
            androidVersion = profile.hardware.androidVersion,
        ).map { CommunityDeviceMatch(it.id, it.model, it.gpu, it.soc) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "DeviceProfiler: community device lookup failed")
        emptyList()
    }

    private fun hardware(): DeviceProfile.Hardware {
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else ""
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE
        return DeviceProfile.Hardware(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            socManufacturer = socManufacturer,
            socModel = socModel,
            socName = socMarketingName(socModel),
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            build = if (BuildConfig.MODERN_ANDROID) "modern" else "legacy",
            containerVariant = containerVariant(),
        )
    }

    private fun gpu(context: Context): DeviceProfile.Gpu {
        val renderer = runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty().trim()
        val vendor = runCatching { GPUInformation.getVendor(context) }.getOrNull().orEmpty().trim()
        val glVersion = runCatching { GPUInformation.getVersion(context) }.getOrNull().orEmpty().trim()
        val vulkan = context.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?.version
            ?.let(::decodeVulkanVersion)
        return DeviceProfile.Gpu(
            renderer = renderer,
            vendor = vendor,
            glVersion = glVersion,
            vulkanApi = vulkan,
            communityClass = communityClassName(communityGpuCompatibility(renderer)),
        )
    }

    private fun memory(context: Context): DeviceProfile.Memory {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(info)
        return DeviceProfile.Memory(totalMb = info.totalMem / MB, availableMb = info.availMem / MB)
    }

    private fun storage(context: Context): DeviceProfile.Storage = try {
        val stat = StatFs(context.filesDir.absolutePath)
        DeviceProfile.Storage(totalMb = stat.totalBytes / MB, freeMb = stat.availableBytes / MB)
    } catch (e: Exception) {
        DeviceProfile.Storage(totalMb = 0, freeMb = 0)
    }

    private fun display(context: Context): DeviceProfile.Display {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val width = mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
        val height = mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
        val supported = display?.supportedModes
            ?.filter { it.physicalWidth == width && it.physicalHeight == height }
            ?.map { it.refreshRate.roundToInt() }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()
        return DeviceProfile.Display(
            // Games run in landscape, so the long side is the width whatever the current rotation is.
            widthPx = maxOf(width, height),
            heightPx = minOf(width, height),
            refreshHz = (mode?.refreshRate ?: display?.refreshRate ?: 60f).roundToInt(),
            supportedRefreshHz = supported,
            densityDpi = context.resources.displayMetrics.densityDpi,
        )
    }

    private fun power(context: Context): DeviceProfile.Power {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return DeviceProfile.Power(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = battery?.let { it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 },
            batteryTempC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                ?.takeIf { it > 0 }
                ?.let { (it / 10f).roundToInt() },
            thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pm?.currentThermalStatus else null,
            thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm?.getThermalHeadroom(10)?.takeIf { !it.isNaN() }?.let { (it * 100).roundToInt() / 100f }
            } else {
                null
            },
        )
    }

    /**
     * Ready versions follow GameNative's container dialog: bundled entries plus installed content are
     * selectable; manifest entries that are not on the device are the ones it offers to download.
     */
    private suspend fun components(context: Context): DeviceProfile.Components {
        val availability = ManifestComponentHelper.loadComponentAvailability(context)
        val installed = availability.installed
        val variant = containerVariant()
        val bionic = variant.equals(Container.BIONIC, ignoreCase = true)
        val res = context.resources
        fun manifest(type: String): List<ManifestEntry> =
            ManifestComponentHelper.filterManifestByVariant(availability.manifest.items[type].orEmpty(), variant)

        return DeviceProfile.Components(
            drivers = componentVersions(
                base = ManifestComponentHelper.bundledGraphicsDriverBase(
                    res.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList(),
                ),
                installed = availability.installedDrivers,
                manifest = manifest(ManifestContentTypes.DRIVER),
            ),
            wine = componentVersions(
                base = res.getStringArray(if (bionic) R.array.bionic_wine_entries else R.array.glibc_wine_entries).toList(),
                installed = installed.wine + installed.proton,
                manifest = manifest(ManifestContentTypes.WINE) + manifest(ManifestContentTypes.PROTON),
            ),
            dxvk = componentVersions(
                base = ManifestComponentHelper.bundledDxWrapperBase(res.getStringArray(R.array.dxvk_version_entries).toList()),
                installed = installed.dxvk,
                manifest = manifest(ManifestContentTypes.DXVK),
            ),
            vkd3d = componentVersions(
                base = ManifestComponentHelper.bundledDxWrapperBase(res.getStringArray(R.array.vkd3d_version_entries).toList()),
                installed = installed.vkd3d,
                manifest = manifest(ManifestContentTypes.VKD3D),
            ),
            box64 = componentVersions(
                base = res.getStringArray(if (bionic) R.array.box64_bionic_version_entries else R.array.box64_version_entries).toList(),
                installed = installed.box64,
                manifest = manifest(ManifestContentTypes.BOX64),
            ),
            wowBox64 = componentVersions(
                base = res.getStringArray(R.array.wowbox64_version_entries).toList(),
                installed = installed.wowBox64,
                manifest = manifest(ManifestContentTypes.WOWBOX64),
            ),
            fexcore = componentVersions(
                base = res.getStringArray(R.array.fexcore_version_entries).toList(),
                installed = installed.fexcore,
                manifest = manifest(ManifestContentTypes.FEXCORE),
            ),
        )
    }

    private fun componentVersions(
        base: List<String>,
        installed: List<String>,
        manifest: List<ManifestEntry>,
    ): DeviceProfile.ComponentVersions {
        val options = ManifestComponentHelper.buildVersionOptionList(base, installed, manifest)
        return DeviceProfile.ComponentVersions(
            ready = options.ids.filterIndexed { i, _ -> !options.muted[i] },
            downloadable = options.ids.filterIndexed { i, _ -> options.muted[i] },
        )
    }

    /** Modern builds cannot run glibc containers, so every game there runs in bionic. */
    private fun containerVariant(): String =
        if (BuildConfig.MODERN_ANDROID) Container.BIONIC else Container.DEFAULT_VARIANT

    private const val MB = 1024L * 1024L

    /** Decodes [PackageManager.FEATURE_VULKAN_HARDWARE_VERSION]'s packed value (VK_MAKE_API_VERSION). */
    internal fun decodeVulkanVersion(packed: Int): String? {
        if (packed <= 0) return null
        return "${packed ushr 22}.${(packed ushr 12) and 0x3ff}.${packed and 0xfff}"
    }

    internal fun communityClassName(compatibility: CommunityGpuCompatibility): String =
        compatibility.name.lowercase(Locale.ROOT)

    /** Snapdragon part numbers for chips handhelds and phones that run GameNative ship with. */
    private val snapdragonNames = mapOf(
        "SM8850" to "Snapdragon 8 Elite Gen 5",
        "SM8750" to "Snapdragon 8 Elite",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8635" to "Snapdragon 8s Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865",
        "SM7675" to "Snapdragon 7+ Gen 3",
        "SM7550" to "Snapdragon 7 Gen 3",
    )

    internal fun socMarketingName(socModel: String): String? {
        val code = socModel.trim().uppercase(Locale.ROOT)
        return snapdragonNames.entries.firstOrNull { code.startsWith(it.key) }?.value
    }
}

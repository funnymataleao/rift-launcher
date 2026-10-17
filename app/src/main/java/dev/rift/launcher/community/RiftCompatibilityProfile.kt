package dev.rift.launcher.community

import android.content.Context
import android.os.Build
import app.gamenative.R
import app.gamenative.api.CommunityConfigRun
import app.gamenative.api.CommunityConfigService
import app.gamenative.api.CommunityConfigSort
import app.gamenative.api.canonicalCommunityGpu
import app.gamenative.api.communityConfigMatchType
import app.gamenative.ui.component.dialog.CommunityHardwareScope
import app.gamenative.utils.ContainerUtils
import com.winlator.container.Container
import com.winlator.container.ContainerData
import com.winlator.core.GPUInformation
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** The top-rated community profile for this device, the narrowest hardware scope that has one. */
data class RiftBestConfig(
    val run: CommunityConfigRun,
    val matchType: String,
    val scope: CommunityHardwareScope,
)

object RiftBestConfigs {
    /** Same lookup as the profiles list: Rift's database first, the community one when Rift has not seen the game. */
    suspend fun load(context: Context, gameName: String): RiftBestConfig? = withContext(Dispatchers.IO) {
        val gpu = runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty().trim()
            .takeIf { canonicalCommunityGpu(it).isNotEmpty() }.orEmpty()
        val services = listOfNotNull(RiftCommunity.configService, CommunityConfigService.shared)
        for (service in services) {
            try {
                val game = service.findGame(gameName) ?: continue
                val devices = runCatching {
                    service.findDevices(Build.MANUFACTURER, Build.MODEL, gpu, Build.VERSION.RELEASE)
                }.getOrDefault(emptyList())
                if (devices.isNotEmpty()) {
                    service.fetchConfigs(game.id, null, CommunityConfigSort.HIGHEST_RATED, 0, deviceIds = devices.map { it.id })
                        .runs.firstOrNull()
                        ?.let { return@withContext RiftBestConfig(it, communityConfigMatchType(gpu, it.device.gpu), CommunityHardwareScope.CURRENT_DEVICE) }
                }
                if (gpu.isNotBlank()) {
                    service.fetchConfigs(game.id, gpu, CommunityConfigSort.HIGHEST_RATED, 0)
                        .runs.firstOrNull()
                        ?.let { return@withContext RiftBestConfig(it, communityConfigMatchType(gpu, it.device.gpu), CommunityHardwareScope.CURRENT_GPU) }
                    service.fetchCompatibleConfigs(game.id, gpu, CommunityConfigSort.HIGHEST_RATED, 0)
                        .runs.firstOrNull()
                        ?.let { return@withContext RiftBestConfig(it, communityConfigMatchType(gpu, it.device.gpu), CommunityHardwareScope.COMPATIBLE_GPUS) }
                }
                return@withContext null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Best community config lookup failed for $gameName")
            }
        }
        null
    }
}

/**
 * One-step undo for applying a community profile: the container's config file is copied aside
 * right before the apply, and "Откатить" loads it back.
 */
object RiftProfileBackup {
    private fun file(container: Container) = File(container.rootDir, ".container.rift-backup")

    fun snapshot(container: Container) {
        runCatching { file(container).writeText(container.containerJson) }
            .onFailure { Timber.w(it, "Could not back up container ${container.id}") }
    }

    fun exists(context: Context, appId: String): Boolean =
        ContainerUtils.hasContainer(context, appId) &&
            runCatching { file(ContainerUtils.getContainer(context, appId)).isFile }.getOrDefault(false)

    fun restore(context: Context, appId: String): Boolean = runCatching {
        val container = ContainerUtils.getContainer(context, appId)
        val backup = file(container)
        container.loadData(JSONObject(backup.readText()))
        container.saveDataChecked().also { if (it) backup.delete() }
    }.onFailure { Timber.w(it, "Could not restore container $appId") }.getOrDefault(false)
}

/** The rows of "Мой профиль": what decides whether a PC game runs. */
fun riftProfileSummary(context: Context, data: ContainerData): List<Pair<String, String>> {
    fun String.setting(key: String) = split(',', ';')
        .firstOrNull { it.substringBefore('=').trim().equals(key, ignoreCase = true) }
        ?.substringAfter('=', "")?.trim().orEmpty()
    val driverVersion = data.graphicsDriverVersion.ifBlank { data.graphicsDriverConfig.setting("version") }
    val dx = listOf(
        data.dxwrapperConfig.setting("version"),
        data.dxwrapperConfig.setting("vkd3dVersion"),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    val fps = data.dxwrapperConfig.setting("framerate").toIntOrNull()
    return buildList {
        add(context.getString(R.string.community_config_wine) to data.wineVersion)
        add(context.getString(R.string.community_config_driver) to listOf(data.graphicsDriver, driverVersion).filter { it.isNotBlank() }.joinToString(" "))
        add(context.getString(R.string.rift_compat_dx) to listOf(data.dxwrapper, dx).filter { it.isNotBlank() }.joinToString(" "))
        add(context.getString(R.string.community_config_emulator) to data.emulator)
        add(context.getString(R.string.rift_compat_resolution) to data.screenSize.replace("x", " × "))
        add(
            context.getString(R.string.rift_compat_fps_limit) to
                if (fps == null || fps <= 0) context.getString(R.string.rift_compat_no_limit) else fps.toString(),
        )
    }.filter { it.second.isNotBlank() }
}

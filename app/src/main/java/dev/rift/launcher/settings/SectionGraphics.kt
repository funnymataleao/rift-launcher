package dev.rift.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.PrefManager
import app.gamenative.ui.component.dialog.ContainerConfigDialog
import app.gamenative.ui.screen.settings.ContentsManagerDialog
import app.gamenative.ui.screen.settings.DriverManagerDialog
import app.gamenative.utils.ContainerUtils
import com.winlator.container.Container
import com.winlator.core.KeyValueSet
import com.winlator.core.StringUtils

/** The Vulkan driver and DirectX layer games start with, and the managers that install more of them. */
@Composable
internal fun GraphicsSettings() {
    val defaults = rememberContainerDefaults()
    val data = defaults.data

    var driverDialog by rememberSaveable { mutableStateOf(false) }
    var memoryDialog by rememberSaveable { mutableStateOf(false) }
    var driverManager by rememberSaveable { mutableStateOf(false) }
    var contentsManager by rememberSaveable { mutableStateOf(false) }
    var fullConfig by rememberSaveable { mutableStateOf(false) }

    val bionic = data.containerVariant.equals(Container.BIONIC, ignoreCase = true)
    val driverEntries = stringArrayResource(
        if (bionic) R.array.bionic_graphics_driver_entries else R.array.graphics_driver_entries,
    )
    val driverIndex = driverEntries.indexOfFirst { StringUtils.parseIdentifier(it) == data.graphicsDriver }

    val wrapperEntries = stringArrayResource(R.array.dxwrapper_entries)
    val wrapperIndex = wrapperEntries.indexOfFirst { StringUtils.parseIdentifier(it) == data.dxwrapper }

    val memoryEntries = stringArrayResource(R.array.video_memory_size_entries)
    val memoryIndex = memoryEntries.indexOfFirst { StringUtils.parseNumber(it) == data.videoMemorySize }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_driver)) {
        LinkRow(
            title = stringResource(R.string.graphics_driver),
            subtitle = stringResource(R.string.rift_settings_driver_summary),
            icon = Icons.Rounded.Memory,
            value = driverEntries.getOrNull(driverIndex) ?: data.graphicsDriver,
            onClick = { driverDialog = true },
        )
        ChoiceChipsRow(
            title = stringResource(R.string.dx_wrapper),
            subtitle = stringResource(R.string.rift_settings_dxwrapper_summary),
            icon = Icons.Rounded.Extension,
            options = wrapperEntries.toList(),
            selectedIndex = wrapperIndex,
            onSelect = { index ->
                val wrapper = StringUtils.parseIdentifier(wrapperEntries[index])
                if (wrapper != data.dxwrapper) {
                    // The old wrapper's version does not exist for the new one; fall back to its default.
                    defaults.update { it.copy(dxwrapper = wrapper, dxwrapperConfig = withoutVersion(it.dxwrapperConfig)) }
                }
            },
        )
    }

    var globalFgEnabled by rememberSaveable { mutableStateOf(PrefManager.globalLsfgEnabled) }
    var globalFgMultiplier by rememberSaveable { mutableStateOf(PrefManager.globalLsfgMultiplier) }
    var globalFgFlow by rememberSaveable { mutableStateOf(PrefManager.globalLsfgFlowScale) }
    var globalFgPerformance by rememberSaveable { mutableStateOf(PrefManager.globalLsfgPerformanceMode) }
    var globalFgPresent by rememberSaveable { mutableStateOf(PrefManager.globalLsfgPresentMode) }
    var globalFgBackend by rememberSaveable { mutableStateOf(PrefManager.globalLsfgBackend) }

    SettingsGroup(title = "Global Frame Generation") {
        SwitchRow(
            title = "Frame Generation",
            subtitle = "Default LSFG setting for PC games. Games can inherit, override, or disable it.",
            icon = Icons.Rounded.AutoAwesome,
            checked = globalFgEnabled,
            onCheckedChange = { globalFgEnabled = it; PrefManager.globalLsfgEnabled = it },
        )
        StepSliderRow(
            title = "Multiplier", options = listOf("2x", "3x", "4x"),
            selectedIndex = (globalFgMultiplier - 2).coerceIn(0, 2),
            onSelect = { globalFgMultiplier = it + 2; PrefManager.globalLsfgMultiplier = it + 2 },
            enabled = globalFgEnabled,
        )
        StepSliderRow(
            title = "Flow Scale", options = (25..100 step 5).map { String.format(java.util.Locale.US, "%.2f", it / 100f) },
            selectedIndex = (((globalFgFlow - 0.25f) / 0.05f).toInt()).coerceIn(0, 15),
            onSelect = { globalFgFlow = 0.25f + it * 0.05f; PrefManager.globalLsfgFlowScale = globalFgFlow },
            enabled = globalFgEnabled,
        )
        SwitchRow(
            title = "Performance Mode", subtitle = "Use the faster LSFG model to reduce GPU cost.",
            checked = globalFgPerformance,
            onCheckedChange = { globalFgPerformance = it; PrefManager.globalLsfgPerformanceMode = it },
        )
        ChoiceChipsRow(
            title = "Present Mode", options = listOf("Mailbox", "FIFO"),
            selectedIndex = if (globalFgPresent == "fifo") 1 else 0,
            onSelect = { globalFgPresent = if (it == 1) "fifo" else "mailbox"; PrefManager.globalLsfgPresentMode = globalFgPresent },
        )
        ChoiceChipsRow(
            title = "Backend", options = listOf("Native", "Legacy"),
            selectedIndex = if (globalFgBackend == "legacy") 1 else 0,
            onSelect = { globalFgBackend = if (it == 1) "legacy" else "native"; PrefManager.globalLsfgBackend = globalFgBackend },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_compat)) {
        LinkRow(
            title = stringResource(R.string.video_memory_size),
            subtitle = stringResource(R.string.rift_settings_video_memory_summary),
            icon = Icons.Rounded.Storage,
            value = memoryEntries.getOrNull(memoryIndex) ?: "${data.videoMemorySize} MB",
            onClick = { memoryDialog = true },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_strict_shader),
            subtitle = stringResource(R.string.rift_settings_strict_shader_summary),
            icon = Icons.Rounded.Functions,
            checked = data.strictShaderMath,
            onCheckedChange = { value -> defaults.update { it.copy(strictShaderMath = value) } },
        )
        SwitchRow(
            title = stringResource(R.string.rift_settings_csmt),
            subtitle = stringResource(R.string.rift_settings_csmt_summary),
            icon = Icons.Rounded.Speed,
            checked = data.csmt,
            onCheckedChange = { value -> defaults.update { it.copy(csmt = value) } },
        )
    }

    SettingsGroup(title = stringResource(R.string.rift_settings_group_managers)) {
        LinkRow(
            title = stringResource(R.string.settings_emulation_driver_manager_title),
            subtitle = stringResource(R.string.settings_emulation_driver_manager_subtitle),
            icon = Icons.Rounded.Apps,
            onClick = { driverManager = true },
        )
        LinkRow(
            title = stringResource(R.string.settings_emulation_contents_manager_title),
            subtitle = stringResource(R.string.settings_emulation_contents_manager_subtitle),
            icon = Icons.Rounded.Inventory2,
            onClick = { contentsManager = true },
        )
        LinkRow(
            title = stringResource(R.string.rift_settings_full_config),
            subtitle = stringResource(R.string.rift_settings_full_config_graphics),
            icon = Icons.Rounded.Tune,
            onClick = { fullConfig = true },
        )
    }

    if (driverDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.graphics_driver),
            options = driverEntries.toList(),
            selectedIndex = driverIndex,
            onSelect = { index ->
                val driver = StringUtils.parseIdentifier(driverEntries[index])
                if (driver != data.graphicsDriver) {
                    // A driver's version list is its own, so go back to that driver's default version.
                    defaults.update {
                        it.copy(
                            graphicsDriver = driver,
                            graphicsDriverVersion = "",
                            graphicsDriverConfig = withoutVersion(it.graphicsDriverConfig),
                        )
                    }
                }
            },
            onDismiss = { driverDialog = false },
        )
    }
    if (memoryDialog) {
        RiftChoiceDialog(
            title = stringResource(R.string.video_memory_size),
            options = memoryEntries.toList(),
            selectedIndex = memoryIndex,
            onSelect = { index ->
                defaults.update { it.copy(videoMemorySize = StringUtils.parseNumber(memoryEntries[index])) }
            },
            onDismiss = { memoryDialog = false },
        )
    }
    if (driverManager) DriverManagerDialog(open = true, onDismiss = { driverManager = false })
    if (contentsManager) ContentsManagerDialog(open = true, onDismiss = { contentsManager = false })
    DefaultConfigDialog(visible = fullConfig, defaults = defaults, onDismiss = { fullConfig = false })
}

/** GameNative's own full "default container configuration" dialog, for everything the rows do not cover. */
@Composable
internal fun DefaultConfigDialog(visible: Boolean, defaults: ContainerDefaults, onDismiss: () -> Unit) {
    ContainerConfigDialog(
        visible = visible,
        title = stringResource(R.string.settings_emulation_default_config_dialog_title),
        default = true,
        initialConfig = ContainerUtils.getDefaultContainerData(),
        onDismissRequest = onDismiss,
        onSave = {
            ContainerUtils.setDefaultContainerData(it)
            defaults.reload()
        },
    )
}

/** "a=1,version=2,b=3" without the version key, the way GameNative's config strings are built. */
private fun withoutVersion(config: String): String =
    KeyValueSet(config).filter { it[0] != "version" }.joinToString(",") { "${it[0]}=${it[1]}" }

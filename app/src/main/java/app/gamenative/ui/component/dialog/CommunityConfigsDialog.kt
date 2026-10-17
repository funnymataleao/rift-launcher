package app.gamenative.ui.component.dialog

import android.os.Build
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftSizes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.api.CommunityConfigDevice
import app.gamenative.api.CommunityConfigRun
import app.gamenative.api.CommunityConfigService
import app.gamenative.api.CommunityConfigSort
import app.gamenative.api.CommunityGame
import app.gamenative.api.canonicalCommunityGpu
import app.gamenative.api.communityConfigMatchType
import app.gamenative.api.communityIdentityKey
import app.gamenative.api.sortCommunityRuns
import app.gamenative.utils.BestConfigService
import com.winlator.core.GPUInformation
import dev.rift.launcher.community.RiftCommunity
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CommunityHardwareScope {
    CURRENT_DEVICE,
    CURRENT_GPU,
    COMPATIBLE_GPUS,
}

data class CommunityConfigApplyOptions(
    val applyLaunchArguments: Boolean,
    val applyEnvironmentVariables: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityConfigsDialog(
    visible: Boolean,
    gameName: String,
    currentLaunchArguments: String,
    currentEnvironmentVariables: String,
    onDismissRequest: () -> Unit,
    onApply: (CommunityConfigRun, String, CommunityConfigApplyOptions) -> Unit,
    initialHardwareScope: CommunityHardwareScope = CommunityHardwareScope.CURRENT_DEVICE,
    canApply: Boolean = true,
    service: CommunityConfigService = CommunityConfigService.shared,
    riftService: CommunityConfigService? = RiftCommunity.configService,
) {
    if (!visible) return

    // Rift: configs come from Rift's own database first; GameNative's when Rift has not seen the game.
    var fromRift by remember(gameName) { mutableStateOf(riftService != null) }
    var sourceChosen by remember(gameName) { mutableStateOf(false) }
    val activeService = riftService?.takeIf { fromRift } ?: service

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var detectedGpu by remember(gameName) { mutableStateOf("") }
    var detectedDevices by remember(gameName) { mutableStateOf(emptyList<CommunityConfigDevice>()) }
    var resolvedGame by remember(gameName) { mutableStateOf<CommunityGame?>(null) }
    var sort by remember(gameName) { mutableStateOf(CommunityConfigSort.HIGHEST_RATED) }
    var hardwareScope by remember(gameName, initialHardwareScope) { mutableStateOf(initialHardwareScope) }
    var runs by remember(gameName) { mutableStateOf(emptyList<CommunityConfigRun>()) }
    var total by remember(gameName) { mutableIntStateOf(0) }
    var hasMore by remember(gameName) { mutableStateOf(false) }
    var currentPage by remember(gameName) { mutableIntStateOf(0) }
    var loading by remember(gameName) { mutableStateOf(true) }
    var loadingMore by remember(gameName) { mutableStateOf(false) }
    var errorMessage by remember(gameName) { mutableStateOf<String?>(null) }
    var selectedRun by remember(gameName) { mutableStateOf<CommunityConfigRun?>(null) }
    // Rift: the run shown in the detail pane on wide screens (the focused row).
    var highlightedRun by remember(gameName) { mutableStateOf<CommunityConfigRun?>(null) }
    var lookupKey by remember(gameName) { mutableIntStateOf(0) }
    var configRefreshKey by remember(gameName) { mutableIntStateOf(0) }
    var requestGeneration by remember(gameName) { mutableIntStateOf(0) }

    LaunchedEffect(visible, gameName, lookupKey, fromRift) {
        val generation = ++requestGeneration
        loading = true
        loadingMore = false
        errorMessage = null
        runs = emptyList()
        total = 0
        hasMore = false
        currentPage = 0
        resolvedGame = null
        try {
            val renderer = withContext(Dispatchers.IO) {
                runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty().trim()
            }
            val gpu = renderer.takeIf { canonicalCommunityGpu(it).isNotEmpty() }.orEmpty()
            val devices = try {
                activeService.findDevices(
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    gpu = gpu,
                    androidVersion = Build.VERSION.RELEASE,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            val game = activeService.findGame(gameName)
            if (generation != requestGeneration) return@LaunchedEffect
            if (game == null && fromRift && !sourceChosen) {
                fromRift = false
                return@LaunchedEffect
            }

            detectedGpu = gpu
            detectedDevices = devices
            if (devices.isEmpty() &&
                detectedGpu.isNotBlank() &&
                hardwareScope == CommunityHardwareScope.CURRENT_DEVICE
            ) {
                hardwareScope = CommunityHardwareScope.CURRENT_GPU
            }
            if (game == null) {
                errorMessage = context.getString(R.string.community_config_game_not_found, gameName)
                loading = false
            } else {
                resolvedGame = game
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == requestGeneration && fromRift && !sourceChosen) {
                fromRift = false
            } else if (generation == requestGeneration) {
                errorMessage = context.getString(
                    R.string.community_config_load_failed,
                    error.message ?: context.getString(R.string.community_config_unknown_error),
                )
                loading = false
            }
        }
    }

    LaunchedEffect(
        visible,
        resolvedGame?.id,
        detectedGpu,
        detectedDevices.map { it.id },
        sort,
        hardwareScope,
        lookupKey,
        configRefreshKey,
    ) {
        val game = resolvedGame ?: return@LaunchedEffect
        val generation = ++requestGeneration
        loading = true
        loadingMore = false
        errorMessage = null
        runs = emptyList()
        total = 0
        hasMore = false
        currentPage = 0
        if (detectedDevices.isEmpty() && detectedGpu.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        try {
            val result = if (hardwareScope == CommunityHardwareScope.COMPATIBLE_GPUS) {
                activeService.fetchCompatibleConfigs(game.id, detectedGpu, sort, page = 0)
            } else {
                activeService.fetchConfigs(
                    gameId = game.id,
                    gpu = detectedGpu.takeIf { hardwareScope == CommunityHardwareScope.CURRENT_GPU },
                    sort = sort,
                    page = 0,
                    deviceIds = detectedDevices.map { it.id }
                        .takeIf { hardwareScope == CommunityHardwareScope.CURRENT_DEVICE }
                        .orEmpty(),
                )
            }
            if (generation != requestGeneration) return@LaunchedEffect
            runs = result.runs
            total = result.total
            currentPage = result.page
            hasMore = result.hasMore
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == requestGeneration) {
                errorMessage = context.getString(
                    R.string.community_config_load_failed,
                    error.message ?: context.getString(R.string.community_config_unknown_error),
                )
            }
        } finally {
            if (generation == requestGeneration) loading = false
        }
    }

    fun loadMore() {
        val game = resolvedGame ?: return
        if (loadingMore || !hasMore) return
        val generation = requestGeneration
        val requestedSort = sort
        val requestedScope = hardwareScope
        val requestedGpu = detectedGpu.takeIf { requestedScope != CommunityHardwareScope.CURRENT_DEVICE }
        val requestedDeviceIds = detectedDevices.map { it.id }
            .takeIf { requestedScope == CommunityHardwareScope.CURRENT_DEVICE }
            .orEmpty()
        val requestedPage = currentPage + 1
        fun requestIsCurrent(): Boolean = generation == requestGeneration &&
            sort == requestedSort &&
            hardwareScope == requestedScope &&
            resolvedGame?.id == game.id &&
            detectedGpu.takeIf { requestedScope != CommunityHardwareScope.CURRENT_DEVICE } == requestedGpu &&
            detectedDevices.map { it.id }
                .takeIf { requestedScope == CommunityHardwareScope.CURRENT_DEVICE }
                .orEmpty() == requestedDeviceIds
        loadingMore = true
        coroutineScope.launch {
            errorMessage = null
            try {
                val result = if (requestedScope == CommunityHardwareScope.COMPATIBLE_GPUS) {
                    activeService.fetchCompatibleConfigs(game.id, requestedGpu.orEmpty(), requestedSort, requestedPage)
                } else {
                    activeService.fetchConfigs(
                        gameId = game.id,
                        gpu = requestedGpu,
                        sort = requestedSort,
                        page = requestedPage,
                        deviceIds = requestedDeviceIds,
                    )
                }
                if (requestIsCurrent()) {
                    val mergedRuns = sortCommunityRuns(
                        (runs + result.runs).distinctBy { it.communityIdentityKey() },
                        requestedSort,
                    )
                    runs = mergedRuns
                    total = if (requestedScope == CommunityHardwareScope.COMPATIBLE_GPUS) {
                        mergedRuns.size
                    } else {
                        result.total
                    }
                    currentPage = result.page
                    hasMore = result.hasMore
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestIsCurrent()) {
                    errorMessage = context.getString(
                        R.string.community_config_load_failed,
                        error.message ?: context.getString(R.string.community_config_unknown_error),
                    )
                }
            } finally {
                if (requestIsCurrent()) loadingMore = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        // Rift: black OLED screen with the game, device and GPU as the header and round close/refresh buttons.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = RiftColors.Background,
            topBar = {
                RiftConfigHeader(
                    gameName = resolvedGame?.name ?: gameName,
                    hardware = listOf(
                        detectedDevices.firstOrNull()?.model ?: listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim(),
                        detectedGpu.ifBlank { stringResource(R.string.community_config_gpu_unknown) },
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    total = total,
                    refreshEnabled = !loading && !loadingMore,
                    onRefresh = {
                        activeService.clearConfigCache()
                        configRefreshKey++
                    },
                    onClose = onDismissRequest,
                )
            },
        ) { paddingValues ->
            val controlsEnabled = !loading && !loadingMore
            val scopeOptions = CommunityHardwareScope.entries.filter {
                if (it == CommunityHardwareScope.CURRENT_DEVICE) detectedDevices.isNotEmpty() else detectedGpu.isNotBlank()
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    // Rift: LB/RB switch the hardware filter, Y switches the sort.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || !controlsEnabled) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_BUTTON_L1, android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                                val index = scopeOptions.indexOf(hardwareScope)
                                val step = if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_R1) 1 else -1
                                scopeOptions.getOrNull(index + step)?.let { hardwareScope = it }
                                true
                            }
                            android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                                sort = if (sort == CommunityConfigSort.HIGHEST_RATED) CommunityConfigSort.NEWEST else CommunityConfigSort.HIGHEST_RATED
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                if (riftService != null) {
                    RiftPillGroup(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)) {
                        listOf(true, false).forEach { rift ->
                            RiftFilterPill(
                                text = stringResource(if (rift) R.string.rift_community_source_rift else R.string.rift_community_source_gamenative),
                                selected = fromRift == rift,
                                enabled = controlsEnabled,
                                onClick = {
                                    sourceChosen = true
                                    fromRift = rift
                                },
                            )
                        }
                    }
                }
                RiftConfigFilters(
                    sort = sort,
                    hardwareScope = hardwareScope,
                    scopeOptions = scopeOptions,
                    enabled = controlsEnabled,
                    onSortChange = { sort = it },
                    onHardwareScopeChange = { hardwareScope = it },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        errorMessage != null && runs.isEmpty() -> CommunityConfigError(
                            message = errorMessage.orEmpty(),
                            onRetry = {
                                if (resolvedGame == null) {
                                    lookupKey++
                                } else {
                                    activeService.clearConfigCache()
                                    configRefreshKey++
                                }
                            },
                            modifier = Modifier.align(Alignment.Center),
                        )
                        runs.isEmpty() -> CommunityConfigEmptyState(
                            hardwareScope = hardwareScope,
                            gpuAvailable = detectedGpu.isNotBlank(),
                            hasMore = hasMore,
                            loadingMore = loadingMore,
                            onBroaden = {
                                hardwareScope = if (
                                    hardwareScope == CommunityHardwareScope.CURRENT_DEVICE &&
                                    detectedGpu.isNotBlank()
                                ) {
                                    CommunityHardwareScope.CURRENT_GPU
                                } else {
                                    CommunityHardwareScope.COMPATIBLE_GPUS
                                }
                            },
                            onLoadMore = ::loadMore,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth >= 840.dp
                        val shownRun = highlightedRun?.takeIf { it in runs } ?: runs.first()
                        Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.weight(if (wide) 0.46f else 1f).fillMaxHeight(),
                            contentPadding = if (wide) PaddingValues(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 48.dp) else PaddingValues(bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 0.dp),
                        ) {
                            items(
                                items = runs,
                                key = { it.communityIdentityKey() },
                            ) { run ->
                                if (wide) {
                                    RiftConfigRow(
                                        run = run,
                                        selected = run == shownRun,
                                        onFocused = { highlightedRun = run },
                                        onClick = { selectedRun = run },
                                    )
                                } else {
                                    CommunityConfigListItem(
                                        run = run,
                                        onClick = { selectedRun = run },
                                    )
                                    HorizontalDivider()
                                }
                            }
                            if (errorMessage != null) {
                                item(key = "pagination-error") {
                                    CommunityConfigError(
                                        message = errorMessage.orEmpty(),
                                        onRetry = ::loadMore,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else if (hasMore) {
                                item(key = "load-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Button(onClick = ::loadMore, enabled = !loadingMore) {
                                            if (loadingMore) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Icon(Icons.Default.ExpandMore, null)
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.community_config_load_more))
                                        }
                                    }
                                }
                            }
                        }
                        if (wide) {
                            RiftConfigDetailPane(
                                run = shownRun,
                                canApply = canApply,
                                onApply = { selectedRun = shownRun },
                                modifier = Modifier.weight(0.54f).fillMaxHeight(),
                            )
                        }
                        }
                        }
                    }
                }
            }
        }
    }

    selectedRun?.let { run ->
        val matchType = communityConfigMatchType(detectedGpu, run.device.gpu)
        CommunityConfigPreviewDialog(
            run = run,
            matchType = matchType,
            currentLaunchArguments = currentLaunchArguments,
            currentEnvironmentVariables = currentEnvironmentVariables,
            canApply = canApply,
            onDismissRequest = { selectedRun = null },
            onApply = { options ->
                selectedRun = null
                onApply(run, matchType, options)
            },
        )
    }
}

@Composable
private fun CommunityConfigListItem(
    run: CommunityConfigRun,
    onClick: () -> Unit,
) {
    val performance = buildList {
        add(stringResource(R.string.community_config_rating_value, run.rating))
        run.averageFps?.let {
            add(stringResource(R.string.community_config_fps_value, String.format(Locale.getDefault(), "%.1f", it)))
        }
        formatCommunityConfigDate(run.createdAt).takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" | ")
    val hardware = listOf(run.device.model, run.device.gpu)
        .filter { it.isNotBlank() }
        .joinToString(" | ")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(run.rating.toString(), style = MaterialTheme.typography.labelMedium)
            }
        },
        headlineContent = {
            Text(
                text = performance,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hardware.isNotBlank()) {
                    Text(hardware, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (run.notes.isNotBlank()) {
                    Text(
                        text = run.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
    )
}

@Composable
private fun CommunityConfigError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text(stringResource(R.string.community_config_retry)) }
    }
}

@Composable
private fun CommunityConfigEmptyState(
    hardwareScope: CommunityHardwareScope,
    gpuAvailable: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    onBroaden: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                when {
                    !gpuAvailable -> R.string.community_config_gpu_unknown
                    hardwareScope == CommunityHardwareScope.CURRENT_DEVICE -> {
                        R.string.community_config_no_device_results
                    }
                    hardwareScope == CommunityHardwareScope.CURRENT_GPU -> {
                        R.string.community_config_no_gpu_results
                    }
                    else -> R.string.community_config_no_compatible_results
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (gpuAvailable && hardwareScope != CommunityHardwareScope.COMPATIBLE_GPUS) {
            Button(onClick = onBroaden) {
                Text(
                    stringResource(
                        if (hardwareScope == CommunityHardwareScope.CURRENT_DEVICE && gpuAvailable) {
                            R.string.community_config_show_this_gpu
                        } else {
                            R.string.community_config_show_compatible_gpus
                        },
                    ),
                )
            }
        }
        if (hasMore) {
            Button(onClick = onLoadMore, enabled = !loadingMore) {
                if (loadingMore) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.ExpandMore, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.community_config_load_more))
            }
        }
    }
}

@Composable
internal fun CommunityConfigPreviewDialog(
    run: CommunityConfigRun,
    matchType: String,
    currentLaunchArguments: String,
    currentEnvironmentVariables: String,
    canApply: Boolean,
    onDismissRequest: () -> Unit,
    onApply: (CommunityConfigApplyOptions) -> Unit,
) {
    val context = LocalContext.current
    val launchArguments = run.configString("execArgs")
    val environmentVariables = run.configString("envVars")
    var dependencyNames by remember(run.id) { mutableStateOf<List<String>?>(null) }
    var dependencyCheckFailed by remember(run.id) { mutableStateOf(false) }
    var applyLaunchArguments by remember(run.id) { mutableStateOf(false) }
    var applyEnvironmentVariables by remember(run.id) { mutableStateOf(false) }

    LaunchedEffect(run.id, matchType) {
        dependencyNames = null
        dependencyCheckFailed = false
        try {
            dependencyNames = withContext(Dispatchers.IO) {
                BestConfigService.resolveMissingManifestInstallRequests(
                    context = context,
                    configJson = run.config,
                    matchType = matchType,
                    matchedGpu = run.device.gpu,
                    preserveConfigValues = true,
                ).map { it.entry.name }.distinct()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            dependencyCheckFailed = true
            dependencyNames = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.community_config_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!canApply) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.community_config_install_before_apply),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                if (matchType == "fallback_match") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.community_config_other_gpu_warning),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                CommunityConfigSectionTitle(stringResource(R.string.community_config_run_details))
                CommunityConfigDetailRow(
                    stringResource(R.string.community_config_rating),
                    stringResource(R.string.community_config_rating_value, run.rating),
                )
                run.averageFps?.let {
                    CommunityConfigDetailRow(
                        stringResource(R.string.community_config_average_fps),
                        stringResource(R.string.community_config_fps_value, String.format(Locale.getDefault(), "%.1f", it)),
                    )
                }
                run.sessionLengthSeconds?.let {
                    CommunityConfigDetailRow(
                        stringResource(R.string.community_config_session_length),
                        DateUtils.formatElapsedTime(it),
                    )
                }
                formatCommunityConfigStore(run.gameStore).takeIf { it.isNotBlank() }?.let {
                    CommunityConfigDetailRow(stringResource(R.string.community_config_game_store), it)
                }
                CommunityConfigDetailRow(
                    stringResource(R.string.community_config_device),
                    run.device.model.ifBlank { stringResource(R.string.community_config_unknown) },
                )
                CommunityConfigDetailRow(
                    stringResource(R.string.community_config_gpu),
                    run.device.gpu.ifBlank { stringResource(R.string.community_config_gpu_unknown) },
                )
                run.device.androidVersion.takeIf { it.isNotBlank() }?.let {
                    CommunityConfigDetailRow(stringResource(R.string.community_config_android), it)
                }
                run.device.soc.takeIf { it.isNotBlank() }?.let {
                    CommunityConfigDetailRow(stringResource(R.string.community_config_soc), it)
                }
                run.appVersion.takeIf { it.isNotBlank() }?.let {
                    CommunityConfigDetailRow(stringResource(R.string.community_config_app_version), it)
                }
                formatCommunityConfigDate(run.createdAt).takeIf { it.isNotBlank() }?.let {
                    CommunityConfigDetailRow(stringResource(R.string.community_config_submitted), it)
                }

                CommunityConfigSectionTitle(stringResource(R.string.community_config_settings))
                communityConfigSummary(run).forEach { (label, value) ->
                    CommunityConfigDetailRow(label, value)
                }

                if (launchArguments.isNotBlank() || environmentVariables.isNotBlank()) {
                    CommunityConfigSectionTitle(
                        stringResource(R.string.community_config_additional_launch_settings),
                    )
                    if (launchArguments.isNotBlank()) {
                        CommunityConfigApplyOption(
                            label = stringResource(R.string.community_config_apply_launch_arguments),
                            value = launchArguments,
                            checked = applyLaunchArguments,
                            replacesCurrentValue = currentLaunchArguments.isNotBlank() &&
                                currentLaunchArguments != launchArguments,
                            onCheckedChange = { applyLaunchArguments = it },
                        )
                    }
                    if (environmentVariables.isNotBlank()) {
                        CommunityConfigApplyOption(
                            label = stringResource(R.string.community_config_apply_environment_variables),
                            value = environmentVariables,
                            checked = applyEnvironmentVariables,
                            replacesCurrentValue = currentEnvironmentVariables.isNotBlank() &&
                                currentEnvironmentVariables != environmentVariables,
                            onCheckedChange = { applyEnvironmentVariables = it },
                        )
                    }
                }

                CommunityConfigSectionTitle(stringResource(R.string.community_config_dependencies))
                when {
                    dependencyNames == null -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    dependencyCheckFailed -> Text(
                        stringResource(R.string.community_config_dependencies_apply_check),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    dependencyNames.orEmpty().isEmpty() -> Text(
                        stringResource(R.string.community_config_no_downloads),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> Text(
                        stringResource(
                            R.string.community_config_downloads_required,
                            dependencyNames.orEmpty().joinToString("\n"),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (run.tags.isNotEmpty()) {
                    CommunityConfigSectionTitle(stringResource(R.string.community_config_tags))
                    Text(run.tags.joinToString(" | "), style = MaterialTheme.typography.bodyMedium)
                }
                if (run.notes.isNotBlank()) {
                    CommunityConfigSectionTitle(stringResource(R.string.community_config_notes))
                    Text(run.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canApply,
                onClick = {
                    onApply(
                        CommunityConfigApplyOptions(
                            applyLaunchArguments = applyLaunchArguments,
                            applyEnvironmentVariables = applyEnvironmentVariables,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.community_config_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun CommunityConfigApplyOption(
    label: String,
    value: String,
    checked: Boolean,
    replacesCurrentValue: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (replacesCurrentValue) {
                Text(
                    text = stringResource(R.string.community_config_replaces_existing_value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun formatCommunityConfigStore(value: String): String {
    return when (value) {
        "steam" -> "Steam"
        "epic" -> "Epic Games Store"
        "gog" -> "GOG"
        "amazon" -> "Amazon Games"
        "custom" -> stringResource(R.string.community_config_custom_game)
        else -> value
    }
}

@Composable
private fun CommunityConfigSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CommunityConfigDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@Composable
private fun communityConfigSummary(run: CommunityConfigRun): List<Pair<String, String>> {
    val context = LocalContext.current
    return buildList {
        fun addValue(label: Int, key: String) {
            run.configString(key).takeIf { it.isNotBlank() }?.let {
                add(context.getString(label) to it)
            }
        }
        addValue(R.string.community_config_container, "containerVariant")
        addValue(R.string.community_config_wine, "wineVersion")
        addValue(R.string.community_config_emulator, "emulator")
        addValue(R.string.community_config_wrapper, "dxwrapper")
        addValue(R.string.community_config_box64_preset, "box64Preset")
        run.configString("graphicsDriverConfig")
            .settingValue("version")
            .takeIf { it.isNotBlank() }
            ?.let { add(context.getString(R.string.community_config_driver) to it) }
    }
}

private fun String.settingValue(key: String): String {
    return split(',', ';')
        .firstOrNull { it.substringBefore('=').trim().equals(key, ignoreCase = true) }
        ?.substringAfter('=', "")
        ?.trim()
        .orEmpty()
}

private fun formatCommunityConfigDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrDefault(value.substringBefore('T'))
}

/** Rift: hardware filter pills between LB/RB glyphs, sort pills with a Y glyph. */
@Composable
private fun RiftConfigFilters(
    sort: CommunityConfigSort,
    hardwareScope: CommunityHardwareScope,
    scopeOptions: List<CommunityHardwareScope>,
    enabled: Boolean,
    onSortChange: (CommunityConfigSort) -> Unit,
    onHardwareScopeChange: (CommunityHardwareScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RiftButtonGlyph("LB")
        RiftPillGroup { CommunityHardwareScope.entries.forEach { option ->
            RiftFilterPill(
                text = stringResource(
                    when (option) {
                        CommunityHardwareScope.CURRENT_DEVICE -> R.string.community_config_same_device
                        CommunityHardwareScope.CURRENT_GPU -> R.string.community_config_this_gpu
                        CommunityHardwareScope.COMPATIBLE_GPUS -> R.string.community_config_compatible_gpus
                    },
                ),
                selected = hardwareScope == option,
                enabled = enabled && option in scopeOptions,
                onClick = { onHardwareScopeChange(option) },
            )
        } }
        RiftButtonGlyph("RB")
        Spacer(Modifier.width(24.dp))
        RiftButtonGlyph("Y")
        RiftPillGroup { CommunityConfigSort.entries.forEach { option ->
            RiftFilterPill(
                text = stringResource(
                    if (option == CommunityConfigSort.HIGHEST_RATED) R.string.community_config_highest_rated else R.string.community_config_newest,
                ),
                selected = sort == option,
                enabled = enabled,
                onClick = { onSortChange(option) },
            )
        } }
    }
}

/** Whether focus is inside the current pill group: then only the focused pill is filled, else the active one. */
private val LocalFilterGroupFocused = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun RiftPillGroup(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    var groupFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.onFocusChanged { groupFocused = it.hasFocus },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalFilterGroupFocused provides groupFocused) { content() }
    }
}

@Composable
private fun RiftButtonGlyph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = RiftColors.OnSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun RiftFilterPill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    // Rift: two states: the pill in focus is filled; with focus elsewhere the active choice is.
    val groupFocused = LocalFilterGroupFocused.current
    val filled = if (groupFocused) focused else selected
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (filled) RiftColors.Primary else Color.White.copy(alpha = 0.07f))
            .selectable(selected = selected, enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (filled) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = when {
                filled -> RiftColors.OnPrimary
                enabled -> RiftColors.OnSurface
                else -> RiftColors.Outline
            },
            maxLines = 1,
        )
    }
}

/** Rift: one profile in the list, every row the same height; focusing it shows it in the detail pane. */
@Composable
private fun RiftConfigRow(
    run: CommunityConfigRun,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused() }
    val shape = RoundedCornerShape(18.dp)
    val secondary = buildList {
        run.averageFps?.let { add(stringResource(R.string.community_config_fps_value, String.format(Locale.getDefault(), "%.1f", it))) }
        run.configString("wineVersion").takeIf { it.isNotBlank() }?.let(::add)
        formatCommunityConfigDate(run.createdAt).takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(shape)
            .background(if (focused) RiftColors.Primary else RiftColors.Surface)
            .selectable(selected = selected, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        focused -> Color.Black.copy(alpha = 0.12f)
                        selected -> RiftColors.Primary
                        else -> Color.White.copy(alpha = 0.1f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = run.rating.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected || focused) RiftColors.OnPrimary else RiftColors.OnSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOf(run.device.model, run.device.gpu).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { stringResource(R.string.community_config_unknown) },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (focused) RiftColors.OnPrimary else RiftColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (focused) RiftColors.OnPrimary.copy(alpha = 0.72f) else RiftColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (focused) RiftColors.OnPrimary else RiftColors.OnSurfaceVariant)
    }
}

/** Rift: the right half of the profiles screen, the focused profile in full with Apply. */
@Composable
private fun RiftConfigDetailPane(
    run: CommunityConfigRun,
    canApply: Boolean,
    onApply: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .padding(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(RiftColors.Surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = run.rating.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = RiftColors.Primary,
            )
            Text(
                text = listOfNotNull(
                    stringResource(R.string.rift_compat_of_five),
                    run.averageFps?.let { stringResource(R.string.community_config_fps_value, String.format(Locale.getDefault(), "%.1f", it)) },
                    run.sessionLengthSeconds?.let { DateUtils.formatElapsedTime(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = RiftColors.OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommunityConfigDetailRow(
                stringResource(R.string.community_config_device),
                run.device.model.ifBlank { stringResource(R.string.community_config_unknown) },
            )
            CommunityConfigDetailRow(
                stringResource(R.string.community_config_gpu),
                run.device.gpu.ifBlank { stringResource(R.string.community_config_gpu_unknown) },
            )
            run.device.androidVersion.takeIf { it.isNotBlank() }?.let {
                CommunityConfigDetailRow(stringResource(R.string.community_config_android), it)
            }
            formatCommunityConfigDate(run.createdAt).takeIf { it.isNotBlank() }?.let {
                CommunityConfigDetailRow(stringResource(R.string.community_config_submitted), it)
            }
            HorizontalDivider(color = RiftColors.OutlineVariant)
            communityConfigSummary(run).forEach { (label, value) -> CommunityConfigDetailRow(label, value) }
            if (run.tags.isNotEmpty()) {
                HorizontalDivider(color = RiftColors.OutlineVariant)
                Text(run.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = RiftColors.OnSurfaceVariant)
            }
            if (run.notes.isNotBlank()) {
                Text(run.notes, style = MaterialTheme.typography.bodyMedium, color = RiftColors.OnSurface)
            }
        }
        val interactionSource = remember { MutableInteractionSource() }
        val applyFocused by interactionSource.collectIsFocusedAsState()
        val shape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(RiftSizes.button)
                .clip(shape)
                .background(if (applyFocused) RiftColors.Primary else Color.White.copy(alpha = if (canApply) 0.1f else 0.05f))
                .selectable(selected = false, interactionSource = interactionSource, indication = null, onClick = onApply),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.rift_compat_apply),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = when {
                    applyFocused -> RiftColors.OnPrimary
                    canApply -> RiftColors.OnSurface
                    else -> RiftColors.Outline
                },
            )
        }
    }
}

/** Rift: the profiles screen header: game, hardware and count on the left, round refresh and close on the right. */
@Composable
private fun RiftConfigHeader(
    gameName: String,
    hardware: String,
    total: Int,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.community_config_title).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = RiftColors.OnSurfaceVariant,
            )
            Text(
                text = gameName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = RiftColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    hardware.takeIf { it.isNotBlank() },
                    if (total > 0) pluralStringResource(R.plurals.community_config_result_count, total, total) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = RiftColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RiftHeaderButton(Icons.Default.Refresh, stringResource(R.string.community_config_refresh), refreshEnabled, onRefresh)
        RiftHeaderButton(Icons.Default.Close, stringResource(R.string.community_config_close), true, onClose)
    }
}

@Composable
private fun RiftHeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(RiftSizes.button)
            .clip(RoundedCornerShape(50))
            .background(if (focused) RiftColors.Primary else Color.White.copy(alpha = 0.1f))
            .selectable(selected = false, enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                focused -> RiftColors.OnPrimary
                enabled -> RiftColors.OnSurface
                else -> RiftColors.Outline
            },
        )
    }
}

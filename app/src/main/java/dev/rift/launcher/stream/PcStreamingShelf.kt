package dev.rift.launcher.stream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import dev.rift.launcher.home.CoverCard
import dev.rift.launcher.home.CoverColors
import dev.rift.launcher.ui.theme.RiftColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ADD_PC_KEY = "stream:add"

/** One cover in the "PC streaming" row: an app of a paired PC, or the "Add PC" card at the end. */
internal sealed interface StreamEntry {
    val key: String

    data class App(val pc: ComputerDetails, val app: NvApp) : StreamEntry {
        override val key = "stream:${pc.uuid}:${app.appId}"
    }

    data object AddPc : StreamEntry {
        override val key = ADD_PC_KEY
    }
}

/**
 * State of the "PC streaming" tab, kept by the home screen like its other rows: the paired PCs,
 * their apps, the row position and the focused cover.
 */
class PcStreamingTab internal constructor(internal val hosts: PcHosts, val rowState: LazyListState) {
    internal val apps = mutableStateMapOf<String, List<NvApp>>()
    internal val errors = mutableStateMapOf<String, String>()
    internal var focusedKey by mutableStateOf<String?>(null)

    internal val paired: List<ComputerDetails>
        get() = hosts.hosts.values.filter { PcStreaming.isPaired(it) }.sortedBy { it.name }

    internal val entries: List<StreamEntry>
        get() = paired.flatMap { pc ->
            if (PcStreaming.isOnline(pc)) apps[pc.uuid].orEmpty().map { StreamEntry.App(pc, it) } else emptyList()
        } + StreamEntry.AddPc

    /** The cover focus goes back to on this tab: the last focused one, else the first. */
    fun focusKey(): String {
        val keys = entries.map { it.key }
        return focusedKey?.takeIf { it in keys } ?: keys.first()
    }

    /** Y on the home screen: streams the focused app straight away. */
    fun launchFocused(context: Context): Boolean {
        val entry = entries.firstOrNull { it.key == focusKey() } as? StreamEntry.App ?: return false
        val uniqueId = hosts.uniqueId ?: return false
        PcStreaming.startStream(context, entry.pc, entry.app, uniqueId)
        return true
    }
}

@Composable
fun rememberPcStreamingTab(): PcStreamingTab {
    val hosts = rememberPcHosts()
    val rowState = rememberLazyListState()
    val tab = remember(hosts) { PcStreamingTab(hosts, rowState) }
    var savedKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) { if (tab.focusedKey == null) tab.focusedKey = savedKey }
    LaunchedEffect(tab.focusedKey) { savedKey = tab.focusedKey }
    return tab
}

/**
 * Content of the "PC streaming" tab: the focused app's name large, and one row of every app the
 * paired PCs' hosts (Apollo / Vibepollo / Sunshine) offer. A or Y starts the stream.
 */
@Composable
fun PcStreamingContent(
    tab: PcStreamingTab,
    cardHeight: Dp,
    screenPadding: Dp,
    cardGap: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
    onWantFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paired = tab.paired
    val uniqueId = tab.hosts.uniqueId

    // Apps load once per PC when it comes online.
    paired.forEach { pc ->
        val online = PcStreaming.isOnline(pc)
        LaunchedEffect(pc.uuid, online, uniqueId) {
            if (online && uniqueId != null && tab.apps[pc.uuid] == null) {
                try {
                    tab.apps[pc.uuid] = PcStreaming.loadApps(context, pc, uniqueId)
                    tab.errors.remove(pc.uuid)
                } catch (e: Exception) {
                    tab.errors[pc.uuid] = e.message ?: e.javaClass.simpleName
                }
            }
        }
    }

    val entries = tab.entries
    // Covers arrive after the tab is shown; hand focus to the row once they do.
    LaunchedEffect(entries.size) { onWantFocus() }

    val focused = entries.firstOrNull { it.key == tab.focusedKey } ?: entries.first()
    Column(modifier = modifier.fillMaxWidth()) {
        StreamHero(
            entry = focused,
            paired = paired,
            errors = tab.errors,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = screenPadding),
        )
        StreamRow(
            tab = tab,
            entries = entries,
            uniqueId = uniqueId,
            cardHeight = cardHeight,
            screenPadding = screenPadding,
            cardGap = cardGap,
            pendingFocusKey = pendingFocusKey,
            onFocusConsumed = onFocusConsumed,
        )
    }
}

@Composable
private fun StreamHero(entry: StreamEntry, paired: List<ComputerDetails>, errors: Map<String, String>, modifier: Modifier) {
    val pc = (entry as? StreamEntry.App)?.pc ?: paired.firstOrNull()
    Column(modifier = modifier, verticalArrangement = Arrangement.Bottom) {
        Text(
            text = when (entry) {
                is StreamEntry.App -> entry.app.appName.orEmpty()
                StreamEntry.AddPc -> stringResource(R.string.rift_stream_add_pc_title)
            }.uppercase(),
            color = RiftColors.OnSurface,
            fontSize = 54.sp,
            lineHeight = 56.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        Spacer(Modifier.height(12.dp))
        if (pc == null) {
            Text(stringResource(R.string.rift_stream_searching), color = RiftColors.OnSurfaceVariant, fontSize = 14.sp)
        } else {
            val online = PcStreaming.isOnline(pc)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(online)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${pc.name ?: "PC"} · " + stringResource(
                        when {
                            pc.state == ComputerDetails.State.UNKNOWN -> R.string.rift_stream_state_checking
                            online -> R.string.rift_stream_state_online
                            else -> R.string.rift_stream_state_offline
                        },
                    ),
                    color = RiftColors.OnSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            val note = when {
                errors[pc.uuid] != null -> stringResource(R.string.rift_stream_apps_error, errors[pc.uuid].orEmpty())
                pc.state == ComputerDetails.State.OFFLINE -> stringResource(R.string.rift_stream_offline_help)
                else -> null
            }
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(note, color = RiftColors.OnSurfaceVariant, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamRow(
    tab: PcStreamingTab,
    entries: List<StreamEntry>,
    uniqueId: String?,
    cardHeight: Dp,
    screenPadding: Dp,
    cardGap: Dp,
    pendingFocusKey: String?,
    onFocusConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loading = tab.paired.any { PcStreaming.isOnline(it) && tab.apps[it.uuid] == null && tab.errors[it.uuid] == null }
    LazyRow(
        state = tab.rowState,
        contentPadding = PaddingValues(start = screenPadding, end = screenPadding, top = 18.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(cardGap),
        verticalAlignment = Alignment.Top,
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            val onFocused = {
                tab.focusedKey = entry.key
                scope.launch { tab.rowState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                Unit
            }
            when (entry) {
                is StreamEntry.App -> {
                    val art by produceState<Bitmap?>(null, entry.key, uniqueId) {
                        if (uniqueId != null) value = loadBoxArt(context, entry.pc, entry.app, uniqueId)
                    }
                    CoverCard(
                        height = cardHeight,
                        paletteKey = entry.key + (if (art != null) ":art" else ""),
                        loadPalette = { art?.let(CoverColors::palette) },
                        requestFocus = pendingFocusKey == entry.key,
                        onFocusRequested = onFocusConsumed,
                        onFocused = onFocused,
                        onClick = { uniqueId?.let { PcStreaming.startStream(context, entry.pc, entry.app, it) } },
                        cover = {
                            Text(
                                text = entry.app.appName.orEmpty(),
                                color = RiftColors.OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                            )
                            art?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = entry.app.appName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        },
                    )
                }
                StreamEntry.AddPc -> CoverCard(
                    height = cardHeight,
                    paletteKey = ADD_PC_KEY,
                    loadPalette = { null },
                    requestFocus = pendingFocusKey == entry.key,
                    onFocusRequested = onFocusConsumed,
                    onFocused = onFocused,
                    onClick = { PcStreaming.openAddPc(context) },
                    cover = {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (loading) {
                                LoadingIndicator(color = RiftColors.Primary, modifier = Modifier.size(48.dp))
                            } else {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = RiftColors.OnSurface, modifier = Modifier.size(40.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.rift_stream_add_pc_title),
                                color = RiftColors.OnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Box art cache: the row recycles covers as it scrolls. */
private val boxArtCache = android.util.LruCache<String, Bitmap>(64)

private suspend fun loadBoxArt(context: Context, pc: ComputerDetails, app: NvApp, uniqueId: String): Bitmap? {
    val key = "${pc.uuid}:${app.appId}"
    boxArtCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            val http = NvHTTP(pc.activeAddress, pc.httpsPort, uniqueId, pc.serverCert, PlatformBinding.getCryptoProvider(context))
            http.getBoxArt(app).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.also { boxArtCache.put(key, it) }
    }
}

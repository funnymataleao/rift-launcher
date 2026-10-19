package dev.rift.launcher.stream

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entry points for PC streaming (embedded Artemis core).
 *
 * Paired PCs live in Artemis' own PC database inside Rift; a PC counts as paired once its server
 * certificate is pinned there.
 */
object PcStreaming {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val paired = MutableStateFlow<List<String>>(emptyList())
    private var pairedLoaded = false

    /** Opens the "Add PC" screen: PCs found on the network, manual IP, PIN pairing. */
    fun openAddPc(context: Context) {
        val intent = Intent(context, AddPcActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Names of paired PCs, read straight from the database (call off the main thread). */
    fun pairedHostNames(context: Context): List<String> {
        val db = ComputerDatabaseManager(context.applicationContext)
        return try {
            db.allComputers.filter { it.serverCert != null }.mapNotNull { it.name }.sorted()
        } finally {
            db.close()
        }
    }

    /** Paired PC names as a flow; the home screen shows the "PC streaming" tab while it is not empty. */
    fun pairedHosts(context: Context): StateFlow<List<String>> {
        if (!pairedLoaded) {
            pairedLoaded = true
            refreshPaired(context)
        }
        return paired.asStateFlow()
    }

    fun refreshPaired(context: Context) {
        val appContext = context.applicationContext
        scope.launch { paired.value = pairedHostNames(appContext) }
    }

    fun isPaired(pc: ComputerDetails) =
        pc.pairState == PairingManager.PairState.PAIRED || (pc.pairState == null && pc.serverCert != null)

    fun isOnline(pc: ComputerDetails) = pc.state == ComputerDetails.State.ONLINE && pc.activeAddress != null

    /**
     * Apps the host offers (Apollo/Sunshine apps.json); needs the PC online and paired. The list is kept
     * per PC, so the home still shows its games while the PC sleeps.
     */
    suspend fun loadApps(context: Context, pc: ComputerDetails, uniqueId: String): List<NvApp> = withContext(Dispatchers.IO) {
        val http = NvHTTP(pc.activeAddress, pc.httpsPort, uniqueId, pc.serverCert, PlatformBinding.getCryptoProvider(context))
        http.appList.also { apps ->
            Log.i("RiftStream", "apps of ${pc.name}: ${apps.joinToString { it.appName.orEmpty() }}")
            pc.uuid?.let { saveApps(context, it, apps) }
        }
    }

    /** The last app list each PC returned, keyed by host UUID (call off the main thread). */
    fun cachedApps(context: Context): Map<String, List<NvApp>> =
        appsPrefs(context).all.mapNotNull { (uuid, value) ->
            runCatching {
                val json = JSONArray(value as String)
                uuid to (0 until json.length()).map { i ->
                    val app = json.getJSONObject(i)
                    NvApp(app.getString("name"), app.optString("uuid").ifEmpty { null }, app.getInt("id"), app.optBoolean("hdr"))
                }
            }.getOrNull()
        }.toMap()

    private fun saveApps(context: Context, uuid: String, apps: List<NvApp>) {
        val json = JSONArray()
        apps.forEach { app ->
            json.put(
                JSONObject()
                    .put("name", app.appName)
                    .put("uuid", app.appUUID.orEmpty())
                    .put("id", app.appId)
                    .put("hdr", app.isHdrSupported),
            )
        }
        appsPrefs(context).edit().putString(uuid, json.toString()).apply()
    }

    private fun appsPrefs(context: Context) = context.applicationContext.getSharedPreferences("rift_stream_apps", Context.MODE_PRIVATE)

    /**
     * Starts the stream of [app] on [pc] with the user's stream settings. When polling has not marked the
     * PC online (just resumed, network change), its known addresses are asked directly first, so a PC
     * that is on is never reported offline.
     */
    fun startStream(context: Context, pc: ComputerDetails, app: NvApp, uniqueId: String, art: StreamArt = StreamArt()) {
        val address = pc.activeAddress
        if (isOnline(pc) && address != null) {
            launchStream(context, pc, address, pc.httpsPort, pc.vDisplaySupported && pc.vDisplayDriverReady, app, uniqueId, art)
            return
        }
        if (checking) return
        checking = true
        Log.i(TAG, "${pc.name} state=${pc.state} active=$address: checking its addresses before streaming")
        SnackbarManager.show(context.getString(R.string.rift_stream_connecting, pc.name))
        scope.launch {
            val reach = reach(context, pc, uniqueId)
            withContext(Dispatchers.Main) {
                checking = false
                when (reach) {
                    is Reach.Online -> launchStream(context, pc, reach.address, reach.httpsPort, reach.virtualDisplay, app, uniqueId, art)
                    is Reach.NoHttps -> SnackbarManager.show(context.getString(R.string.rift_stream_pc_no_https, pc.name, reach.httpsPort))
                    Reach.Offline -> SnackbarManager.show(context.getString(R.string.rift_stream_pc_offline, pc.name))
                }
            }
        }
    }

    @Volatile
    private var checking = false

    private sealed interface Reach {
        class Online(val address: ComputerDetails.AddressTuple, val httpsPort: Int, val virtualDisplay: Boolean) : Reach

        /** The host answers over plain HTTP but not on its HTTPS port, which streaming needs. */
        class NoHttps(val httpsPort: Int) : Reach

        object Offline : Reach
    }

    /** Asks each of [pc]'s addresses directly: over HTTPS as streaming does, then over HTTP to tell a dead PC from a stuck host. */
    private fun reach(context: Context, pc: ComputerDetails, uniqueId: String): Reach {
        val crypto = PlatformBinding.getCryptoProvider(context)
        var noHttps: Reach? = null
        for (address in listOfNotNull(pc.activeAddress, pc.localAddress, pc.manualAddress, pc.remoteAddress, pc.ipv6Address).distinct()) {
            runCatching { NvHTTP(address, 0, uniqueId, pc.serverCert, crypto).getComputerDetails(false) }
                .onSuccess { if (it.uuid == pc.uuid) return Reach.Online(address, it.httpsPort, it.vDisplaySupported && it.vDisplayDriverReady) }
                .onFailure { Log.i(TAG, "${pc.name} at $address over HTTPS: $it") }
            if (noHttps == null) {
                runCatching { NvHTTP(address, 0, uniqueId, null, crypto).getComputerDetails(false) }
                    .onSuccess {
                        if (it.uuid == pc.uuid) {
                            Log.w(TAG, "${pc.name} at $address answers over HTTP, HTTPS port ${it.httpsPort} does not")
                            noHttps = Reach.NoHttps(it.httpsPort)
                        }
                    }
                    .onFailure { Log.i(TAG, "${pc.name} at $address over HTTP: $it") }
            }
        }
        return noHttps ?: Reach.Offline
    }

    private fun launchStream(
        context: Context,
        pc: ComputerDetails,
        address: ComputerDetails.AddressTuple,
        httpsPort: Int,
        virtualDisplay: Boolean,
        app: NvApp,
        uniqueId: String,
        art: StreamArt,
    ) {
        context.startActivity(
            Intent(context, StreamActivity::class.java).apply {
                putExtra(StreamActivity.EXTRA_HOST, address.address)
                putExtra(StreamActivity.EXTRA_PORT, address.port)
                putExtra(StreamActivity.EXTRA_HTTPS_PORT, httpsPort)
                putExtra(StreamActivity.EXTRA_UNIQUE_ID, uniqueId)
                putExtra(StreamActivity.EXTRA_SERVER_CERT, pc.serverCert?.encoded)
                putExtra(StreamActivity.EXTRA_PC_NAME, pc.name)
                putExtra(StreamActivity.EXTRA_APP_NAME, app.appName)
                putExtra(StreamActivity.EXTRA_APP_ID, app.appId)
                putExtra(StreamActivity.EXTRA_APP_UUID, app.appUUID)
                putExtra(StreamActivity.EXTRA_APP_HDR, app.isHdrSupported)
                putExtra(StreamActivity.EXTRA_VIRTUAL_DISPLAY_READY, virtualDisplay)
                putExtra(StreamActivity.EXTRA_COVER_URLS, art.coverUrls.toTypedArray())
                putExtra(StreamActivity.EXTRA_HERO_URL, art.heroUrl)
                putExtra(StreamActivity.EXTRA_LOGO_URL, art.logoUrl)
                putExtra(StreamActivity.EXTRA_STEAM_APP_ID, art.steamAppId)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private const val TAG = "RiftStream"
}

/**
 * Live view of the PCs Artemis knows about or finds on the network (mDNS), while the screen is resumed.
 */
class PcHosts internal constructor() {
    /** Keyed by host UUID. */
    val hosts = mutableStateMapOf<String, ComputerDetails>()

    @Volatile
    var binder: ComputerManagerService.ComputerManagerBinder? = null
        internal set

    val uniqueId: String? get() = binder?.uniqueId

    internal val listener: ComputerManagerListener get() = PcPolling.listener
}

/**
 * The service has a single listener and a single polling switch, so every [PcHosts] on screen (the
 * home's tab, the streaming settings) shares them: polling stops only when the last one leaves.
 */
private object PcPolling {
    /** Touched on the main thread only. */
    private val watchers = mutableSetOf<PcHosts>()

    val listener = ComputerManagerListener { details ->
        val uuid = details.uuid ?: return@ComputerManagerListener
        // The service calls back from its polling threads.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            watchers.forEach { it.hosts[uuid] = details }
            PcHostsMemory.known = PcHostsMemory.known + (uuid to details)
        }
    }

    fun start(hosts: PcHosts, binder: ComputerManagerService.ComputerManagerBinder) {
        watchers += hosts
        binder.startPolling(listener)
    }

    fun stop(hosts: PcHosts, binder: ComputerManagerService.ComputerManagerBinder?) {
        if (watchers.remove(hosts) && watchers.isEmpty()) binder?.stopPolling()
    }
}

@Composable
fun rememberPcHosts(): PcHosts {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Rift: starts with the PCs this run already knew. Empty, the tab first showed only the Steam games, then the
    // PC's own games came in when the PC was found and pushed the row along (Hellraiser over Silent Hill).
    val pcHosts = remember { PcHosts().apply { hosts.putAll(PcHostsMemory.known) } }
    androidx.compose.runtime.SideEffect { if (pcHosts.hosts.isNotEmpty()) PcHostsMemory.known = pcHosts.hosts.toMap() }

    DisposableEffect(context, lifecycleOwner) {
        var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as ComputerManagerService.ComputerManagerBinder
                Thread {
                    binder.waitForReady()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        pcHosts.binder = binder
                        if (resumed) PcPolling.start(pcHosts, binder)
                    }
                }.start()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                pcHosts.binder = null
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    resumed = true
                    pcHosts.binder?.let { PcPolling.start(pcHosts, it) }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    PcPolling.stop(pcHosts, pcHosts.binder)
                }
                else -> Unit
            }
        }
        context.bindService(Intent(context, ComputerManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PcPolling.stop(pcHosts, pcHosts.binder)
            pcHosts.binder = null
            context.unbindService(connection)
        }
    }
    return pcHosts
}

/**
 * The game's pictures the stream shows: the wide cover in its panel, the hero and logo on the splash
 * while the game starts. [logoUrl] is a picked logo; [steamAppId] finds Steam's own.
 */
class StreamArt(
    val coverUrls: List<String> = emptyList(),
    val heroUrl: String = "",
    val logoUrl: String = "",
    val steamAppId: Int = 0,
)

/** The PCs seen this run, by UUID (see rememberPcHosts). */
internal object PcHostsMemory {
    @Volatile
    var known: Map<String, ComputerDetails> = emptyMap()
}

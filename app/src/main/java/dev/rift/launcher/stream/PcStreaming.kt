package dev.rift.launcher.stream

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    /** Apps the host offers (Apollo/Sunshine apps.json); needs the PC online and paired. */
    suspend fun loadApps(context: Context, pc: ComputerDetails, uniqueId: String): List<NvApp> = withContext(Dispatchers.IO) {
        val http = NvHTTP(pc.activeAddress, pc.httpsPort, uniqueId, pc.serverCert, PlatformBinding.getCryptoProvider(context))
        http.appList
    }

    /** Starts the stream of [app] on [pc] with the user's stream settings. */
    fun startStream(context: Context, pc: ComputerDetails, app: NvApp, uniqueId: String) {
        val address = pc.activeAddress ?: return
        context.startActivity(
            Intent(context, StreamActivity::class.java).apply {
                putExtra(StreamActivity.EXTRA_HOST, address.address)
                putExtra(StreamActivity.EXTRA_PORT, address.port)
                putExtra(StreamActivity.EXTRA_HTTPS_PORT, pc.httpsPort)
                putExtra(StreamActivity.EXTRA_UNIQUE_ID, uniqueId)
                putExtra(StreamActivity.EXTRA_SERVER_CERT, pc.serverCert?.encoded)
                putExtra(StreamActivity.EXTRA_PC_NAME, pc.name)
                putExtra(StreamActivity.EXTRA_APP_NAME, app.appName)
                putExtra(StreamActivity.EXTRA_APP_ID, app.appId)
                putExtra(StreamActivity.EXTRA_APP_UUID, app.appUUID)
                putExtra(StreamActivity.EXTRA_APP_HDR, app.isHdrSupported)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
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

    internal val listener = ComputerManagerListener { details ->
        val uuid = details.uuid ?: return@ComputerManagerListener
        // The service calls back from its polling threads.
        android.os.Handler(android.os.Looper.getMainLooper()).post { hosts[uuid] = details }
    }
}

@Composable
fun rememberPcHosts(): PcHosts {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pcHosts = remember { PcHosts() }

    DisposableEffect(context, lifecycleOwner) {
        var resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as ComputerManagerService.ComputerManagerBinder
                Thread {
                    binder.waitForReady()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        pcHosts.binder = binder
                        if (resumed) binder.startPolling(pcHosts.listener)
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
                    pcHosts.binder?.startPolling(pcHosts.listener)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    pcHosts.binder?.stopPolling()
                }
                else -> Unit
            }
        }
        context.bindService(Intent(context, ComputerManagerService::class.java), connection, Context.BIND_AUTO_CREATE)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (resumed) pcHosts.binder?.stopPolling()
            pcHosts.binder = null
            context.unbindService(connection)
        }
    }
    return pcHosts
}

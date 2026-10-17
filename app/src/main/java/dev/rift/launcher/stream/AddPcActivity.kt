@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.rift.launcher.stream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gamenative.R
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import dev.rift.launcher.ui.theme.RiftColors
import dev.rift.launcher.ui.theme.RiftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Add PC": PCs found on the network or entered by IP, then PIN pairing with Apollo / Vibepollo / Sunshine. */
class AddPcActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RiftTheme {
                AddPcScreen(onClose = ::finish)
            }
        }
    }
}

private sealed interface PairStep {
    data object Idle : PairStep
    data class Waiting(val pcName: String, val address: String, val pin: String) : PairStep
    data class Done(val pcName: String) : PairStep
    data class Failed(val message: String) : PairStep
}

@Composable
private fun AddPcScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pcHosts = rememberPcHosts()
    var step by remember { mutableStateOf<PairStep>(PairStep.Idle) }
    var askIp by remember { mutableStateOf(false) }
    var addingIp by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    fun pair(pc: ComputerDetails) {
        val binder = pcHosts.binder ?: return
        val address = pc.activeAddress
        if (!PcStreaming.isOnline(pc) || address == null) {
            step = PairStep.Failed(context.getString(R.string.rift_stream_pc_offline, pc.name))
            return
        }
        if (PcStreaming.isPaired(pc)) {
            step = PairStep.Done(pc.name)
            return
        }
        val pin = PairingManager.generatePinString()
        step = PairStep.Waiting(pc.name, address.address, pin)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    binder.stopPolling()
                    binder.waitForPollingStopped()
                    val http = NvHTTP(address, pc.httpsPort, binder.uniqueId, pc.serverCert, PlatformBinding.getCryptoProvider(context))
                    val pm = http.pairingManager
                    when (pm.pair(http.getServerInfo(true), pin, null)) {
                        PairingManager.PairState.PAIRED -> {
                            binder.getComputer(pc.uuid)?.serverCert = pm.pairedCert
                            binder.invalidateStateForComputer(pc.uuid)
                            null
                        }
                        PairingManager.PairState.PIN_WRONG -> context.getString(R.string.rift_stream_pair_wrong_pin)
                        PairingManager.PairState.ALREADY_IN_PROGRESS -> context.getString(R.string.rift_stream_pair_busy)
                        else -> context.getString(R.string.rift_stream_pair_failed)
                    }
                } catch (e: Exception) {
                    e.message ?: context.getString(R.string.rift_stream_pair_failed)
                } finally {
                    binder.startPolling(pcHosts.listener)
                }
            }
            if (result == null) {
                // The service saves the pinned certificate on its next poll; give it a moment.
                kotlinx.coroutines.delay(1500)
                PcStreaming.refreshPaired(context)
                step = PairStep.Done(pc.name)
            } else {
                step = PairStep.Failed(result)
            }
        }
    }

    fun addByIp(text: String) {
        val binder = pcHosts.binder ?: return
        addingIp = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val colon = text.lastIndexOf(':')
                val (host, port) = if (colon > 0 && text.indexOf(':') == colon) {
                    text.substring(0, colon) to (text.substring(colon + 1).toIntOrNull() ?: NvHTTP.DEFAULT_HTTP_PORT)
                } else {
                    text to NvHTTP.DEFAULT_HTTP_PORT
                }
                runCatching {
                    binder.addComputerBlocking(ComputerDetails().apply { manualAddress = ComputerDetails.AddressTuple(host, port) })
                }.getOrDefault(false)
            }
            addingIp = false
            if (!ok) step = PairStep.Failed(context.getString(R.string.rift_stream_ip_unreachable, text))
        }
    }

    val hosts = pcHosts.hosts.values.sortedWith(compareByDescending<ComputerDetails> { PcStreaming.isOnline(it) }.thenBy { it.name })

    Box(modifier = Modifier.fillMaxSize().background(RiftColors.Background)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusSurface(
                    onClick = onClose,
                    modifier = Modifier.size(52.dp),
                    shape = dev.rift.launcher.ui.theme.RiftShapes.full,
                    contentAlignment = Alignment.Center,
                ) { focused ->
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.rift_stream_back), tint = focusedContent(focused))
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(stringResource(R.string.rift_stream_add_pc_title), color = RiftColors.OnSurface, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.rift_stream_add_pc_subtitle), color = RiftColors.OnSurfaceVariant, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rift_stream_found_title), color = RiftColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                        if (pcHosts.binder != null) LoadingIndicator(color = RiftColors.Primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (hosts.isEmpty()) {
                            item {
                                Text(stringResource(R.string.rift_stream_searching), color = RiftColors.OnSurfaceVariant, fontSize = 18.sp)
                            }
                        }
                        items(hosts, key = { it.uuid }) { pc ->
                            HostRow(pc = pc, focusRequester = if (pc == hosts.first()) firstFocus else null, onClick = { pair(pc) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    StreamPillButton(
                        text = stringResource(if (addingIp) R.string.rift_stream_ip_adding else R.string.rift_stream_enter_ip),
                        onClick = { if (!addingIp) askIp = true },
                        focusRequester = if (hosts.isEmpty()) firstFocus else null,
                    )
                }
                GlassPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(targetState = step, label = "pairStep") { current ->
                        PairPanel(current, onClose = onClose, onRetry = { step = PairStep.Idle })
                    }
                }
            }
        }
    }

    LaunchedEffect(hosts.isEmpty()) { runCatching { firstFocus.requestFocus() } }

    if (askIp) {
        IpDialog(onDismiss = { askIp = false }, onConfirm = {
            askIp = false
            if (it.isNotBlank()) addByIp(it.trim())
        })
    }
}

@Composable
private fun HostRow(pc: ComputerDetails, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val online = PcStreaming.isOnline(pc)
    val paired = PcStreaming.isPaired(pc)
    FocusSurface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp), focusRequester = focusRequester) { focused ->
        val content = focusedContent(focused)
        val secondary = if (focused) RiftColors.OnPrimary.copy(alpha = 0.75f) else RiftColors.OnSurfaceVariant
        Row(modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Computer, contentDescription = null, tint = content, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pc.name ?: "PC", color = content, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online, focused = focused)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            pc.state == ComputerDetails.State.UNKNOWN -> stringResource(R.string.rift_stream_state_checking)
                            !online -> stringResource(R.string.rift_stream_state_offline)
                            paired -> stringResource(R.string.rift_stream_state_paired)
                            else -> stringResource(R.string.rift_stream_state_not_paired)
                        },
                        color = secondary,
                        fontSize = 16.sp,
                    )
                }
            }
            if (paired) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = if (focused) RiftColors.OnPrimary else RiftColors.Primary)
        }
    }
}

@Composable
private fun PairPanel(step: PairStep, onClose: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        when (step) {
            PairStep.Idle -> {
                Text(stringResource(R.string.rift_stream_how_title), color = RiftColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                listOf(R.string.rift_stream_how_1, R.string.rift_stream_how_2, R.string.rift_stream_how_3).forEachIndexed { i, res ->
                    Row(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("${i + 1}", color = RiftColors.Primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(36.dp))
                        Text(stringResource(res), color = RiftColors.OnSurface, fontSize = 19.sp)
                    }
                }
            }
            is PairStep.Waiting -> {
                Text(stringResource(R.string.rift_stream_pin_title, step.pcName), color = RiftColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(step.pin, color = RiftColors.Primary, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 16.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.rift_stream_pin_help, "https://${step.address}:47990"), color = RiftColors.OnSurfaceVariant, fontSize = 19.sp, lineHeight = 28.sp)
                Spacer(Modifier.height(24.dp))
                LoadingIndicator(color = RiftColors.Primary)
            }
            is PairStep.Done -> {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = RiftColors.Primary, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.rift_stream_paired_title, step.pcName), color = RiftColors.OnSurface, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.rift_stream_paired_help), color = RiftColors.OnSurfaceVariant, fontSize = 19.sp)
                Spacer(Modifier.height(28.dp))
                StreamPillButton(stringResource(R.string.rift_stream_done), onClick = onClose)
            }
            is PairStep.Failed -> {
                Text(stringResource(R.string.rift_stream_pair_error_title), color = RiftColors.Error, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(step.message, color = RiftColors.OnSurface, fontSize = 19.sp)
                Spacer(Modifier.height(28.dp))
                StreamPillButton(stringResource(R.string.rift_stream_retry), onClick = onRetry)
            }
        }
    }
}

@Composable
private fun IpDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RiftColors.SurfaceContainer,
        title = { Text(stringResource(R.string.rift_stream_ip_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RiftColors.OnSurface) },
        text = {
            // Two states like every Rift control: accent fill while typing, grey otherwise; no outline.
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("192.168.1.10") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = dev.rift.launcher.ui.theme.RiftShapes.card,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = RiftColors.Primary,
                    unfocusedContainerColor = RiftColors.SurfaceContainerHigh,
                    focusedTextColor = RiftColors.OnPrimary,
                    unfocusedTextColor = RiftColors.OnSurface,
                    focusedPlaceholderColor = RiftColors.OnPrimary.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = RiftColors.OnSurfaceVariant,
                    cursorColor = RiftColors.OnPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
            )
        },
        confirmButton = {
            StreamPillButton(stringResource(R.string.rift_stream_ip_add), onClick = { onConfirm(text) })
        },
        dismissButton = {
            StreamPillButton(stringResource(R.string.rift_stream_cancel), onClick = onDismiss)
        },
    )
}

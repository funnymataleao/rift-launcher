package dev.rift.launcher.settings

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.utils.ContainerUtils
import com.winlator.container.ContainerData
import com.winlator.core.AppUtils
import kotlinx.coroutines.delay

@Composable
internal fun rememberRiftPrefs(): RiftPrefs {
    val context = LocalContext.current
    return remember { RiftPrefs.of(context) }
}

/**
 * The settings new games start with, the same values GameNative's "default container configuration"
 * dialog edits. Every change is written straight back through [ContainerUtils.setDefaultContainerData],
 * so GameNative's own dialog and game pages see it.
 */
internal class ContainerDefaults(initial: ContainerData) {
    var data by mutableStateOf(initial)
        private set

    fun update(transform: (ContainerData) -> ContainerData) {
        data = transform(data)
        ContainerUtils.setDefaultContainerData(data)
    }

    /** Re-read after a dialog (GameNative's full configuration, the driver manager) changed them. */
    fun reload() {
        data = ContainerUtils.getDefaultContainerData()
    }
}

@Composable
internal fun rememberContainerDefaults(): ContainerDefaults =
    remember { ContainerDefaults(ContainerUtils.getDefaultContainerData()) }

/** Asks before a setting that needs the app to restart; the caller applies the change on confirm. */
@Composable
internal fun RestartConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RiftConfirmDialog(
        title = stringResource(R.string.settings_language_restart_title),
        message = stringResource(R.string.settings_language_restart_message),
        confirmText = stringResource(R.string.settings_language_restart_confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Shows a spinner, gives the preference a moment to be written, then restarts the app. */
@Composable
internal fun RestartingDialog(message: String) {
    val context = LocalContext.current
    RiftBusyDialog(message)
    LaunchedEffect(Unit) {
        delay(500)
        AppUtils.restartApplication(context)
    }
}

/** "primary:ROMs" in a document tree URI as a path people recognise, e.g. /storage/emulated/0/ROMs. */
internal fun folderLabel(uriString: String): String? {
    if (uriString.isBlank()) return null
    return runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val root = if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0" else "/storage/$volume"
        if (path.isEmpty()) root else "$root/$path"
    }.getOrNull() ?: uriString
}

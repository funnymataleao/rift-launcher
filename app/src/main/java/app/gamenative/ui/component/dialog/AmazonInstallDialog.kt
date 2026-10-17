package app.gamenative.ui.component.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.ui.component.LoadingScreen
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.GameDisplayInfo
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen install confirmation dialog for Amazon games.
 *
 * Mirrors the layout of [GameManagerDialog] used for Steam games so both platforms
 * present a consistent pre-install experience.
 */
@Composable
fun AmazonInstallDialog(
    visible: Boolean,
    displayInfo: GameDisplayInfo,
    downloadSize: String,
    installSize: String,
    availableSpace: String,
    installEnabled: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    // Rift: one install screen for every store (dev.rift.launcher.home.RiftInstallScreen)
    dev.rift.launcher.home.RiftInstallScreen(
        displayInfo = displayInfo,
        downloadSize = downloadSize,
        installSize = installSize,
        availableSpace = availableSpace,
        installEnabled = installEnabled,
        onInstall = onInstall,
        onDismiss = onDismiss,
        warning = if (installEnabled) null else stringResource(R.string.rift_install_not_enough_space),
    )
}

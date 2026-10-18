package app.gamenative.ui.component.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.R
import app.gamenative.data.EpicGame
import app.gamenative.service.epic.EpicConstants
import app.gamenative.service.epic.EpicService
import app.gamenative.ui.component.LoadingScreen
import app.gamenative.ui.component.dialog.InstallSizeInfo
import app.gamenative.ui.component.topbar.BackButton
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.utils.StorageUtils
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage
import dev.rift.launcher.home.RiftInstallItem
import dev.rift.launcher.home.RiftInstallScreen
import com.winlator.core.StringUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpicGameManagerDialog(
    visible: Boolean,
    onGetDisplayInfo: @Composable (Context) -> GameDisplayInfo,
    onInstall: (List<Int>) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    val allDownloadableGames = remember { mutableStateListOf<EpicGame>() }
    val selectedGameIds = remember { mutableStateMapOf<Int, Boolean>() }

    val displayInfo = onGetDisplayInfo(context)
    val gameId = displayInfo.gameId

    // Manifest sizes come from the network and can take long or fail; the list and the
    // Install button must not wait for them (Rift: install stayed disabled with 0 bytes).
    var sizesLoading by remember { mutableStateOf(false) }

    // No scroll reset here: with a gamepad the focus system scrolls the dialog as it opens, which
    // cancelled animateScrollTo and with it this whole load (list empty, 0 bytes, Install disabled).
    // Upstream fixed the same bug in GameManagerDialog for Steam.
    LaunchedEffect(visible) {
        Timber.tag("Epic").i("Game manager: opened for gameId=$gameId")
        allDownloadableGames.clear()
        selectedGameIds.clear()

        // Base game is always selected and can't be deselected
        val baseGame = EpicService.getEpicGameOf(gameId)
        if (baseGame == null) {
            Timber.tag("Epic").w("Game manager: no Epic game for gameId=$gameId")
        } else {
            allDownloadableGames.add(baseGame)
            selectedGameIds[baseGame.id ?: 0] = true
        }

        val dlcs = EpicService.getDLCForGame(gameId)
        dlcs.forEach { dlc ->
            allDownloadableGames.add(dlc)
            selectedGameIds[dlc.id ?: 0] = true
        }
        Timber.tag("Epic").i(
            "Game manager: ${allDownloadableGames.size} item(s) listed, stored sizes " +
                "download=${baseGame?.downloadSize} install=${baseGame?.installSize}",
        )

        // Fetch manifests for accurate sizes, updating each entry as it arrives
        sizesLoading = true
        try {
            allDownloadableGames.toList().forEach { game ->
                val id = game.id ?: return@forEach
                val sizes = EpicService.fetchManifestSizes(context, id)
                Timber.tag("Epic").i(
                    "Game manager: manifest sizes for ${game.appName}: download=${sizes.downloadSize} install=${sizes.installSize}",
                )
                if (sizes.downloadSize <= 0L && sizes.installSize <= 0L) return@forEach
                val index = allDownloadableGames.indexOfFirst { it.id == id }
                if (index >= 0) {
                    allDownloadableGames[index] = game.copy(
                        downloadSize = sizes.downloadSize,
                        installSize = sizes.installSize,
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.tag("Epic").w("Game manager: size loading cancelled for gameId=$gameId")
            throw e
        } finally {
            sizesLoading = false
        }
    }

    fun getInstallSizeInfo(): InstallSizeInfo {
        val installPath = EpicConstants.defaultEpicGamesPath(context)
        val availableBytes = try {
            StorageUtils.getAvailableSpace(installPath)
        } catch (e: Exception) {
            0L
        }

        val selectedGames = allDownloadableGames.filter {
            selectedGameIds[it.id ?: 0] == true
        }

        val totalDownloadBytes = selectedGames.sumOf { it.downloadSize }
        val totalInstallBytes = selectedGames.sumOf { it.installSize }

        return InstallSizeInfo(
            downloadSize = StringUtils.formatBytes(totalDownloadBytes),
            installSize = StringUtils.formatBytes(totalInstallBytes),
            availableSpace = StringUtils.formatBytes(availableBytes),
            installBytes = totalInstallBytes,
            availableBytes = availableBytes
        )
    }

    val installSizeInfo by remember(selectedGameIds.toMap(), allDownloadableGames.toList()) {
        derivedStateOf { getInstallSizeInfo() }
    }

    fun installButtonEnabled(): Boolean {
        // Check if there's enough space
        if (installSizeInfo.availableBytes < installSizeInfo.installBytes) {
            return false
        }

        // At least base game should be selected
        return selectedGameIds.filter { it.value }.isNotEmpty()
    }

    if (visible) {
        // Rift: one install screen for every store (dev.rift.launcher.home.RiftInstallScreen)
        val unknown = stringResource(
            if (sizesLoading) R.string.epic_install_size_calculating else R.string.epic_install_size_unknown,
        )
        val dlcLabel = stringResource(R.string.rift_install_dlc)
        val items = allDownloadableGames.map { game ->
            val gameIdValue = game.id ?: 0
            val isBaseGame = gameIdValue == gameId
            val sizes = if (game.installSize > 0L) {
                context.getString(
                    R.string.rift_install_item_sizes,
                    StringUtils.formatBytes(game.downloadSize),
                    StringUtils.formatBytes(game.installSize),
                )
            } else {
                unknown
            }
            RiftInstallItem(
                key = gameIdValue,
                title = game.title,
                subtitle = if (game.isDLC) "$dlcLabel • $sizes" else sizes,
                checked = selectedGameIds[gameIdValue] ?: false,
                enabled = !isBaseGame, // Base game can't be deselected
                onCheckedChange = { selectedGameIds[gameIdValue] = it },
            )
        }
        val sizesKnown = installSizeInfo.installBytes > 0L
        RiftInstallScreen(
            displayInfo = displayInfo,
            downloadSize = if (sizesKnown) installSizeInfo.downloadSize else unknown,
            installSize = if (sizesKnown) installSizeInfo.installSize else unknown,
            availableSpace = installSizeInfo.availableSpace,
            installEnabled = installButtonEnabled(),
            onInstall = {
                val selectedIds = selectedGameIds
                    .filter { it.value }
                    .keys
                    .toList()
                Timber.tag("Epic").i("Game manager: Install pressed for gameId=$gameId, items=$selectedIds")
                onInstall(selectedIds)
            },
            onDismiss = onDismissRequest,
            items = items,
            warning = if (installSizeInfo.availableBytes < installSizeInfo.installBytes) {
                stringResource(R.string.rift_install_not_enough_space)
            } else {
                null
            },
        )
    }
}

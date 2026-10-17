package dev.rift.launcher.cloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.gamenative.R
import app.gamenative.data.LibraryItem
import app.gamenative.data.StoreGameDetails
import app.gamenative.ui.data.DownloadDisplayDetails
import app.gamenative.ui.data.GameDisplayInfo
import app.gamenative.ui.screen.library.AppScreenContent
import dev.rift.launcher.home.LocalRiftStreamAction
import dev.rift.launcher.home.RiftStreamAction

/**
 * The game page of a cloud game: GameNative's own game page, filled from the Game Pass catalog, with
 * "Play in the cloud" as its main button. A cloud game has nothing on the device, so the page shows
 * no install, options or Wine compatibility.
 */
@Composable
internal fun CloudGamePage(item: LibraryItem, onPlay: () -> Unit, onBack: () -> Unit) {
    val game = remember(item.appId) { CloudLibrary.game(item.appId) }
    val displayInfo = remember(item, game) {
        GameDisplayInfo(
            name = game?.title ?: item.name,
            developer = game?.developer.orEmpty(),
            releaseDate = game?.releaseDate ?: 0L,
            heroImageUrl = (game?.hero ?: item.heroImageUrl).ifEmpty { null },
            iconUrl = null,
            gameId = 0,
            appId = item.appId,
            capsuleUrl = item.capsuleImageUrl.ifEmpty { null },
            headerUrl = item.headerImageUrl.ifEmpty { null },
            storeDetails = StoreGameDetails(
                description = game?.description.orEmpty(),
                tags = game?.categories.orEmpty(),
                screenshots = game?.screenshots.orEmpty(),
            ),
        )
    }
    val action = remember(onPlay) {
        RiftStreamAction(onStream = onPlay, labelRes = R.string.rift_cloud_play, icon = Icons.Default.Cloud, showLocalDetails = false)
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        CompositionLocalProvider(LocalRiftStreamAction provides action) {
            AppScreenContent(
                displayInfo = displayInfo,
                downloadDisplayDetails = DownloadDisplayDetails(
                    isInstalled = true,
                    isValidToDownload = false,
                    isDownloading = false,
                    downloadProgress = 0f,
                    hasPartialDownload = false,
                    isUpdatePending = false,
                ),
                onDownloadInstallClick = onPlay,
                onPauseResumeClick = {},
                onDeleteDownloadClick = {},
                onUpdateClick = {},
                onBack = onBack,
                optionsMenu = emptyList(),
            )
        }
    }
}

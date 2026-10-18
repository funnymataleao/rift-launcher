package dev.rift.launcher.cloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.data.FavoritesManager
import app.gamenative.ui.data.AppMenuOption
import app.gamenative.ui.enums.AppOptionMenuType
import app.gamenative.ui.screen.library.components.toggleFavorite
import dev.rift.launcher.meta.RiftAutoMeta
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
internal fun CloudGamePage(
    item: LibraryItem,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onChangeArt: () -> Unit,
    onRename: () -> Unit,
    onRefreshMeta: () -> Unit,
    onPickMeta: () -> Unit,
) {
    val context = LocalContext.current
    val favorites by FavoritesManager.favorites.collectAsStateWithLifecycle()
    val isFavorite = item.appId in favorites
    val metaVersion by RiftAutoMeta.version.collectAsStateWithLifecycle()
    // Game info the user chose by hand (or found elsewhere when the catalog has none) replaces the catalog's.
    val meta = remember(item.appId, metaVersion) { RiftAutoMeta.cached(context, item.appId) }
    LaunchedEffect(item.appId, item.name, metaVersion) { RiftAutoMeta.ensure(context, item, null) }
    val options = remember(isFavorite, item.appId, item.name, onChangeArt, onRename, onRefreshMeta, onPickMeta) {
        listOf(
            AppMenuOption(AppOptionMenuType.ChangeArt, onChangeArt),
            AppMenuOption(if (isFavorite) AppOptionMenuType.RemoveFromFavorites else AppOptionMenuType.AddToFavorites) {
                toggleFavorite(context, item.appId, item.name)
            },
            AppMenuOption(AppOptionMenuType.Rename, onRename),
            AppMenuOption(AppOptionMenuType.RefreshMeta, onRefreshMeta),
            AppMenuOption(AppOptionMenuType.ChooseMeta, onPickMeta),
        )
    }
    val game = remember(item.appId) { CloudLibrary.game(item.appId) }
    val displayInfo = remember(item, game, meta) {
        GameDisplayInfo(
            name = item.name,
            developer = meta?.developer?.ifBlank { null } ?: game?.developer.orEmpty(),
            releaseDate = meta?.releaseDate?.takeIf { it > 0 } ?: game?.releaseDate ?: 0L,
            heroImageUrl = (game?.hero ?: item.heroImageUrl).ifEmpty { null },
            iconUrl = null,
            gameId = 0,
            appId = item.appId,
            capsuleUrl = item.capsuleImageUrl.ifEmpty { null },
            headerUrl = item.headerImageUrl.ifEmpty { null },
            storeDetails = StoreGameDetails(
                description = meta?.description?.ifBlank { null } ?: game?.description.orEmpty(),
                reviewPercentage = meta?.rating,
                reviewCount = meta?.ratingCount,
                reviewSummary = meta?.ratingText?.ifBlank { null },
                tags = meta?.let { it.genres + it.tags }?.ifEmpty { null } ?: game?.categories.orEmpty(),
                screenshots = game?.screenshots.orEmpty(),
                source = meta?.takeIf { it.description.isNotBlank() }?.source.orEmpty(),
            ),
        )
    }
    val action = remember(onPlay) {
        RiftStreamAction(onStream = onPlay, labelRes = R.string.rift_cloud_play, icon = Icons.Default.Cloud, showLocalDetails = false, showOptions = true)
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
                optionsMenu = options,
            )
        }
    }
}

package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gamenative.PluviaApp
import app.gamenative.data.GameSource
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.events.AndroidEvent
import app.gamenative.service.SteamService
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.ui.data.CancelConfirmation
import app.gamenative.ui.data.DownloadItemState
import app.gamenative.ui.data.DownloadsState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val steamAppDao: SteamAppDao,
    private val epicGameDao: EpicGameDao,
    private val gogGameDao: GOGGameDao,
    private val amazonGameDao: AmazonGameDao,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    // Cache game metadata to avoid repeated DB lookups
    private val gameNameCache = ConcurrentHashMap<String, String>()
    private val gameIconCache = ConcurrentHashMap<String, String>()

    private val onDownloadStatusChanged: (AndroidEvent.DownloadStatusChanged) -> Unit = {
        viewModelScope.launch(Dispatchers.IO) { pollDownloads() }
    }

    init {
        PluviaApp.events.on<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)

        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                pollDownloads()
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        PluviaApp.events.off<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)
        super.onCleared()
    }

    private suspend fun pollDownloads() {
        try {
            val items = mutableMapOf<String, DownloadItemState>()

            // Steam downloads
            for ((appId, info) in SteamService.getActiveDownloads()) {
                val key = "${GameSource.STEAM}_$appId"
                val name = gameNameCache.getOrPut(key) {
                    steamAppDao.findApp(appId)?.name ?: "Steam App $appId"
                }
                val icon = gameIconCache.getOrPut(key) {
                    val app = steamAppDao.findApp(appId)
                    if (app != null && app.clientIconHash.isNotEmpty()) {
                        "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/apps/${app.id}/${app.clientIconHash}.ico"
                    } else {
                        ""
                    }
                }
                val (downloaded, total) = info.getBytesProgress()
                items[appId.toString()] = DownloadItemState(
                    appId = appId.toString(),
                    gameSource = GameSource.STEAM,
                    gameName = name,
                    iconUrl = icon,
                    progress = info.getProgress(),
                    bytesDownloaded = downloaded,
                    bytesTotal = total,
                    etaMs = info.getEstimatedTimeRemaining(),
                    statusMessage = info.getStatusMessageFlow().value,
                    isActive = info.isActive(),
                    isPartial = false
                )
            }

            for (appId in SteamService.getPartialDownloads()) {
                if (items.containsKey(appId.toString())) continue
                val key = "${GameSource.STEAM}_$appId"
                val name = gameNameCache.getOrPut(key) {
                    steamAppDao.findApp(appId)?.name ?: "Steam App $appId"
                }
                val icon = gameIconCache.getOrPut(key) {
                    val app = steamAppDao.findApp(appId)
                    if (app != null && app.clientIconHash.isNotEmpty()) {
                        "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/apps/${app.id}/${app.clientIconHash}.ico"
                    } else {
                        ""
                    }
                }
                items[appId.toString()] = DownloadItemState(
                    appId = appId.toString(),
                    gameSource = GameSource.STEAM,
                    gameName = name,
                    iconUrl = icon,
                    progress = null,
                    bytesDownloaded = null,
                    bytesTotal = null,
                    etaMs = null,
                    statusMessage = null,
                    isActive = null,
                    isPartial = true
                )
            }

            // Epic downloads
            for ((appId, info) in EpicService.getActiveDownloads()) {
                val key = "${GameSource.EPIC}_$appId"
                val name = gameNameCache.getOrPut(key) {
                    epicGameDao.getById(appId)?.title ?: "Epic Game $appId"
                }
                val icon = gameIconCache.getOrPut(key) {
                    epicGameDao.getById(appId)?.artCover ?: ""
                }
                val (downloaded, total) = info.getBytesProgress()
                items[appId.toString()] = DownloadItemState(
                    appId = appId.toString(),
                    gameSource = GameSource.EPIC,
                    gameName = name,
                    iconUrl = icon,
                    progress = info.getProgress(),
                    bytesDownloaded = downloaded,
                    bytesTotal = total,
                    etaMs = info.getEstimatedTimeRemaining(),
                    statusMessage = info.getStatusMessageFlow().value,
                    isActive = info.isActive(),
                    isPartial = false
                )
            }

            // GOG downloads
            for ((gameId, info) in GOGService.getActiveDownloads()) {
                val key = "${GameSource.GOG}_$gameId"
                val name = gameNameCache.getOrPut(key) {
                    gogGameDao.getById(gameId)?.title ?: "GOG Game $gameId"
                }
                val icon = gameIconCache.getOrPut(key) {
                    val game = gogGameDao.getById(gameId)
                    game?.imageUrl?.ifEmpty { game.iconUrl } ?: ""
                }
                val (downloaded, total) = info.getBytesProgress()
                items[gameId] = DownloadItemState(
                    appId = gameId,
                    gameSource = GameSource.GOG,
                    gameName = name,
                    iconUrl = icon,
                    progress = info.getProgress(),
                    bytesDownloaded = downloaded,
                    bytesTotal = total,
                    etaMs = info.getEstimatedTimeRemaining(),
                    statusMessage = info.getStatusMessageFlow().value,
                    isActive = info.isActive(),
                    isPartial = false
                )
            }

            // Amazon downloads
            for ((productId, info) in AmazonService.getActiveDownloads()) {
                val key = "${GameSource.AMAZON}_$productId"
                val name = gameNameCache.getOrPut(key) {
                    amazonGameDao.getByProductId(productId)?.title ?: "Amazon Game"
                }
                val icon = gameIconCache.getOrPut(key) {
                    amazonGameDao.getByProductId(productId)?.artUrl ?: ""
                }
                val (downloaded, total) = info.getBytesProgress()
                items[productId] = DownloadItemState(
                    appId = productId,
                    gameSource = GameSource.AMAZON,
                    gameName = name,
                    iconUrl = icon,
                    progress = info.getProgress(),
                    bytesDownloaded = downloaded,
                    bytesTotal = total,
                    etaMs = info.getEstimatedTimeRemaining(),
                    statusMessage = info.getStatusMessageFlow().value,
                    isActive = info.isActive(),
                    isPartial = false
                )
            }

            _state.update { it.copy(downloads = items) }
        } catch (e: Exception) {
            Timber.tag("DownloadsViewModel").e(e, "Error polling downloads")
        }
    }

    fun onPauseDownload(appId: String, gameSource: GameSource) {
        when (gameSource) {
            GameSource.STEAM -> {
                val id = appId.toIntOrNull() ?: return
                SteamService.getAppDownloadInfo(id)?.cancel()
            }
            GameSource.EPIC -> {
                val id = appId.toIntOrNull() ?: return
                EpicService.cancelDownload(id)
            }
            GameSource.GOG -> {
                GOGService.cancelDownload(appId)
            }
            GameSource.AMAZON -> {
                AmazonService.cancelDownload(appId)
            }
            else -> { /* no-op */ }
        }
    }

    fun onCancelDownload(appId: String, gameSource: GameSource) {
        // Find the game name from current state for the confirmation dialog
        val gameName = _state.value.downloads.values
            .find { it.appId == appId && it.gameSource == gameSource }
            ?.gameName ?: ""
        _state.update {
            it.copy(cancelConfirmation = CancelConfirmation(appId, gameSource, gameName))
        }
    }

    fun onDismissCancel() {
        _state.update { it.copy(cancelConfirmation = null) }
    }

    fun onConfirmCancel() {
        val confirmation = _state.value.cancelConfirmation ?: return
        _state.update { it.copy(cancelConfirmation = null) }

        when (confirmation.gameSource) {
            GameSource.STEAM -> {
                val id = confirmation.appId.toIntOrNull() ?: return
                SteamService.getAppDownloadInfo(id)?.cancel()
                viewModelScope.launch(Dispatchers.IO) {
                    SteamService.deleteApp(id)
                    PluviaApp.events.emit(AndroidEvent.LibraryInstallStatusChanged(id))
                    pollDownloads()
                }
            }
            GameSource.EPIC -> {
                val id = confirmation.appId.toIntOrNull() ?: return
                EpicService.cancelDownload(id)
                viewModelScope.launch(Dispatchers.IO) { pollDownloads() }
            }
            GameSource.GOG -> {
                GOGService.cancelDownload(confirmation.appId)
                viewModelScope.launch(Dispatchers.IO) { pollDownloads() }
            }
            GameSource.AMAZON -> {
                AmazonService.cancelDownload(confirmation.appId)
                viewModelScope.launch(Dispatchers.IO) { pollDownloads() }
            }
            else -> { /* no-op */ }
        }
    }
}

package app.gamenative.ui.data

import app.gamenative.data.GameSource

data class DownloadItemState(
    val appId: String,
    val gameSource: GameSource,
    val gameName: String,
    val iconUrl: String,
    val progress: Float?,
    val bytesDownloaded: Long?,
    val bytesTotal: Long?,
    val etaMs: Long?,
    val statusMessage: String?,
    val isActive: Boolean?,
    val isPartial: Boolean
)

data class CancelConfirmation(
    val appId: String,
    val gameSource: GameSource,
    val gameName: String,
)

data class DownloadsState(
    val downloads: Map<String, DownloadItemState> = emptyMap<String, DownloadItemState>(),
    val cancelConfirmation: CancelConfirmation? = null,
)

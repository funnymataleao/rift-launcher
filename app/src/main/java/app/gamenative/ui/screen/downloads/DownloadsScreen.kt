package app.gamenative.ui.screen.downloads

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gamenative.R
import androidx.compose.ui.layout.ContentScale
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.data.DownloadItemState
import app.gamenative.ui.model.DownloadsViewModel
import app.gamenative.ui.theme.PluviaTheme
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.coil.CoilImage

@Composable
fun HomeDownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.downloads.isEmpty()) {
            EmptyDownloadsContent()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            ) {
                items(
                    items = state.downloads.values.toList(),
                    key = { "${it.gameSource}_${it.appId}" },
                ) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { viewModel.onPauseDownload(item.appId, item.gameSource) },
                        onCancel = { viewModel.onCancelDownload(item.appId, item.gameSource) },
                    )
                }
            }
        }
    }

    // Cancel confirmation dialog
    val confirmation = state.cancelConfirmation
    MessageDialog(
        visible = confirmation != null,
        title = stringResource(R.string.cancel_download_prompt_title),
        message = confirmation?.gameName?.let {
            stringResource(R.string.downloads_cancel_confirm, it)
        },
        confirmBtnText = stringResource(R.string.yes),
        dismissBtnText = stringResource(R.string.no),
        onConfirmClick = { viewModel.onConfirmCancel() },
        onDismissClick = { viewModel.onDismissCancel() },
        onDismissRequest = { viewModel.onDismissCancel() },
    )
}

@Composable
private fun EmptyDownloadsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(R.string.downloads_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItemState,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Game icon
            CoilImage(
                imageModel = { item.iconUrl.ifEmpty { null } },
                imageOptions = ImageOptions(
                    contentScale = ContentScale.Crop,
                    contentDescription = item.gameName,
                ),
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Download info
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.gameName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.progress !== null) {
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = PluviaTheme.colors.statusDownloading,
                        )
                    }
                }

               if (item.progress !== null) {
                   Spacer(modifier = Modifier.height(6.dp))

                   LinearProgressIndicator(
                       progress = { item.progress.coerceIn(0f, 1f) },
                       modifier = Modifier
                           .fillMaxWidth()
                           .height(6.dp)
                           .clip(RoundedCornerShape(3.dp)),
                       color = PluviaTheme.colors.statusDownloading,
                       trackColor = MaterialTheme.colorScheme.surfaceVariant,
                   )
               }

                if (item.bytesTotal !== null && item.bytesDownloaded !== null) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Bytes progress
                        val bytesText = if (item.bytesTotal > 0) {
                            "${Formatter.formatFileSize(context, item.bytesDownloaded)} / ${Formatter.formatFileSize(context, item.bytesTotal)}"
                        } else {
                            Formatter.formatFileSize(context, item.bytesDownloaded)
                        }
                        Text(
                            text = bytesText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )

                        // ETA
                        val etaText = item.etaMs?.let { formatEta(it) }
                            ?: item.statusMessage
                        if (etaText != null) {
                            Text(
                                text = etaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                }

            Spacer(modifier = Modifier.width(8.dp))

            // Pause button
            IconButton(
                onClick = onPause,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.pause_download),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // Cancel button
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatEta(etaMs: Long): String {
    val totalSeconds = etaMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

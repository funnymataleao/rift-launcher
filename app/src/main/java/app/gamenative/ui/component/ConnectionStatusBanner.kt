package app.gamenative.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.enums.ConnectionState
import app.gamenative.ui.theme.PluviaTheme
import dev.rift.launcher.ui.blackGlass
import dev.rift.launcher.ui.theme.RiftAccent

// shared with SteamUtils.awaitSteamLogin so banner UI and intent-launch await fall back to offline together.
const val TIMEOUT_SHOW_OFFLINE_OPTION_SECONDS = 5

@Composable
fun ConnectionStatusBanner(
    connectionState: ConnectionState,
    connectionMessage: String?,
    timeoutSeconds: Int,
    onContinueOffline: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GraphicsLayer? = null,
) {
    val isVisible = connectionState == ConnectionState.CONNECTING ||
                    connectionState == ConnectionState.DISCONNECTED

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier
    ) {
        // Rift: floating black glass pill instead of the stock grey bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // One plate of black glass, nothing inside it but the row.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .blackGlass(MaterialTheme.shapes.large, backdrop = backdrop)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ConnectionIcon(connectionState)

                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTING -> {
                                connectionMessage ?: stringResource(R.string.connection_reconnecting)
                            }
                            ConnectionState.DISCONNECTED -> {
                                connectionMessage ?: stringResource(R.string.connection_disconnected)
                            }
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    if (connectionState == ConnectionState.CONNECTING && timeoutSeconds >= TIMEOUT_SHOW_OFFLINE_OPTION_SECONDS) {
                        Text(
                            text = "${timeoutSeconds}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    when (connectionState) {
                        ConnectionState.CONNECTING -> {
                            if (timeoutSeconds >= TIMEOUT_SHOW_OFFLINE_OPTION_SECONDS) {
                                AccentPill(
                                    text = stringResource(R.string.continue_offline),
                                    onClick = onContinueOffline,
                                )
                            }
                        }
                        ConnectionState.DISCONNECTED -> {
                            AccentPill(
                                text = stringResource(R.string.connection_retry),
                                onClick = onRetry,
                            )
                        }
                        else -> {}
                    }

                    // Dismiss button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The banner's one kind of button: the user's accent colour with dark text, round. */
@Composable
private fun AccentPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .background(RiftAccent.current.brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
        )
    }
}

@Composable
private fun ConnectionIcon(connectionState: ConnectionState) {
    val infiniteTransition = rememberInfiniteTransition(label = "connectionIcon")

    when (connectionState) {
        ConnectionState.CONNECTING -> {
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = RiftAccent.current.main,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation),
            )
        }
        ConnectionState.DISCONNECTED -> {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
        }
        else -> {}
    }
}

@Preview
@Composable
private fun Preview_ConnectionStatusBanner_Connecting() {
    PluviaTheme {
        ConnectionStatusBanner(
            connectionState = ConnectionState.CONNECTING,
            connectionMessage = "Reconnecting to Steam...",
            timeoutSeconds = 3,
            onContinueOffline = {},
            onRetry = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun Preview_ConnectionStatusBanner_Connecting_WithTimeout() {
    PluviaTheme {
        ConnectionStatusBanner(
            connectionState = ConnectionState.CONNECTING,
            connectionMessage = "Reconnecting to Steam...",
            timeoutSeconds = 8,
            onContinueOffline = {},
            onRetry = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun Preview_ConnectionStatusBanner_Disconnected() {
    PluviaTheme {
        ConnectionStatusBanner(
            connectionState = ConnectionState.DISCONNECTED,
            connectionMessage = "Connection lost",
            timeoutSeconds = 0,
            onContinueOffline = {},
            onRetry = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun Preview_ConnectionStatusBanner_Connected() {
    PluviaTheme {
        // Should not be visible
        ConnectionStatusBanner(
            connectionState = ConnectionState.CONNECTED,
            connectionMessage = null,
            timeoutSeconds = 0,
            onContinueOffline = {},
            onRetry = {},
            onDismiss = {},
        )
    }
}

package dev.rift.launcher.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gamenative.R
import app.gamenative.ui.screen.library.components.SystemMenuItem
import app.gamenative.ui.util.adaptivePanelWidth
import dev.rift.launcher.ui.theme.RiftGlassTheme

/**
 * The in-stream panel on the right, opened with Select + Start: the running game and "End stream",
 * which quits the game on the PC so the host lets go of its virtual display. B closes it.
 * Drawn like Rift's other side panels (GameNative's system menu in the glass theme).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StreamMenu(
    isOpen: Boolean,
    appName: String,
    pcName: String,
    onDismiss: () -> Unit,
    onEndStream: () -> Unit,
) = RiftGlassTheme {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit = slideOutHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val endFocus = remember { FocusRequester() }
            Surface(
                modifier = Modifier.width(adaptivePanelWidth(380.dp)).fillMaxHeight(),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pcName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    SystemMenuItem(
                        text = stringResource(R.string.rift_stream_end),
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = onEndStream,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = endFocus,
                    )
                }
            }
            LaunchedEffect(Unit) { runCatching { endFocus.requestFocus() } }
        }
    }
}

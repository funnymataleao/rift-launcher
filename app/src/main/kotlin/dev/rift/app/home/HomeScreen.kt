// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.app.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rift.app.R
import dev.rift.model.Game
import dev.rift.providers.LaunchResult
import dev.rift.ui.theme.RiftMotion
import dev.rift.ui.theme.RiftShapes

/**
 * Stage 0 home: proves the library and providers end to end with a focusable row of covers.
 * The Big Picture home (hero, shelves, side panels, button hints) replaces it in task 1.1.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPlay: (Game) -> Unit,
    onRefresh: () -> Unit,
) {
    var focused by remember { mutableStateOf<Game?>(null) }
    val selected = focused ?: state.games.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "RIFT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )

        if (state.games.isEmpty()) {
            EmptyLibrary(isRefreshing = state.isRefreshing, onRefresh = onRefresh)
            return@Column
        }

        selected?.let { game -> SelectedGame(game, onPlay) }
        state.launchProblem?.let { LaunchProblem(it) }

        val firstCard = remember { FocusRequester() }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(state.games, key = { it.id.value }) { game ->
                GameCard(
                    game = game,
                    onFocused = { focused = game },
                    onClick = { onPlay(game) },
                    modifier = if (game == state.games.first()) Modifier.focusRequester(firstCard) else Modifier,
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }
    }
}

@Composable
private fun SelectedGame(game: Game, onPlay: (Game) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(game.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onPlay(game) }, enabled = game.defaultTarget != null) {
                Text(stringResource(R.string.home_play), fontWeight = FontWeight.Bold)
            }
            Text(
                text = game.defaultTarget?.label ?: stringResource(R.string.home_no_source),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.07f else 1f, RiftMotion.focus(), label = "cardScale")
    LaunchedEffect(isFocused) { if (isFocused) onFocused() }

    Box(
        modifier = modifier
            .height(240.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                BorderStroke(3.dp, if (isFocused) Color.White else Color.Transparent),
                RiftShapes.card,
            )
            .background(MaterialTheme.colorScheme.surfaceContainer, RiftShapes.card)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        // Cover art arrives with metadata (task 1.5); the title stands in for it.
        Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyLibrary(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRefresh, enabled = !isRefreshing) {
            Text(stringResource(R.string.home_refresh))
        }
    }
}

@Composable
private fun LaunchProblem(result: LaunchResult) {
    val text = when (result) {
        is LaunchResult.NeedsSetup -> result.reason
        is LaunchResult.Failed -> stringResource(R.string.launch_failed, result.message)
        LaunchResult.Started -> return
    }
    Text(text, color = MaterialTheme.colorScheme.tertiary)
}

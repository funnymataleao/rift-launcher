package dev.rift.launcher.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import app.gamenative.R

/**
 * Start on the Rift home opens the focused game's options as a side panel over the home, Steam Deck
 * style. Rift reuses GameNative's own options panel and its game screen logic (the options' dialogs
 * live there); with this provided, GameNative's game screen draws only that panel and no page.
 * [panelOpen] mirrors the panel, so the home knows when it is closed.
 */
class RiftOptionsOnly {
    var panelOpen by mutableStateOf(true)
}

val LocalRiftOptionsOnly = staticCompositionLocalOf<RiftOptionsOnly?> { null }

/**
 * A game page opened from the PC streaming tab: its main button streams the game from the PC
 * instead of installing or playing it here, and the page's local-game actions are hidden.
 * The cloud tab uses it too, with its own [labelRes] and [icon]; a cloud game has no copy on the
 * device at all, so [showLocalDetails] off also hides the install status, size and Wine compatibility.
 */
class RiftStreamAction(
    @StringRes val labelRes: Int = R.string.rift_stream_play,
    val icon: ImageVector = Icons.Default.Cast,
    val showLocalDetails: Boolean = true,
    /** The page keeps its settings icon and "Properties" page (for native games on Rift's shared page). */
    val showOptions: Boolean = false,
    val onStream: () -> Unit,
)

val LocalRiftStreamAction = staticCompositionLocalOf<RiftStreamAction?> { null }

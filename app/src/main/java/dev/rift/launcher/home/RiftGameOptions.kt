package dev.rift.launcher.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

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
 */
class RiftStreamAction(val onStream: () -> Unit)

val LocalRiftStreamAction = staticCompositionLocalOf<RiftStreamAction?> { null }

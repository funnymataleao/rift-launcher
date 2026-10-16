package dev.rift.launcher.home

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Start on the Rift home opens the focused game's options, Steam Deck style. Rift reuses GameNative's
 * own options panel on its game page: the page opens with the panel already out when this is true.
 */
val LocalRiftOpenGameOptions = staticCompositionLocalOf { false }

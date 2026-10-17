package dev.rift.launcher.meta

import androidx.compose.runtime.staticCompositionLocalOf
import app.gamenative.data.LibraryItem

/**
 * What every game's menu does with its information and name, the same on every tab. The home provides it
 * where GameNative's own game screens are shown, so their option lists can offer the same lines as the
 * Android and emulator games' menus.
 */
class RiftInfoActions(
    val refresh: (LibraryItem) -> Unit,
    val choose: (LibraryItem) -> Unit,
    val rename: (LibraryItem) -> Unit,
)

val LocalRiftInfoActions = staticCompositionLocalOf<RiftInfoActions?> { null }

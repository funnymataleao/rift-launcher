package dev.rift.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rift.launcher.home.viewGap

/** Free space kept above and below the focused row, in every list of the settings. */
private val FocusMargin = 48.dp

/**
 * Put on the content of a settings scroll area (the section list, a page, a dialog list), after its scroll
 * modifier. Whatever inside takes focus, the area scrolls until that row shows whole with [FocusMargin] above
 * and below, not only until it touches the edge: the last row used to end flush with the screen edge, with
 * its focus enlargement cut off.
 */
@Composable
internal fun Modifier.settingsFocusMargin(): Modifier = viewGap(top = FocusMargin, bottom = FocusMargin)

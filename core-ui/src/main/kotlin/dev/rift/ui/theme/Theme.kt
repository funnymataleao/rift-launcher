// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * Rift's theme: Material 3 Expressive with the dark Big Picture palette.
 * Fonts (Unbounded, Onest) and the window-size type scale come with task 1.1.
 */
@Composable
fun RiftTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = RiftColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = RiftMaterialShapes,
        typography = Typography(),
        content = content,
    )
}

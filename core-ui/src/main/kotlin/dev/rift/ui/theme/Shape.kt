// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radii from design/DESIGN_TOKENS.md ("Формы").
object RiftShapes {
    val card = RoundedCornerShape(18.dp)
    val control = RoundedCornerShape(19.dp)
    val sheet = RoundedCornerShape(26.dp)
    val pill = RoundedCornerShape(percent = 50)
    val playOuter = 29.dp
    val playInner = 10.dp
}

internal val RiftMaterialShapes = Shapes(
    small = RiftShapes.card,
    medium = RiftShapes.control,
    large = RiftShapes.sheet,
    extraLarge = RoundedCornerShape(RiftShapes.playOuter),
)

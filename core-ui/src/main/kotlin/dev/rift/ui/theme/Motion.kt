// SPDX-License-Identifier: GPL-3.0-or-later
package dev.rift.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/** Spring presets from design/DESIGN_TOKENS.md ("Анимации"). Starting values, to be tuned on device. */
object RiftMotion {
    /** Cover focus scale and lift, Play and round button shape morphs, Play menu. */
    fun <T> focus(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 400f)

    /** Side panels sliding in. */
    fun <T> panel(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 300f)

    const val HERO_CROSSFADE_MS = 600
}

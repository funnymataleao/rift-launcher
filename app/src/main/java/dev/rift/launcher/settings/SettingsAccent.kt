package dev.rift.launcher.settings

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Soft warm tones for icons, as in macOS System Settings: each icon takes one colour and sits on a tonal
 * disc of the same colour. No pink or purple, and nothing as loud as the lemon accent.
 */
internal enum class SettingsAccent(val color: Color) {
    Blue(Color(0xFF8EB8F5)),
    Sky(Color(0xFF7CCBE8)),
    Teal(Color(0xFF6FD1C0)),
    Green(Color(0xFF9AD68B)),
    Sand(Color(0xFFE5C98F)),
    Peach(Color(0xFFF2A97E)),
    Yellow(Color(0xFFF0D36B)),
    Terracotta(Color(0xFFE3896B)),
}

/** The accent of the section being shown; rows take their icon colour from it. */
internal val LocalSettingsAccent = compositionLocalOf { SettingsAccent.Blue }

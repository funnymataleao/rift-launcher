package app.gamenative.ui.theme

import androidx.compose.ui.graphics.Color
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults

// Your custom color scheme
val customBackground = Color(0xFF000000)
val customForeground = Color(0xFFFAFAFA)
val customCard = Color(0xFF000000)
val customCardForeground = Color(0xFFFAFAFA)
val customPrimary = Color(0xFFF2F2F2)
val customPrimaryForeground = Color(0xFF000000)
val customSecondary = Color(0xFF27272A)
val customSecondaryForeground = Color(0xFFFAFAFA)
val customMuted = Color(0xFF27272A)
val customMutedForeground = Color(0xFF94969C)
val customAccent = Color(0xFFFFF44F)
val customAccentForeground = Color(0xFF000000)
val customDestructive = Color(0xFF7F1D1D)

val pluviaSeedColor = Color(0x284561FF)

/**
 * Raw color primitives for the Pluvia app.
 * These are the base colors used to construct theme palettes.
 */

// Brand (Rift: black with white controls instead of GameNative's magenta; lemon is only an accent, Denis 2026-10-16)
val PluviaPrimary = Color(0xFFF2F2F2)
val PluviaOnPrimary = Color(0xFF000000)
val PluviaSeed = Color(0x284561FF)

// Backgrounds
val PluviaBackground = Color(0xFF000000)
val PluviaSurface = Color(0xFF121212)
val PluviaSurfaceElevated = Color(0xFF1C1C1C)
val PluviaCard = Color(0xFF000000)

// Foregrounds
val PluviaForeground = Color(0xFFFAFAFA)
val PluviaForegroundMuted = Color(0xFF94969C)

// Secondary
val PluviaSecondary = Color(0xFF27272A)

// Accents
// Rift: cyan becomes the lemon accent; purple and pink become greys, so brand gradients stay quiet.
val PluviaCyan = Color(0xFFFFF44F)
val PluviaPurple = Color(0xFFBDBDBD)
val PluviaPink = Color(0xFFFFFFFF)

// Semantic
val PluviaSuccess = Color(0xFF10B981)
val PluviaWarning = Color(0xFFF59E0B)
val PluviaDanger = Color(0xFFEF4444)
val PluviaDestructive = Color(0xFF7F1D1D)

// Border
val PluviaBorder = Color(0xFF3A3A3A)

// Status - Installed/Download states
val StatusInstalled = Color(0xFF4CAF50)
val StatusDownloading = Color(0xFF00BCD4)
val StatusAvailable = Color(0xFF2196F3)
val StatusAway = Color(0xFFFF9800)
val StatusOffline = Color(0xFF9E9E9E)

// Friend states
val FriendOnline = Color(0xFF6DCFF6)
val FriendOffline = Color(0xFF7A7A7A)
val FriendInGame = Color(0xFF90BA3C)
val FriendAwayOrSnooze = Color(0x806DCFF6)
val FriendInGameAwayOrSnooze = Color(0x8090BA3C)
val FriendBlocked = Color(0xFF983D3D)

// Compatibility
val CompatibilityGood = Color(0xFF4CAF50)
val CompatibilityGoodBg = Color(0xFF1B5E20)
val CompatibilityPartial = Color(0xFF8BC34A)
val CompatibilityPartialBg = Color(0xFF33691E)
val CompatibilityUnknown = Color(0xFF9E9E9E)
val CompatibilityUnknownBg = Color(0xFF424242)
val CompatibilityBad = Color(0xFFEF5350)
val CompatibilityBadBg = Color(0xFFB71C1C)

package dev.rift.launcher.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.gamenative.ui.screen.library.components.SystemMenu
import dev.rift.launcher.ui.DrawerBackdrop

/**
 * GameNative's system menu (profile, settings, AI Help, downloads, Steam): Rift's right, quick access panel,
 * opened with the quick access button (see [RiftControls]). Store accounts live in Settings > Accounts.
 */
@Composable
fun RiftSystemMenu(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onDownloadsClick: () -> Unit,
    onLogout: () -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean,
    glassBackdrop: DrawerBackdrop? = null,
) {
    SystemMenu(
        isOpen = isOpen,
        onDismiss = onDismiss,
        onNavigateRoute = onNavigateRoute,
        onDownloadsClick = onDownloadsClick,
        onLogout = onLogout,
        onGoOnline = onGoOnline,
        isOffline = isOffline,
        panelShape = RectangleShape,
        panelShadowElevation = 0.dp,
        showCloseButton = false,
        showCommunityItems = false,
        showStoreAccounts = false,
        glassBackdrop = glassBackdrop,
    )
}

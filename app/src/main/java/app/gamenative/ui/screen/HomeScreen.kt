package app.gamenative.ui.screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import app.gamenative.ui.model.HomeViewModel
import app.gamenative.ui.screen.downloads.HomeDownloadsScreen
import app.gamenative.ui.theme.PluviaTheme
import dev.rift.launcher.home.RiftDownloadsNav
import dev.rift.launcher.home.RiftDownloadsScreen
import dev.rift.launcher.home.RiftHomeScreen
import dev.rift.launcher.settings.RiftSettingsSection

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onChat: (Long) -> Unit,
    onClickExit: () -> Unit,
    onClickPlay: (String, Boolean) -> Unit,
    onTestGraphics: (String) -> Unit,
    onPlayWithDiagnostics: (String) -> Unit,
    onAiDebugRun: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onGoOnline: () -> Unit,
    isOffline: Boolean = false,
    isSteamConnected: Boolean = false,
) {
    // Rift: our home replaces GameNative's library screen, which never opens in Rift. Only its downloads
    // screen is reachable (quick access panel), and Back from it returns to the Rift home.
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    // Rift: the storage manager still lives in GameNative's downloads screen; it opens from the Rift page.
    var showStorage by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        RiftDownloadsNav.requests.collect { showDownloads = true }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Keep RiftHomeScreen mounted while Downloads/Storage is on top. Besides preserving the exact tab,
        // row scroll and focused game, this keeps RiftMusicEffect alive: Downloads is part of the launcher,
        // not a navigation destination that should tear the home down.
        RiftHomeScreen(
            onClickPlay = onClickPlay,
            onTestGraphics = onTestGraphics,
            onPlayWithDiagnostics = onPlayWithDiagnostics,
            onAiDebugRun = onAiDebugRun,
            onOpenStores = {
                RiftSettingsSection.request(RiftSettingsSection.Accounts)
                onNavigateRoute(PluviaScreen.Settings.route)
            },
            onNavigateRoute = onNavigateRoute,
            onDownloadsClick = { showDownloads = true },
            onLogout = onLogout,
            onGoOnline = onGoOnline,
            onExit = onClickExit,
            isOffline = isOffline,
            isSteamConnected = isSteamConnected,
        )

        if (showDownloads && showStorage) {
            BackHandler { showStorage = false }
            HomeDownloadsScreen(
                onBack = { showStorage = false },
                onClickPlay = onClickPlay,
                onTestGraphics = onTestGraphics,
                onPlayWithDiagnostics = onPlayWithDiagnostics,
                onAiDebugRun = onAiDebugRun,
            )
        } else if (showDownloads) {
            BackHandler { showDownloads = false }
            RiftDownloadsScreen(onBack = { showDownloads = false }, onOpenStorage = { showStorage = true })
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:width=1080px,height=1920px,dpi=440,orientation=landscape",
)
@Composable
private fun Preview_HomeScreenContent() {
    PluviaTheme {
        HomeScreen(
            onChat = {},
            onClickPlay = { _, _ -> },
            onTestGraphics = { },
            onPlayWithDiagnostics = { },
            onAiDebugRun = { },
            onLogout = {},
            onNavigateRoute = {},
            onClickExit = {},
            onGoOnline = {},
        )
    }
}

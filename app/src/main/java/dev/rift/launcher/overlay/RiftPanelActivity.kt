package dev.rift.launcher.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.rift.launcher.ui.LocalWindowBlur
import dev.rift.launcher.ui.RiftDrawerPanel
import dev.rift.launcher.ui.layOutAsDrawer
import dev.rift.launcher.ui.theme.RiftGlassTheme

/**
 * Rift's panel over a game as a see-through screen of its own, for when the accessibility service is off
 * ([RiftGameHome]). It sits in a task of its own on top of the game: Back or a tap outside closes it and the
 * game is there again. Without the service there is no way to press the game's own menu from here, so that row is left out.
 */
class RiftPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val game = ExternalGameSession.packageName(this)
        if (game == null) {
            finish()
            return
        }
        var windowBlur by mutableStateOf(false)
        setFinishOnTouchOutside(true)
        window.layOutAsDrawer(this) { windowBlur = it }
        setContent {
            RiftGlassTheme {
                CompositionLocalProvider(LocalWindowBlur provides windowBlur) {
                    RiftDrawerPanel(modifier = Modifier.fillMaxSize()) {
                        OverlayPanelBody(
                            context = this@RiftPanelActivity,
                            gamePackage = game,
                            onBackToRift = {
                                ExternalGameSession.leftGame(this@RiftPanelActivity)
                                ExternalGameSession.openRift(this@RiftPanelActivity)
                                finish()
                            },
                            onCloseGame = {
                                ExternalGameSession.leftGame(this@RiftPanelActivity)
                                ExternalGameSession.openRift(this@RiftPanelActivity)
                                ExternalGameSession.closeInBackground(this@RiftPanelActivity, game)
                                finish()
                            },
                            onGameMenu = null,
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Another screen took over (Home, recents): the panel is not kept around.
        if (!isChangingConfigurations) finish()
    }
}

package dev.rift.launcher.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.gamenative.ui.component.GamepadButton

/** One tab of a library: a source of the home tab it belongs to (a store, later an emulator platform). */
data class LibraryTabSpec(val key: String, val title: String)

/**
 * Big Picture's "all games" screen, the same for every home tab: the tab's sources as glass tabs
 * between LB and RB, and below them whatever grid the tab shows ([content]). B goes back to the home.
 * Focus stays inside while it is open, except when [keepFocusInside] says another layer (the game
 * page, the options) is taking it.
 */
@Composable
internal fun RiftLibraryScreen(
    isOpen: Boolean,
    glass: GraphicsLayer,
    tabs: List<LibraryTabSpec>,
    selectedKey: String?,
    onSelectTab: (String) -> Unit,
    onDismiss: () -> Unit,
    keepFocusInside: () -> Boolean,
    onKeyDown: (Int) -> Boolean = { false },
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        BackHandler(enabled = isOpen) { onDismiss() }
        // Only a B pressed here closes the screen, not the release of one that closed the game page.
        var backDown by remember { mutableStateOf(false) }
        fun step(by: Int) {
            if (tabs.size < 2) return
            val index = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
            onSelectTab(tabs[(index + by).mod(tabs.size)].key)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .focusProperties { onExit = { if (keepFocusInside()) cancelFocusChange() } }
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    when (code) {
                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            if (event.type == KeyEventType.KeyDown) backDown = true
                            if (event.type == KeyEventType.KeyUp && backDown) {
                                backDown = false
                                onDismiss()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                            if (event.type == KeyEventType.KeyDown) step(if (code == KeyEvent.KEYCODE_BUTTON_L1) -1 else 1)
                            true
                        }
                        else -> event.type == KeyEventType.KeyDown && onKeyDown(code)
                    }
                },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BumperButton(GamepadButton.LB.iconRes, "LB") { step(-1) }
                    GlassTabs(
                        glass = glass,
                        tabs = tabs,
                        selected = tabs.firstOrNull { it.key == selectedKey },
                        title = { it.title },
                        onSelect = { onSelectTab(it.key) },
                    )
                    BumperButton(GamepadButton.RB.iconRes, "RB") { step(1) }
                    if (headerAction != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        headerAction()
                    }
                }
                Box(modifier = Modifier.weight(1f)) { content() }
            }
        }
    }
}

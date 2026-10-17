package dev.rift.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.rift.launcher.home.RiftControls

/**
 * Opens [OverlayPanel] over an Android game or emulator started from Rift ([ExternalGameSession]) when the
 * gamepad's Home button (or the quick access button set in Rift's settings) is pressed. Gamepad buttons are
 * only filtered while that game is on screen; everywhere else they go straight to the app.
 */
class RiftOverlayService : AccessibilityService() {
    private var panel: OverlayPanel? = null
    private var filtering = false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own panel, the status bar and the keyboard come and go over the game.
        if (pkg == packageName && panel != null) return
        if (pkg == SYSTEM_UI || pkg == inputMethodPackage()) return
        val inGame = pkg != packageName && pkg == ExternalGameSession.packageName(this)
        setFiltering(inGame)
        if (!inGame) hidePanel()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!filtering || !isOpenButton(event)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (panel == null) showPanel() else hidePanel()
        }
        return true
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hidePanel()
        super.onDestroy()
    }

    private fun isOpenButton(event: KeyEvent): Boolean {
        val code = event.keyCode
        val chosen = RiftControls.quickAccessKey(this)
        if (chosen != KeyEvent.KEYCODE_UNKNOWN && code == chosen) return true
        if (code != KeyEvent.KEYCODE_BUTTON_MODE && code != KeyEvent.KEYCODE_HOME) return false
        // Only the gamepad's Home: the tablet's own Home still leaves the game.
        val sources = InputDevice.getDevice(event.deviceId)?.sources ?: return false
        return sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }

    private fun showPanel() {
        val game = ExternalGameSession.packageName(this) ?: return
        panel = OverlayPanel(
            service = this,
            gamePackage = game,
            onBackToRift = {
                hidePanel()
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            },
            onGameMenu = {
                hidePanel()
                // Back reaches the game once the panel's window is gone: emulators open their own menu
                // (with Exit), most Android games their pause menu.
                Handler(Looper.getMainLooper()).postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, GAME_MENU_DELAY_MS)
            },
            onDismissed = { panel = null },
        ).also { it.show() }
    }

    private fun hidePanel() {
        panel?.dismiss()
        panel = null
    }

    private fun setFiltering(on: Boolean) {
        if (on == filtering) return
        val info = serviceInfo ?: return
        info.flags = if (on) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
        filtering = on
    }

    private fun inputMethodPackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val GAME_MENU_DELAY_MS = 250L
    }
}

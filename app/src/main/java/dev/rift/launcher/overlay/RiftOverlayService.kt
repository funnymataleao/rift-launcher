package dev.rift.launcher.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.rift.launcher.home.RiftControls

/**
 * Opens [OverlayPanel] over an Android game or emulator started from Rift ([ExternalGameSession]) when the
 * gamepad's Home button (or the quick access button set in Rift's settings) is pressed. Gamepad buttons are
 * only filtered while a game or emulator is on screen; everywhere else they go straight to the app.
 */
class RiftOverlayService : AccessibilityService() {
    private var panel: OverlayPanel? = null
    private var filtering = false

    // The game or emulator in front: the one Rift started, or any emulator or game app, also one opened without Rift.
    private var gameInFront: String? = null

    // The emulator's game screen in the session Rift started ([ExternalGameSession.startedAt]), once it has shown.
    private var watchedSession = 0L
    private var gameScreen: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Turned on while a game Rift started is on screen: no window change will tell, so listen from the start.
        val game = ExternalGameSession.packageName(this)
        if (game != null && ExternalGameSession.isOnScreen(this)) {
            gameInFront = game
            setFiltering(true)
        }
        Log.i(TAG, "connected, game on screen: $gameInFront")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own panel, the status bar and the keyboard come and go over the game.
        if (pkg == packageName && panel != null) return
        if (pkg == SYSTEM_UI || pkg == inputMethodPackage()) return
        if (backToRiftFromEmulatorMenu(pkg, event.className?.toString())) return
        val inGame = pkg != packageName && ExternalGameSession.isGameApp(this, pkg)
        if (inGame != filtering) Log.i(TAG, "in front: $pkg, game: $inGame")
        gameInFront = if (inGame) pkg else null
        setFiltering(inGame)
        if (!inGame) hidePanel()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!filtering || !isOpenButton(event)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.i(TAG, "${KeyEvent.keyCodeToString(event.keyCode)} over $gameInFront, panel ${if (panel == null) "opens" else "closes"}")
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
        val game = gameInFront ?: ExternalGameSession.packageName(this) ?: return
        panel = OverlayPanel(
            service = this,
            gamePackage = game,
            onBackToRift = {
                hidePanel()
                ExternalGameSession.leftGame(this)
                ExternalGameSession.openRift(this)
            },
            onCloseGame = {
                hidePanel()
                ExternalGameSession.leftGame(this)
                ExternalGameSession.openRift(this)
                ExternalGameSession.closeInBackground(this, game)
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

    /**
     * The game Rift started in an emulator was quit with the emulator's own menu, and the emulator went back to its
     * main screen: Rift comes back instead, as after "Close game" (Denis 2026-10-17). Only the emulator's main screen
     * (its launcher activity) counts, and only after its game screen was on: settings opened from a game stay.
     */
    private fun backToRiftFromEmulatorMenu(pkg: String, className: String?): Boolean {
        val session = ExternalGameSession.startedAt(this)
        if (session != watchedSession) {
            watchedSession = session
            gameScreen = null
        }
        if (className == null || pkg != ExternalGameSession.packageName(this) || !ExternalGameSession.isOnScreen(this)) return false
        if (!ExternalGameSession.isEmulator(this, pkg)) return false
        // A dialog or a popup of the emulator is not a screen.
        val screen = activityName(pkg, className) ?: return false
        val mainScreen = packageManager.getLaunchIntentForPackage(pkg)?.component?.let { activityName(pkg, it.className) }
        if (screen != mainScreen) {
            if (gameScreen == null) {
                gameScreen = screen
                Log.i(TAG, "game screen of $pkg: $screen")
            }
            return false
        }
        if (gameScreen == null) return false
        Log.i(TAG, "$pkg back on its main screen $screen after $gameScreen: back to Rift")
        gameScreen = null
        hidePanel()
        setFiltering(false)
        gameInFront = null
        ExternalGameSession.leftGame(this)
        ExternalGameSession.openRift(this)
        return true
    }

    /** The activity [className] of [pkg] stands for (an alias's target), or null when it is not an activity. */
    private fun activityName(pkg: String, className: String): String? =
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, className), 0) }.getOrNull()
            ?.let { it.targetActivity ?: it.name }

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
        const val TAG = "RiftOverlay"
        const val SYSTEM_UI = "com.android.systemui"
        const val GAME_MENU_DELAY_MS = 250L
    }
}

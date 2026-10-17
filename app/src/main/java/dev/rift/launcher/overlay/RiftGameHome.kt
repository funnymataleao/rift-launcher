package dev.rift.launcher.overlay

import android.app.Activity
import android.content.Intent

/**
 * Home with the accessibility service off. Rift is the home app, so the system answers Home with Rift's home
 * screen even while a game Rift started is on screen. When that happens this brings the game back to the front
 * and opens [RiftPanelActivity] over it, so Home opens the panel in the game rather than leaving it.
 * With the service on, the service takes the gamepad's Home itself and this stays out of the way.
 */
internal object RiftGameHome {
    /** Called with a Home intent that reached Rift's main screen. Returns true when it was turned into the panel. */
    fun intercept(activity: Activity): Boolean {
        if (!ExternalGameSession.isOnScreen(activity) || ExternalGameSession.isServiceEnabled(activity)) return false
        ExternalGameSession.homeTakenOver()
        if (!ExternalGameSession.resumeGame(activity)) return false
        activity.startActivity(
            Intent(activity, RiftPanelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        return true
    }
}

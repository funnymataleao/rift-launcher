package dev.rift.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * The gamepad's Home button always opens the right panel, on Rift's own screens and in every game (Denis
 * 2026-10-17). On the tablet Home is Android's Home key, which an app never gets as a key:
 * - on Rift's screens and in PC games Rift is the home app on top, so Home arrives as a new home intent
 *   (MainActivity → AndroidEvent.HomePressed);
 * - in a stream or in Cloud the home task is already in front, so Android only closes system dialogs with the
 *   reason "homekey"; [onHomeKey] turns that into the panel.
 * Android games and emulators have their own route (dev.rift.launcher.overlay).
 */
object RiftHomeKey {
    const val TAG = "RiftHome"

    /** Home was pressed on a screen of its own (Settings, say): the home screen opens its panel when it shows. */
    @Volatile
    var panelPending = false

    /** Calls [onHome] when Home is pressed while [activity] is on screen, from its creation to its end. */
    fun onHomeKey(activity: ComponentActivity, onHome: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra(EXTRA_REASON) != REASON_HOME_KEY) return
                if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                Log.i(TAG, "home key over ${activity.javaClass.simpleName}")
                onHome()
            }
        }
        @Suppress("DEPRECATION")
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        // Only the system sends this broadcast.
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    runCatching { activity.unregisterReceiver(receiver) }
                }
            },
        )
    }

    private const val EXTRA_REASON = "reason"
    private const val REASON_HOME_KEY = "homekey"
}

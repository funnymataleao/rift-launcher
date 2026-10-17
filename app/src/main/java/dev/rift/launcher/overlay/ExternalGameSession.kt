package dev.rift.launcher.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import dev.rift.launcher.home.AndroidGames

/**
 * The Android game or emulator Rift started last. While that app is on screen, [RiftOverlayService]
 * opens Rift's panel over it with the gamepad's Home button. Android only lets another app see those
 * buttons through an accessibility service, which the person turns on once in system settings.
 */
object ExternalGameSession {
    private const val KEY_PACKAGE = "overlay_game_package"

    fun started(context: Context, packageName: String) {
        AndroidGames.prefs(context).edit { putString(KEY_PACKAGE, packageName) }
    }

    fun packageName(context: Context): String? = AndroidGames.prefs(context).getString(KEY_PACKAGE, null)

    /** Whether the person has turned on Rift's service in Android's accessibility settings. */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, RiftOverlayService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun openServiceSettings(context: Context) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

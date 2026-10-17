package dev.rift.launcher.overlay

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import app.gamenative.R
import app.gamenative.ui.util.SnackbarManager
import dev.rift.launcher.home.AndroidGames
import dev.rift.launcher.settings.EmulatorCatalog

/**
 * The Android game or emulator Rift started last. While that app is on screen, [RiftOverlayService]
 * opens Rift's panel over it with the gamepad's Home button. Android only lets another app see those
 * buttons through an accessibility service, which the person turns on once in system settings.
 *
 * Without the service Home goes to the system, which opens Rift's home screen (Rift is the home app). So the
 * session also keeps how to get back into the game ([resumeGame]) and whether it was the last thing on screen
 * ([isOnScreen]); [RiftGameHome] uses both to open the panel over the game instead of the home screen.
 */
object ExternalGameSession {
    private const val KEY_PACKAGE = "overlay_game_package"
    private const val KEY_RESUME = "overlay_game_resume"
    private const val KEY_ON_SCREEN = "overlay_game_on_screen"
    private const val KEY_HINT_PENDING = "overlay_service_hint_pending"
    private const val KEY_HINT_SHOWN = "overlay_service_hint_shown"
    private const val HINT_EVERY_MS = 24L * 60 * 60 * 1000
    private const val HOME_GRACE_MS = 2500L

    // Android lets an app end another app's process only while that process is in the background.
    private val CLOSE_GAME_DELAYS_MS = longArrayOf(700L, 2000L)

    @Volatile
    private var interceptedAt = 0L

    fun started(context: Context, packageName: String) {
        AndroidGames.prefs(context).edit {
            putString(KEY_PACKAGE, packageName)
            remove(KEY_RESUME)
            putBoolean(KEY_ON_SCREEN, true)
            putBoolean(KEY_HINT_PENDING, true)
        }
    }

    /** Keeps [intent], the one that opened the game, so the game's task can be brought back to the front later. */
    fun setResumeIntent(context: Context, intent: Intent) {
        // Only the intent's identity counts for finding the running task; flags that restart the game are left out.
        val plain = Intent(intent).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            clipData = null
        }
        runCatching { AndroidGames.prefs(context).edit { putString(KEY_RESUME, plain.toUri(Intent.URI_INTENT_SCHEME)) } }
    }

    fun packageName(context: Context): String? = AndroidGames.prefs(context).getString(KEY_PACKAGE, null)

    /** Whether the game was the last thing the person had on screen, as far as Rift can tell. */
    fun isOnScreen(context: Context): Boolean =
        AndroidGames.prefs(context).getBoolean(KEY_ON_SCREEN, false) && packageName(context) != null

    /** The person is not in the game any more (back in Rift, or the game was closed). */
    fun leftGame(context: Context) {
        AndroidGames.prefs(context).edit { putBoolean(KEY_ON_SCREEN, false) }
    }

    /** Rift's own screen came to the front: the game is not on screen, unless Home was just taken over for the panel. */
    fun riftShown(context: Context) {
        if (SystemClock.elapsedRealtime() - interceptedAt <= HOME_GRACE_MS) return
        leftGame(context)
        hintIfServiceIsOff(context)
    }

    fun homeTakenOver() {
        interceptedAt = SystemClock.elapsedRealtime()
    }

    /** Brings the game's own task back to the front. Returns false when there is nothing to bring back. */
    fun resumeGame(context: Context): Boolean {
        val prefs = AndroidGames.prefs(context)
        val intent = prefs.getString(KEY_RESUME, null)?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: packageName(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            prefs.edit { putBoolean(KEY_ON_SCREEN, true) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Whether [packageName] is something Rift's panel belongs over: the last game Rift started, an emulator, or an app that is a game. */
    fun isGameApp(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return false
        if (packageName == packageName(context)) return true
        if (EmulatorCatalog.platforms.any { platform -> platform.emulators.any { it.matches(packageName) } }) return true
        if (packageName in AndroidGames.selectedPackages(context)) return true
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0
        }.getOrDefault(false)
    }

    /** Ends [gamePackage] once Rift is in front of it; a second try follows in case the first came before the game was in the background. */
    fun closeInBackground(context: Context, gamePackage: String) {
        val app = context.applicationContext
        val activities = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val handler = Handler(Looper.getMainLooper())
        for (delay in CLOSE_GAME_DELAYS_MS) {
            handler.postDelayed({ runCatching { activities.killBackgroundProcesses(gamePackage) } }, delay)
        }
    }

    /** Opens Rift's home screen. */
    fun openRift(context: Context) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

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

    /** With the service off the panel opens through Home taken over, which is less smooth: tell the person once a day how to turn it on. */
    private fun hintIfServiceIsOff(context: Context) {
        val prefs = AndroidGames.prefs(context)
        // Only after a game Rift started, and when the person is back in Rift to read it.
        if (!prefs.getBoolean(KEY_HINT_PENDING, false)) return
        prefs.edit { putBoolean(KEY_HINT_PENDING, false) }
        if (isServiceEnabled(context)) return
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_HINT_SHOWN, 0L) < HINT_EVERY_MS) return
        prefs.edit { putLong(KEY_HINT_SHOWN, now) }
        SnackbarManager.show(context.getString(R.string.rift_overlay_service_hint))
    }
}

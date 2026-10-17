package dev.rift.launcher.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

/** An installed Android app shown in the "Android games" section. */
data class AndroidGame(val packageName: String, val label: String, val isGame: Boolean = false, val installedAt: Long = 0L)

/**
 * Android games for the "Android games" section. Like every other section it only appears once the
 * user sets it up: the packages picked in Rift's settings are stored under [KEY_PACKAGES].
 * Listing apps needs the launcher intent in the manifest's queries on Android 11+.
 */
object AndroidGames {
    const val PREFS_NAME = "rift_prefs"
    const val KEY_PACKAGES = "android_games_packages"

    private const val ICON_SIZE = 256
    private val icons = LruCache<String, Bitmap>(160)

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selectedPackages(context: Context): Set<String> = prefs(context).getStringSet(KEY_PACKAGES, null).orEmpty()

    fun setSelectedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet(KEY_PACKAGES, packages) }
    }

    /** Every launchable app, games (by the system's category or the older flag) first, for the picker. */
    fun launchable(context: Context): List<AndroidGame> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                val isGame = info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0
                @Suppress("DEPRECATION")
                val installedAt = runCatching { pm.getPackageInfo(info.packageName, 0).firstInstallTime }.getOrDefault(0L)
                AndroidGame(info.packageName, pm.getApplicationLabel(info).toString(), isGame, installedAt)
            }
            .sortedWith(compareBy<AndroidGame> { !it.isGame }.thenBy { it.label.lowercase() })
            .also { cachedLaunchable = it }
    }

    /** The last [launchable] result, so the apps grid opens instantly; refreshed in the background. */
    @Volatile
    var cachedLaunchable: List<AndroidGame>? = null
        private set

    // --- When each app was last opened from Rift, for the apps grid's "recent" order ---
    private const val USAGE_PREFS = "rift_app_usage"

    fun lastOpened(context: Context): Map<String, Long> =
        context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    /** The games the user added in settings that are still installed. */
    fun selected(context: Context): List<AndroidGame> {
        val picked = selectedPackages(context)
        if (picked.isEmpty()) return emptyList()
        return launchable(context).filter { it.packageName in picked }.sortedBy { it.label.lowercase() }
    }

    fun iconCached(packageName: String): Bitmap? = icons.get(packageName)

    fun icon(context: Context, packageName: String): Bitmap? {
        icons.get(packageName)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null
        icons.put(packageName, bitmap)
        return bitmap
    }

    fun launch(context: Context, game: AndroidGame) {
        val intent = context.packageManager.getLaunchIntentForPackage(game.packageName) ?: return
        context.getSharedPreferences(USAGE_PREFS, Context.MODE_PRIVATE).edit { putLong(game.packageName, System.currentTimeMillis()) }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

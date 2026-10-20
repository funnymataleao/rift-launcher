package dev.rift.launcher.platform

import app.gamenative.PluviaApp
import com.winlator.xenvironment.XEnvironment
import com.winlator.xenvironment.components.GuestProgramLauncherComponent
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Steam GUI sometimes logs in and synchronizes a protected title but never dispatches the
 * command-line -applaunch from its initial startup. Diagnose this from Steam's own logs
 * and send ONE normal Steam launch request into the already-running client.
 *
 * The game is only started through the authorized Steam client, never by launching its EXE or
 * patching binaries. The observer does not alter saves, credentials, DRM, or installed files.
 */
internal object SteamGuiLaunchRecovery {
    private const val TAG = "SteamGuiLaunch"
    private const val FIRST_CHECK_MS = 23_000L
    private const val AFTER_RETRY_MS = 24_000L
    private const val WAIT_STEP_MS = 2_000L

    data class LogCursor(val cloudBytes: Long, val contentBytes: Long)

    fun capture(winePrefix: File): LogCursor {
        val logs = logsDir(winePrefix)
        return LogCursor(
            cloudBytes = File(logs, "cloud_log.txt").length(),
            contentBytes = File(logs, "content_log.txt").length(),
        )
    }

    internal fun gameWasDispatched(content: String, appId: Int): Boolean =
        content.contains("Game $appId adding PID") ||
            content.contains("AppID $appId state changed : Fully Installed,App Running")

    internal fun cloudReady(cloud: String, appId: Int): Boolean =
        cloud.contains("[AppID $appId] Successfully synced") ||
            cloud.contains("[AppID $appId] Download complete, result OK") ||
            cloud.contains("[AppID $appId] AutoCloud done")

    internal fun retryEligible(
        elapsedMs: Long, cloud: String, content: String, appId: Int,
    ): Boolean = elapsedMs >= FIRST_CHECK_MS &&
        cloudReady(cloud, appId) && !gameWasDispatched(content, appId)

    /**
     * Fire only on a GUI Steam game launch. The callback refuses to operate on old sessions.
     * A single Steam client launch request is never repeated in this session.
     */
    fun observe(
        scope: CoroutineScope,
        environment: XEnvironment,
        launcher: GuestProgramLauncherComponent,
        winePrefix: File,
        appId: Int,
        from: LogCursor,
    ) {
        if (appId <= 0) return
        scope.launch(Dispatchers.IO) {
            val logs = logsDir(winePrefix)
            val cloudLog = File(logs, "cloud_log.txt")
            val contentLog = File(logs, "content_log.txt")
            fun stillOpen() = PluviaApp.xEnvironment === environment
            var elapsed = 0L
            try {
                while (elapsed <= 65_000L) {
                    delay(WAIT_STEP_MS)
                    elapsed += WAIT_STEP_MS
                    if (!stillOpen()) return@launch
                    val content = appended(contentLog, from.contentBytes)
                    if (gameWasDispatched(content, appId)) {
                        Timber.tag(TAG).i("Steam dispatched appId=%d normally; no intervention", appId)
                        return@launch
                    }
                    val cloud = appended(cloudLog, from.cloudBytes)
                    if (!retryEligible(elapsed, cloud, content, appId)) continue

                    // A conflict in 241100 belongs to Steam Input, not the current title's saves.
                    // Never delete or overwrite cloud controller settings from the launcher.
                    if (controllerCloudConflict(cloud)) {
                        Timber.tag(TAG).w(
                            "Steam Controller Configs (241100) has a cloud conflict; its resolution may require Steam UI"
                        )
                    }
                    Timber.tag(TAG).w(
                        "Steam synced appId=%d but has not dispatched its EXE after %d ms; requesting via running Steam client",
                        appId, elapsed,
                    )
                    // The Steam URI handler did not dispatch games reliably under Wine.
                    // Reuse the official Steam EXE with -applaunch: the running client handles
                    // ownership, DRM and any required third-party sign-in. Never start game EXE.
                    val result = SteamClientCommand.applaunch(launcher, winePrefix, appId)
                    Timber.tag(TAG).i("Steam client launch request returned: %s", result.take(250))
                    delay(AFTER_RETRY_MS)
                    if (!stillOpen()) return@launch
                    val after = appended(contentLog, from.contentBytes)
                    if (gameWasDispatched(after, appId)) {
                        Timber.tag(TAG).i("Steam retry dispatched appId=%d", appId)
                    } else {
                        Timber.tag(TAG).e("Steam did not dispatch appId=%d after one recovery request; stopping without further retries", appId)
                    }
                    return@launch
                }
                Timber.tag(TAG).w("No Steam dispatch for appId=%d; cloud sync not yet confirmed, no retry", appId)
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Steam launch check failed for appId=%d (no more attempts)", appId)
            }
        }
    }

    internal fun controllerCloudConflict(cloud: String): Boolean =
        cloud.lineSequence().any {
            it.contains("[AppID 241100]") &&
                (it.contains("Sync conflict") || it.contains("Download failed due to conflicts"))
        }

    /** Safe AppID is numeric only: no user-controlled command fragments. */
    internal fun steamLaunchCommand(appId: Int): String {
        require(appId > 0)
        return """wine 'C:\Program Files (x86)\Steam\steam.exe' -applaunch $appId"""
    }

    private fun logsDir(prefix: File): File =
        File(prefix, "drive_c/Program Files (x86)/Steam/logs")

    private fun appended(file: File, offset: Long): String {
        if (!file.isFile) return ""
        return try {
        RandomAccessFile(file, "r").use { reader ->
            // Some Steam builds recreate logs at startup, so a shorter log starts from zero.
            reader.seek(offset.takeIf { it <= reader.length() } ?: 0L)
            val length = (reader.length() - reader.filePointer).coerceAtMost(256_000L).toInt()
            val bytes = ByteArray(length)
            reader.readFully(bytes)
            String(bytes, Charsets.UTF_8)
        }
        } catch (_: Exception) {
            ""
        }
    }
}

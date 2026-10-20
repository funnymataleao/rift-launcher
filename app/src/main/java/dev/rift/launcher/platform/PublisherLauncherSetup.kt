package dev.rift.launcher.platform

import android.content.Context
import app.gamenative.data.EpicGame
import app.gamenative.utils.UbisoftConnectStep
import com.winlator.container.Container
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Official publisher clients run under the game's Wine prefix, never inside an Android WebView.
 *
 * Epic's third-party-management metadata is the source of truth. Installing an official client
 * is not a login: only the publisher can authorize the user and issue a persistent session.
 * Do not copy Wine credentials or tokens between prefixes.
 */
object PublisherLauncherSetup {
    enum class Publisher { UBISOFT, EA, ROCKSTAR }

    private const val UBISOFT_INSTALLER_URL =
        "https://static3.cdn.ubi.com/orbit/launcher_installer/UbisoftConnectInstaller.exe"
    private const val UBISOFT_INSTALLER_NAME = "UbisoftConnectInstaller.exe"
    private const val MIN_INSTALLER_BYTES = 1_048_576L
    private const val MAX_INSTALLER_BYTES = 600L * 1024L * 1024L

    fun publisherForEpic(game: EpicGame): Publisher? {
        val managed = game.thirdPartyManagedApp.lowercase()
        return when {
            "ubisoft" in managed || "uplay" in managed ||
                game.appName.equals("Albacore", ignoreCase = true) -> Publisher.UBISOFT
            managed == "origin" || "ea app" in managed || game.isEAManaged -> Publisher.EA
            "rockstar" in managed -> Publisher.ROCKSTAR
            else -> null
        }
    }

    /**
     * Stage the official installer into the exact A: drive seen by Wine.
     *
     * Heroic also downloads this installer from Ubisoft's CDN and runs it in the game's prefix.
     * Rift's existing UbisoftConnectStep performs the Wine installation after this function.
     * The two other providers are deliberately not installed here: their Windows clients and
     * account-login flows need separate compatibility testing, not fabricated auth state.
     */
    suspend fun prepareForEpic(
        context: Context,
        game: EpicGame,
        container: Container,
        onProgress: (Float) -> Unit,
    ) {
        if (publisherForEpic(game) != Publisher.UBISOFT) return
        if (game.installPath.isBlank()) return
        if (UbisoftConnectStep.isInstalled(container)) return

        val gameDirectory = File(game.installPath)
        if (!gameDirectory.isDirectory) return
        if (UbisoftConnectStep.findInstaller(gameDirectory) != null) return

        withContext(Dispatchers.IO) {
            val cacheDir = File(context.filesDir, "publisher_installers")
            check(cacheDir.isDirectory || cacheDir.mkdirs()) {
                "Unable to prepare publisher installer cache"
            }
            val cachedInstaller = File(cacheDir, UBISOFT_INSTALLER_NAME)
            if (!isPlausibleInstaller(cachedInstaller)) {
                downloadOfficialUbisoftInstaller(cachedInstaller, onProgress)
            }
            check(isPlausibleInstaller(cachedInstaller)) {
                "The Ubisoft installer download is incomplete or invalid"
            }

            val destinationDir = File(gameDirectory, "_CommonRedist/UbisoftConnect")
            check(destinationDir.isDirectory || destinationDir.mkdirs()) {
                "Unable to create Ubisoft redistributable directory"
            }
            val destination = File(destinationDir, UBISOFT_INSTALLER_NAME)
            if (!destination.isFile) {
                val partial = File(destinationDir, "$UBISOFT_INSTALLER_NAME.partial")
                try {
                    cachedInstaller.copyTo(partial, overwrite = true)
                    check(isPlausibleInstaller(partial)) { "Ubisoft installer copy is incomplete" }
                    check(partial.renameTo(destination)) {
                        "Unable to finalize Ubisoft installer in the game directory"
                    }
                } finally {
                    partial.delete()
                }
            }
            Timber.tag("PublisherLauncher").i("Official Ubisoft installer staged for Epic game %s", game.appName)
        }
    }

    /** Basic corruption check, not an Authenticode signature verification. */
    internal fun isPlausibleInstaller(file: File): Boolean {
        if (!file.isFile || file.length() !in MIN_INSTALLER_BYTES..MAX_INSTALLER_BYTES) return false
        return runCatching {
            file.inputStream().use { input ->
                input.read() == 'M'.code && input.read() == 'Z'.code
            }
        }.getOrDefault(false)
    }

    private suspend fun downloadOfficialUbisoftInstaller(
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        val url = URL(UBISOFT_INSTALLER_URL)
        check(url.protocol == "https" && url.host == "static3.cdn.ubi.com")
        val partial = File(destination.parentFile, "${destination.name}.partial")
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            check(connection.responseCode == 200) {
                "Official Ubisoft installer download returned HTTP ${connection.responseCode}"
            }
            val expected = connection.contentLengthLong
            check(expected < 0L || expected in MIN_INSTALLER_BYTES..MAX_INSTALLER_BYTES) {
                "Unexpected Ubisoft installer size: $expected"
            }
            var received = 0L
            connection.inputStream.buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        received += read
                        check(received <= MAX_INSTALLER_BYTES) { "Ubisoft installer exceeds size limit" }
                        output.write(buffer, 0, read)
                        if (expected > 0) onProgress((received.toFloat() / expected).coerceIn(0f, 1f))
                    }
                }
            }
            check(expected <= 0 || received == expected) { "Ubisoft download is truncated" }
            check(isPlausibleInstaller(partial)) { "Invalid Ubisoft installer executable" }
            check(partial.renameTo(destination)) { "Unable to finish Ubisoft installer download" }
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }
}

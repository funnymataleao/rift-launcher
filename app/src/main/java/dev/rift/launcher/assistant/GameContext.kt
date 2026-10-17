package dev.rift.launcher.assistant

import android.content.Context
import app.gamenative.api.CommunityConfigService
import app.gamenative.api.CommunityConfigSort
import app.gamenative.filedetect.GameFileDetection
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.DebugReportUtils
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Everything the assistant knows about one game before it suggests anything. Built locally, no API key needed. */
@Serializable
data class GameContext(
    val appId: String,
    val gameName: String,
    val store: String,
    val settings: Map<String, String>,
    val configSource: String? = null,
    /** What GameNative's file scan found in the game folder: engine, anti-cheat, SDKs, launchers, exe bitness. */
    val detected: Map<String, String> = emptyMap(),
    val runs: RunSummary? = null,
    val lastChange: RunComparison? = null,
    /** Error lines from the last debug run's Wine log, if one was recorded. */
    val wineErrors: List<String> = emptyList(),
    val community: CommunityDigest? = null,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    private companion object {
        val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }
    }
}

object GameContextCollector {

    private const val LOG_TAIL_BYTES = 512L * 1024
    private const val MAX_WINE_ERRORS = 40
    private const val MAX_LINE_CHARS = 240

    /** Wine lines worth showing: its own error channel, unhandled exceptions and missing DLLs or imports. */
    private val WINE_ERROR = Regex(
        "^[0-9a-f]{4}:err:|^err:|Unhandled exception|unimplemented function|could not load|Library .* not found|" +
            "import_dll|page fault|EXCEPTION_",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Collects the game's context. [profile] decides which community runs count as this device; without it the
     * community part is skipped. Returns null when the game has no container yet.
     */
    suspend fun collect(context: Context, appId: String, profile: DeviceProfile?): GameContext? =
        withContext(Dispatchers.IO) {
            val container = try {
                if (!ContainerUtils.hasContainer(context, appId)) return@withContext null
                ContainerUtils.getContainer(context, appId)
            } catch (e: Exception) {
                Timber.w(e, "GameContext: no container for $appId")
                return@withContext null
            }
            val gameName = runCatching { ContainerUtils.resolveGameName(appId) }.getOrDefault(appId)
            val runs = RunHistory.read(RunHistory.runsDir(container))
            GameContext(
                appId = appId,
                gameName = gameName,
                store = runCatching { ContainerUtils.extractGameSourceFromContainerId(appId).name }.getOrDefault(""),
                settings = GameSettings.fromContainerData(ContainerUtils.toContainerData(container)),
                configSource = container.configSource.ifEmpty { null },
                detected = GameFileDetection.properties(container).mapValues { (_, v) ->
                    if (v is List<*>) v.joinToString(", ") else v.toString()
                },
                runs = runs.takeIf { it.isNotEmpty() }?.let(RunHistory::summarize),
                lastChange = RunHistory.compareLatest(runs),
                wineErrors = wineErrors(DebugReportUtils.wineLogFile(context, appId)),
                community = profile?.let { community(gameName, it) },
            )
        }

    /**
     * Community configs for this game the way "Browse configs" finds them: runs from this exact device plus runs
     * from devices with a compatible GPU, digested into a few groups. Null when the game is unknown there or the
     * service can't be reached.
     */
    suspend fun community(gameName: String, profile: DeviceProfile): CommunityDigest? {
        val service = CommunityConfigService.shared
        return try {
            val game = service.findGame(gameName) ?: return null
            val devices = DeviceProfiler.matchCommunityDevices(profile)
            val sameDevice = if (devices.isEmpty()) {
                emptyList()
            } else {
                service.fetchConfigs(
                    gameId = game.id,
                    gpu = null,
                    sort = CommunityConfigSort.HIGHEST_RATED,
                    page = 0,
                    limit = 50,
                    deviceIds = devices.map { it.id },
                ).runs
            }
            val compatible = service.fetchCompatibleConfigs(game.id, profile.gpu.renderer, CommunityConfigSort.HIGHEST_RATED, page = 0).runs
            CommunityDigester.digest(sameDevice + compatible, devices.map { it.id }.toSet())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "GameContext: community configs for $gameName failed")
            null
        }
    }

    fun wineErrors(log: File): List<String> {
        if (!log.isFile) return emptyList()
        return try {
            wineErrors(readTail(log, LOG_TAIL_BYTES).lineSequence())
        } catch (e: Exception) {
            Timber.w(e, "GameContext: reading ${log.name} failed")
            emptyList()
        }
    }

    /** Matching lines, deduplicated without the thread prefix, newest kept when there are too many. */
    internal fun wineErrors(lines: Sequence<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        for (line in lines) {
            if (!WINE_ERROR.containsMatchIn(line)) continue
            val trimmed = line.trim().take(MAX_LINE_CHARS)
            val key = trimmed.replaceFirst(Regex("^[0-9a-f]{4}:"), "")
            seen.remove(key)
            seen[key] = trimmed
        }
        return seen.values.toList().takeLast(MAX_WINE_ERRORS)
    }

    private fun readTail(file: File, bytes: Long): String = RandomAccessFile(file, "r").use { raf ->
        val start = (raf.length() - bytes).coerceAtLeast(0)
        raf.seek(start)
        val buffer = ByteArray((raf.length() - start).toInt())
        raf.readFully(buffer)
        String(buffer, Charsets.UTF_8).let { if (start > 0) it.substringAfter('\n') else it }
    }
}

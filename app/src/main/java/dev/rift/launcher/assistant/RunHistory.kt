package dev.rift.launcher.assistant

import android.content.Context
import app.gamenative.utils.ContainerUtils
import com.winlator.container.Container
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** One finished play session, read back from the record GameNative writes when a game exits. */
@Serializable
data class RunRecord(
    val endedAt: Long,
    val sessionSeconds: Long? = null,
    val avgFps: Double? = null,
    /** Window actually measured by the renderer. Steam/explorer FPS is not gameplay FPS. */
    val mainWindowClass: String? = null,
    val mainWindowActiveSeconds: Long? = null,
    val frameP50Ms: Double? = null,
    val frameP99Ms: Double? = null,
    val fpsBy5Min: List<Int> = emptyList(),
    val thermalPeak: Int? = null,
    val timeToThrottleSeconds: Long? = null,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val exitReason: String? = null,
    val configSource: String? = null,
    val settings: Map<String, String> = emptyMap(),
) {
    /** Only use measured FPS if a non-launcher game window held focus during the session. */
    val hasGameWindowEvidence: Boolean
        get() {
            val name = mainWindowClass?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
            if (name in setOf("steam.exe", "steamwebhelper.exe", "explorer.exe", "wine.exe", "wfm.exe")) return false
            return (mainWindowActiveSeconds ?: 0L) >= 15L
        }

    /** The game closed by itself within a minute: most often a crash on start. */
    val endedEarly: Boolean
        get() = exitReason == "guest_terminated" && (sessionSeconds ?: 0) < EARLY_EXIT_SECONDS

    companion object {
        const val EARLY_EXIT_SECONDS = 60L
    }
}

/** Typical numbers for a group of runs played with the same settings. */
@Serializable
data class RunSummary(
    val runs: Int,
    val medianFps: Double? = null,
    val medianFrameP99Ms: Double? = null,
    val earlyExits: Int = 0,
    val throttledRuns: Int = 0,
    val medianSecondsToThrottle: Long? = null,
    val settings: Map<String, String> = emptyMap(),
)

/**
 * The latest settings next to the ones played before them. [fpsChangePercent] and [frameP99ChangePercent]
 * are relative to [before]; positive FPS is better, negative frame time is better.
 */
@Serializable
data class RunComparison(
    val before: RunSummary,
    val after: RunSummary,
    val changes: List<GameSettings.Change>,
    val fpsChangePercent: Double? = null,
    val frameP99ChangePercent: Double? = null,
)

/**
 * Reads the JSON files in `<container>/.gamenative/runs` (written by GameNative's SessionReport, at most 30 kept) and turns it
 * into numbers the assistant can reason about: what the game does on these settings, and whether the last change
 * helped.
 */
object RunHistory {

    private const val RUNS_DIR = ".gamenative/runs"

    /** Sessions shorter than this say little about performance and are left out of FPS medians. */
    private const val MIN_MEASURED_SECONDS = 120L

    /** Android's THERMAL_STATUS_MODERATE: from here the system starts limiting clocks. */
    private const val THERMAL_THROTTLING = 2

    fun runsDir(container: Container): File = File(container.rootDir, RUNS_DIR)

    suspend fun load(context: Context, appId: String): List<RunRecord> = withContext(Dispatchers.IO) {
        try {
            if (!ContainerUtils.hasContainer(context, appId)) return@withContext emptyList()
            read(runsDir(ContainerUtils.getContainer(context, appId)))
        } catch (e: Exception) {
            Timber.w(e, "RunHistory: reading runs for $appId failed")
            emptyList()
        }
    }

    /** Runs in [dir], oldest first. Unreadable files are skipped. */
    fun read(dir: File): List<RunRecord> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { file -> runCatching { parse(file.name, file.readText()) }.getOrNull() }
            ?.sortedBy { it.endedAt }
            .orEmpty()

    fun parse(fileName: String, text: String): RunRecord? {
        val json = JSONObject(text)
        val endedAt = fileName.removeSuffix(".json").toLongOrNull() ?: return null
        val config = when (val value = json.opt("container_config")) {
            is JSONObject -> value
            is String -> runCatching { JSONObject(value) }.getOrNull()
            else -> null
        }
        return RunRecord(
            endedAt = endedAt,
            sessionSeconds = json.longOrNull("session_length"),
            avgFps = json.doubleOrNull("avg_fps")?.takeIf { it > 0 },
            mainWindowClass = json.optString("main_window_class").ifEmpty { null },
            mainWindowActiveSeconds = json.longOrNull("main_window_active_seconds"),
            frameP50Ms = json.doubleOrNull("frame_p50_ms"),
            frameP99Ms = json.doubleOrNull("frame_p99_ms"),
            fpsBy5Min = json.optJSONArray("fps_by_5min").ints(),
            thermalPeak = json.longOrNull("thermal_peak")?.toInt(),
            timeToThrottleSeconds = json.longOrNull("time_to_throttle_s"),
            batteryStartPct = json.longOrNull("battery_start_pct")?.toInt(),
            batteryEndPct = json.longOrNull("battery_end_pct")?.toInt(),
            exitReason = json.optString("exit_reason").ifEmpty { null },
            configSource = json.optString("config_source").ifEmpty { null },
            settings = config?.let(GameSettings::fromJson).orEmpty(),
        )
    }

    fun summarize(runs: List<RunRecord>): RunSummary {
        val measured = runs.filter {
            (it.sessionSeconds ?: 0) >= MIN_MEASURED_SECONDS && it.hasGameWindowEvidence
        }
        val throttled = runs.filter { (it.thermalPeak ?: 0) >= THERMAL_THROTTLING }
        return RunSummary(
            runs = runs.size,
            medianFps = median(measured.mapNotNull { it.avgFps }),
            medianFrameP99Ms = median(measured.mapNotNull { it.frameP99Ms }),
            earlyExits = runs.count { it.endedEarly },
            throttledRuns = throttled.size,
            medianSecondsToThrottle = median(throttled.mapNotNull { it.timeToThrottleSeconds?.toDouble() })?.toLong(),
            settings = runs.lastOrNull()?.settings.orEmpty(),
        )
    }

    /**
     * Compares the newest run of settings with the run of settings before it. Runs are grouped by consecutive
     * equal settings, so going back and forth between two configs compares the two latest stretches.
     * Returns null until there are two different settings to compare.
     */
    fun compareLatest(runs: List<RunRecord>): RunComparison? {
        val stretches = stretches(runs.filter { it.settings.isNotEmpty() })
        if (stretches.size < 2) return null
        val before = summarize(stretches[stretches.size - 2])
        val after = summarize(stretches.last())
        return RunComparison(
            before = before,
            after = after,
            changes = GameSettings.diff(before.settings, after.settings),
            fpsChangePercent = percentChange(before.medianFps, after.medianFps),
            frameP99ChangePercent = percentChange(before.medianFrameP99Ms, after.medianFrameP99Ms),
        )
    }

    /** Splits runs into stretches of consecutive runs that share settings (the executable path does not count). */
    fun stretches(runs: List<RunRecord>): List<List<RunRecord>> {
        val result = ArrayList<MutableList<RunRecord>>()
        for (run in runs) {
            val last = result.lastOrNull()
            if (last != null && sameSettings(last.last(), run)) last.add(run) else result.add(mutableListOf(run))
        }
        return result
    }

    private fun sameSettings(a: RunRecord, b: RunRecord): Boolean =
        GameSettings.diff(a.settings, b.settings).all { it.key == "executablePath" }

    internal fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun percentChange(before: Double?, after: Double?): Double? {
        if (before == null || after == null || before <= 0) return null
        return Math.round((after - before) / before * 1000) / 10.0
    }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else (opt(key) as? Number)?.toLong() ?: optString(key).toLongOrNull()

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }

    private fun JSONArray?.ints(): List<Int> =
        if (this == null) emptyList() else List(length()) { optInt(it) }
}

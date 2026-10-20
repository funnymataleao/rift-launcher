package dev.rift.launcher.assistant.optimize

import dev.rift.launcher.assistant.GameSettings
import dev.rift.launcher.assistant.RunHistory
import dev.rift.launcher.assistant.RunRecord
import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * Evidence from finished runs, NOT a benchmark. Different scenes, resolutions, and game versions
 * are not necessarily comparable, so no profile is automatically applied from these numbers.
 */
@Serializable
data class AutoTuneEvidence(
    val profiles: List<Profile>,
    val recommendedFromHistory: Int? = null,
    val notes: List<String> = emptyList(),
) {
    @Serializable
    data class Profile(
        val settings: Map<String, String>,
        val totalRuns: Int,
        val measuredRuns: Int,
        val medianFps: Double? = null,
        val medianP99Ms: Double? = null,
        val earlyExits: Int,
        val thermallyLimitedRuns: Int,
        /** ready: at least two reasonable runs with no early exit or thermal limit. */
        val readiness: String,
        val isCurrent: Boolean,
    )
}

/**
 * Phase 1 of Rift AutoTune: conservative, reproducible analysis of local sessions.
 * It never changes Wine, Proton, drivers, game files or the current container.
 */
object AutoTuneAnalyzer {
    private const val MIN_SESSION_SECONDS = 120L
    private const val THERMAL_MODERATE = 2
    private const val MAX_PROFILES = 5

    fun analyze(runs: List<RunRecord>, currentSettings: Map<String, String>): AutoTuneEvidence {
        val grouped = runs.asSequence()
            .filter { it.settings.isNotEmpty() }
            .groupBy { signature(it.settings) }
            .values
            .map { group ->
                val measured = group.filter {
                    (it.sessionSeconds ?: 0L) >= MIN_SESSION_SECONDS &&
                        !it.endedEarly && it.hasGameWindowEvidence &&
                        (it.thermalPeak ?: 0) < THERMAL_MODERATE &&
                        it.avgFps != null && it.avgFps.isFinite() && it.avgFps > 0.0
                }
                val early = group.count { it.endedEarly }
                val thermal = group.count { (it.thermalPeak ?: 0) >= THERMAL_MODERATE }
                val currentRuntime = signature(currentSettings)
                val isCurrent = currentRuntime.isNotEmpty() &&
                    currentRuntime.all { (key, value) -> group.last().settings[key] == value }
                AutoTuneEvidence.Profile(
                    settings = group.last().settings.filterKeys { it in GameSettings.RUNTIME_KEYS },
                    totalRuns = group.size,
                    measuredRuns = measured.size,
                    medianFps = RunHistory.median(measured.mapNotNull { it.avgFps })?.let(::round1),
                    medianP99Ms = RunHistory.median(measured.mapNotNull { it.frameP99Ms?.takeIf { v -> v > 0 && v.isFinite() } })?.let(::round1),
                    earlyExits = early,
                    thermallyLimitedRuns = thermal,
                    readiness = when {
                        measured.size >= 2 && early == 0 && thermal == 0 -> "repeatable"
                        measured.isNotEmpty() -> "preliminary"
                        else -> "insufficient"
                    },
                    isCurrent = isCurrent,
                )
            }
            .sortedWith(compareByDescending<AutoTuneEvidence.Profile> { it.isCurrent }
                .thenByDescending { it.measuredRuns }
                .thenByDescending { it.totalRuns })
            .take(MAX_PROFILES)

        val candidates = grouped.filter { it.readiness == "repeatable" && it.medianFps != null }
        // Prefer adequate frame pacing and fewer slow frames over headline FPS. No candidate from
        // one run, a thermally limited session or a failed boot can be called "best".
        val best = candidates.maxByOrNull { p ->
            val fps = (p.medianFps ?: 0.0).coerceAtMost(120.0)
            val slowFramePenalty = ((p.medianP99Ms ?: 40.0) - 40.0).coerceAtLeast(0.0) * 0.3
            fps - slowFramePenalty
        }
        val index = best?.let { grouped.indexOf(it) }?.takeIf { it >= 0 }
        return AutoTuneEvidence(
            profiles = grouped,
            recommendedFromHistory = index,
            notes = listOf(
                "Steam, explorer and other launcher windows are not valid gameplay FPS measurements.",
                "A session under 120 seconds is not a performance benchmark.",
                "Runs with moderate-or-higher thermal status are excluded from FPS comparisons.",
                "Different scenes, game builds, resolutions and shader compilation can invalidate FPS comparisons.",
                "A historical recommendation requires at least two measured stable runs; it is not automatically applied.",
            ),
        )
    }

    private fun signature(settings: Map<String, String>): Map<String, String> =
        settings.filterKeys { it in GameSettings.RUNTIME_KEYS }

    private fun round1(value: Double): Double = round(value * 10.0) / 10.0
}

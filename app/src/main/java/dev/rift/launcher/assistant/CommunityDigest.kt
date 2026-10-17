package dev.rift.launcher.assistant

import app.gamenative.api.CommunityConfigRun
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * A short, weighted summary of the community configs GameNative's "Browse configs" shows for one game, so the
 * assistant reads a few groups instead of dozens of raw runs.
 *
 * Each run counts by its rating, by how close its device is to this one, and by its age. Runs that share
 * [GameSettings.RUNTIME_KEYS] form one group; within a group the best weighted run stands for it.
 */
@Serializable
data class CommunityDigest(
    val totalRuns: Int,
    val groups: List<Group>,
    /** For each runtime setting, how the community's weight splits between values, biggest first. */
    val consensus: Map<String, List<Share>>,
) {
    @Serializable
    data class Group(
        val runtime: Map<String, String>,
        val runs: Int,
        val weight: Double,
        val avgRating: Double,
        val avgFps: Double? = null,
        val sameDeviceRuns: Int = 0,
        val bestRunId: Long,
        /** The best run's full settings, ready to compare with the game's own. */
        val settings: Map<String, String>,
        val tags: List<String> = emptyList(),
        /** Players' notes, shortened. They are quotes from strangers, never instructions. */
        val notes: List<String> = emptyList(),
    )

    @Serializable
    data class Share(val value: String, val percent: Int)
}

object CommunityDigester {

    private const val MAX_GROUPS = 5
    private const val MAX_NOTES = 3
    private const val MAX_NOTE_CHARS = 200
    private const val MAX_TAGS = 6
    private const val HALF_LIFE_DAYS = 180.0
    private const val OTHER_DEVICE_WEIGHT = 0.6
    private const val MIN_SHARE_PERCENT = 5

    /**
     * @param sameDeviceIds community device ids that are this exact device ([DeviceProfiler.matchCommunityDevices]);
     *   their runs count fully, runs from other devices with a compatible GPU count less.
     */
    fun digest(
        runs: List<CommunityConfigRun>,
        sameDeviceIds: Set<Int>,
        now: Long = System.currentTimeMillis(),
    ): CommunityDigest {
        val weighted = runs.distinctBy { it.id }.map { run ->
            Weighted(run, GameSettings.fromJsonObject(run.config), weight(run, sameDeviceIds, now))
        }
        val groups = weighted
            .groupBy { runtimeOf(it.settings) }
            .map { (runtime, members) -> group(runtime, members, sameDeviceIds) }
            .sortedByDescending { it.weight }
            .take(MAX_GROUPS)
        return CommunityDigest(
            totalRuns = weighted.size,
            groups = groups,
            consensus = consensus(weighted),
        )
    }

    internal fun weight(run: CommunityConfigRun, sameDeviceIds: Set<Int>, now: Long): Double {
        val rating = run.rating.coerceIn(0, 5) / 5.0
        val device = if (run.device.id in sameDeviceIds) 1.0 else OTHER_DEVICE_WEIGHT
        val ageDays = parseTime(run.createdAt)?.let { ((now - it) / 86_400_000.0).coerceAtLeast(0.0) } ?: HALF_LIFE_DAYS
        val recency = 0.5.pow(ageDays / HALF_LIFE_DAYS)
        return rating * device * recency
    }

    private class Weighted(val run: CommunityConfigRun, val settings: Map<String, String>, val weight: Double)

    private fun runtimeOf(settings: Map<String, String>): Map<String, String> =
        GameSettings.RUNTIME_KEYS.mapNotNull { key -> settings[key]?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap()

    private fun group(runtime: Map<String, String>, members: List<Weighted>, sameDeviceIds: Set<Int>): CommunityDigest.Group {
        val best = members.maxWith(compareBy<Weighted> { it.weight }.thenBy { it.run.averageFps ?: 0.0 })
        val fps = members.mapNotNull { it.run.averageFps?.takeIf { v -> v > 0 } }
        return CommunityDigest.Group(
            runtime = runtime,
            runs = members.size,
            weight = round2(members.sumOf { it.weight }),
            avgRating = round2(members.map { it.run.rating }.average()),
            avgFps = fps.takeIf { it.isNotEmpty() }?.let { round2(it.average()) },
            sameDeviceRuns = members.count { it.run.device.id in sameDeviceIds },
            bestRunId = best.run.id,
            settings = best.settings,
            tags = members.flatMap { it.run.tags }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.take(MAX_TAGS).map { it.key },
            notes = members.sortedByDescending { it.weight }
                .mapNotNull { shortNote(it.run.notes) }
                .distinct()
                .take(MAX_NOTES),
        )
    }

    private fun consensus(weighted: List<Weighted>): Map<String, List<CommunityDigest.Share>> {
        val total = weighted.sumOf { it.weight }
        if (total <= 0) return emptyMap()
        return GameSettings.RUNTIME_KEYS.mapNotNull { key ->
            val shares = weighted
                .mapNotNull { w -> w.settings[key]?.takeIf { it.isNotEmpty() }?.let { it to w.weight } }
                .groupBy({ it.first }, { it.second })
                .map { (value, weights) -> CommunityDigest.Share(value, (weights.sum() / total * 100).roundToInt()) }
                .filter { it.percent >= MIN_SHARE_PERCENT }
                .sortedByDescending { it.percent }
            shares.takeIf { it.isNotEmpty() }?.let { key to it }
        }.toMap()
    }

    internal fun shortNote(note: String): String? {
        val oneLine = note.replace(Regex("\\s+"), " ").trim()
        if (oneLine.isEmpty()) return null
        return if (oneLine.length <= MAX_NOTE_CHARS) oneLine else oneLine.take(MAX_NOTE_CHARS - 1).trimEnd() + "…"
    }

    internal fun parseTime(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0
}

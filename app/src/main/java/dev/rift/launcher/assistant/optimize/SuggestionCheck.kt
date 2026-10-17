package dev.rift.launcher.assistant.optimize

import app.gamenative.api.SupportSuggestion
import dev.rift.launcher.assistant.DeviceProfile
import java.util.Locale
import org.json.JSONObject

/** The model's answer after Rift's checks: either something the user can apply, nothing to change, or problems. */
sealed interface CheckedAnswer {
    data class Proposal(val suggestion: SupportSuggestion, val summary: String, val risk: String) : CheckedAnswer
    data class NothingToChange(val summary: String) : CheckedAnswer
    data class Rejected(val problems: List<String>) : CheckedAnswer
}

/**
 * Checks an answer before the user sees it. GameNative's [SupportSuggestion.parse] already rejects unknown keys,
 * bad values and duplicates; on top of that Rift caps the number of changes, keeps envVars and execArgs out unless
 * the user allowed them, and accepts component versions only from the device's ready or downloadable lists.
 */
object SuggestionCheck {

    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

    fun check(text: String, profile: DeviceProfile, current: Map<String, String>, allowEnvAndArgs: Boolean): CheckedAnswer {
        val json = extractJson(text) ?: return CheckedAnswer.Rejected(listOf("The answer is not one JSON object."))
        val summary = json.optString("summary").trim()
        val risk = json.optString("risk").trim().lowercase(Locale.ROOT).takeIf { it in setOf("low", "medium", "high") } ?: "medium"
        val parsed = try {
            SupportSuggestion.parse(json)
        } catch (e: Exception) {
            return CheckedAnswer.Rejected(listOf("The JSON does not follow the format: ${e.message}"))
        }
        // Changes that set what is already there do nothing; drop them instead of bothering the user.
        val changes = parsed?.changes.orEmpty().filterNot { !it.isEnv && it.to != null && current[it.key] == it.to }
        if (parsed == null || (changes.isEmpty() && parsed.run == null)) {
            if (json.optInt("v", 0) != 1) return CheckedAnswer.Rejected(listOf("\"v\" must be 1."))
            return CheckedAnswer.NothingToChange(summary)
        }

        val problems = ArrayList<String>()
        changes.filterNot(SupportSuggestion::isValid).forEach { problems += "\"${it.key}\" = \"${it.to}\" is not an allowed key or value." }
        if (!parsed.applicable && problems.isEmpty()) problems += "The changes repeat a key or there are too many."
        if (changes.size > OptimizerPrompt.MAX_CHANGES) problems += "${changes.size} changes; at most ${OptimizerPrompt.MAX_CHANGES} are allowed."
        if (!allowEnvAndArgs) {
            changes.filter { it.isEnv || it.key == "execArgs" }.forEach { problems += "\"${it.key}\" may not be changed for this player." }
        }
        changes.forEach { change -> versionProblem(change, profile)?.let(problems::add) }
        if (parsed.run != null) problems += "Debug runs are not supported here; leave out \"run\"."
        if (problems.isNotEmpty()) return CheckedAnswer.Rejected(problems)

        return CheckedAnswer.Proposal(
            suggestion = SupportSuggestion(changes, rerun = false, run = null, applicable = true),
            summary = summary,
            risk = risk,
        )
    }

    /** Finds the JSON object in a reply, also when the model wrapped it in a code fence or a sentence. */
    internal fun extractJson(text: String): JSONObject? {
        val trimmed = text.trim().replace(FENCE, "").trim()
        runCatching { return JSONObject(trimmed) }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(trimmed.substring(start, end + 1)) }.getOrNull()
    }

    private fun versionProblem(change: SupportSuggestion.Change, profile: DeviceProfile): String? {
        val c = profile.components
        val lists = when (change.key) {
            "wineVersion" -> listOf(c.wine)
            "graphicsDriverVersion", "graphicsDriverConfig.version" -> listOf(c.drivers)
            "dxwrapperConfig.version" -> listOf(c.dxvk)
            "dxwrapperConfig.vkd3dVersion" -> listOf(c.vkd3d)
            "box64Version" -> listOf(c.box64, c.wowBox64)
            "fexcoreVersion" -> listOf(c.fexcore)
            else -> return null
        }
        val known = lists.flatMap { it.ready + it.downloadable }
        if (known.isEmpty()) return null
        val wanted = normalize(change.to.orEmpty())
        if (known.any { normalize(it) == wanted }) return null
        return "\"${change.key}\" = \"${change.to}\" is not on this device or in the download list. " +
            "Choose from: ${known.joinToString(", ")}."
    }

    /** List entries carry labels such as "0.3.7 (Default)"; containers store "0.3.7". */
    private fun normalize(version: String): String =
        version.replace(Regex("\\s*\\(default\\)", RegexOption.IGNORE_CASE), "").trim().lowercase(Locale.ROOT)
}

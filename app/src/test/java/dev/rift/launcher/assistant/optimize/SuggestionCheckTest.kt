package dev.rift.launcher.assistant.optimize

import app.gamenative.api.SupportSuggestion
import dev.rift.launcher.assistant.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionCheckTest {

    private fun versions(vararg ready: String, downloadable: List<String> = emptyList()) =
        DeviceProfile.ComponentVersions(ready.toList(), downloadable)

    private val profile = DeviceProfile(
        collectedAt = 1L,
        hardware = DeviceProfile.Hardware("REDMAGIC", "NP05J", "QTI", "SM8750", "Snapdragon 8 Elite", "16", 36, "modern", "bionic"),
        gpu = DeviceProfile.Gpu("Adreno (TM) 830", "Qualcomm", "OpenGL ES 3.2", "1.3.284", "adreno_elite"),
        memory = DeviceProfile.Memory(16000, 9000),
        storage = DeviceProfile.Storage(500000, 120000),
        display = DeviceProfile.Display(2400, 1504, 165, listOf(165, 120, 60), 320),
        power = DeviceProfile.Power(84, true, 36, 0, 0.4f),
        components = DeviceProfile.Components(
            drivers = versions("System", "Turnip_Gen8_V25", downloadable = listOf("turnip26.0.0_R8")),
            wine = versions("proton-9.0-arm64ec"),
            dxvk = versions("2.7.1", "1.10.3"),
            vkd3d = versions("2.14.1"),
            box64 = versions("0.3.7 (Default)", "0.4.2"),
            wowBox64 = versions("0.3.7"),
            fexcore = versions("2508"),
        ),
    )

    private fun check(text: String, current: Map<String, String> = emptyMap(), allowEnv: Boolean = false) =
        SuggestionCheck.check(text, profile, current, allowEnv)

    @Test
    fun acceptsAValidAnswerInACodeFence() {
        val answer = check(
            """
            ```json
            {"v":1,"summary":"Newer Turnip and DXVK.","risk":"low","changes":[
              {"key":"graphicsDriverConfig.version","to":"turnip26.0.0_R8","why":"faster on Adreno 830"},
              {"key":"dxwrapperConfig.version","to":"2.7.1","why":"fixes stutter"},
              {"key":"box64Version","to":"0.3.7","why":"stable"}]}
            ```
            """.trimIndent(),
        )
        answer as CheckedAnswer.Proposal
        assertEquals(3, answer.suggestion.changes.size)
        assertEquals("low", answer.risk)
        assertEquals("Newer Turnip and DXVK.", answer.summary)
    }

    @Test
    fun rejectsInventedVersionsWithTheList() {
        val answer = check("""{"v":1,"changes":[{"key":"dxwrapperConfig.version","to":"9.9"}]}""") as CheckedAnswer.Rejected
        assertTrue(answer.problems.single().contains("Choose from: 2.7.1, 1.10.3"))
    }

    @Test
    fun rejectsUnknownKeysAndEnvWhenNotAllowed() {
        val bad = check("""{"v":1,"changes":[{"key":"screenSize","to":"1x1"}]}""") as CheckedAnswer.Rejected
        assertTrue(bad.problems.any { it.contains("screenSize") })
        val env = """{"v":1,"changes":[{"key":"envVars","name":"DXVK_ASYNC","op":"set","to":"1"}]}"""
        assertTrue(check(env) is CheckedAnswer.Rejected)
        assertTrue(check(env, allowEnv = true) is CheckedAnswer.Proposal)
    }

    @Test
    fun capsTheNumberOfChanges() {
        val changes = SupportSuggestion.WIN_COMPONENTS.take(7).joinToString(",") { """{"key":"wincomponents.$it","to":"1"}""" }
        val answer = check("""{"v":1,"changes":[$changes]}""") as CheckedAnswer.Rejected
        assertTrue(answer.problems.any { it.contains("at most 6") })
    }

    @Test
    fun noOpChangesMeanNothingToChange() {
        val answer = check(
            """{"v":1,"summary":"Already the best known setup.","changes":[{"key":"dxwrapper","to":"dxvk"}]}""",
            current = mapOf("dxwrapper" to "dxvk"),
        )
        assertEquals(CheckedAnswer.NothingToChange("Already the best known setup."), answer)
    }

    @Test
    fun proseIsRejected() {
        assertTrue(check("I would switch to DXVK.") is CheckedAnswer.Rejected)
    }

    @Test
    fun historyRoundTrips() {
        val proposal = OptimizeProposal(
            id = 42,
            appId = "STEAM_1",
            goal = OptimizeGoal.Battery,
            suggestion = SupportSuggestion(
                listOf(SupportSuggestion.Change("envVars", "DXVK_FRAME_RATE", "set", null, "40", "cap")),
                rerun = false,
                run = null,
                applicable = true,
            ),
            summary = "Cap at 40",
            risk = "low",
            model = "claude-sonnet-5-5",
        )
        val entry = Optimizer.History.fromJson(Optimizer.History.toJson(proposal))
        assertEquals(42L, entry.id)
        assertEquals(OptimizeGoal.Battery, entry.goal)
        assertEquals("DXVK_FRAME_RATE", entry.changes.single().name)
        assertEquals("40", entry.changes.single().to)
    }
}

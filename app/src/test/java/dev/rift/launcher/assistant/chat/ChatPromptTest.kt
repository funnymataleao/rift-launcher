package dev.rift.launcher.assistant.chat

import dev.rift.launcher.assistant.ai.AiMessage
import dev.rift.launcher.assistant.optimize.OptimizerPrompt
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptTest {

    private fun user(text: String) = AiMessage(AiMessage.Role.USER, text)
    private fun assistant(text: String) = AiMessage(AiMessage.Role.ASSISTANT, text)

    @Test
    fun promptsHaveTheSharedPartsFilledIn() {
        listOf(ChatPrompt.SYSTEM, OptimizerPrompt.SYSTEM).forEach { prompt ->
            assertFalse(prompt.contains("@KEYS@") || prompt.contains("@FORMAT@"))
            assertTrue(prompt.contains("dxwrapperConfig.version"))
            assertTrue(prompt.contains("\"changes\":["))
            // trimIndent ran before the parts went in, so the prompt itself is not indented.
            assertFalse(prompt.lines().first().startsWith(" "))
        }
    }

    @Test
    fun contextGoesOnTheFirstUserTurnAndSameSideTurnsAreJoined() {
        val turns = listOf(assistant("left over"), user("a"), user("b"), assistant("c"), user("d"))
        val window = ChatPrompt.window(turns, "<rift-context>x</rift-context>")
        assertEquals(listOf(AiMessage.Role.USER, AiMessage.Role.ASSISTANT, AiMessage.Role.USER), window.map { it.role })
        assertEquals("<rift-context>x</rift-context>\n\na\n\nb", window[0].text)
        assertEquals("d", window[2].text)
    }

    @Test
    fun windowKeepsOnlyTheLatestTurns() {
        val turns = (1..40).map { if (it % 2 == 1) user("u$it") else assistant("a$it") }
        val window = ChatPrompt.window(turns, "ctx")
        assertTrue(window.size <= ChatPrompt.WINDOW)
        assertTrue(window[0].text.startsWith("ctx\n\nu"))
        assertEquals("a40", window.last().text)
    }

    @Test
    fun proposalBlockIsSplitFromTheText() {
        val answer = """
            Turnip 26 is faster on this GPU.

            ```json
            {"v":1,"summary":"Newer driver","changes":[{"key":"graphicsDriverConfig.version","to":"turnip26.0.0_R8"}]}
            ```
        """.trimIndent()
        val (text, block) = ChatPrompt.splitProposal(answer)
        assertEquals("Turnip 26 is faster on this GPU.", text)
        assertTrue(block!!.startsWith("{\"v\":1"))
        assertTrue(block.endsWith("]}"))
    }

    @Test
    fun otherCodeBlocksStayInTheText() {
        val answer = "Set this:\n```json\n{\"DXVK_HUD\":\"fps\"}\n```"
        val (text, block) = ChatPrompt.splitProposal(answer)
        assertEquals(answer, text)
        assertNull(block)
    }

    @Test
    fun contextSaysWhenNoGameIsAttached() {
        val none = ChatPrompt.context(null, null, null, allowEnvAndArgs = false, locale = Locale.forLanguageTag("ru"))
        assertTrue(none.contains("Player's language: Russian"))
        assertTrue(none.contains("Attached game: none."))
        val unstarted = ChatPrompt.context(null, "Hades", null, allowEnvAndArgs = true, locale = Locale.ENGLISH)
        assertTrue(unstarted.contains("\"Hades\". It has no settings yet"))
        assertTrue(unstarted.contains("may be changed: yes"))
    }
}

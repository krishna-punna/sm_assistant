package com.smassistant.app.generate

import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateChooserTest {
    private fun template(id: String) = ImageTemplate(id, id.replaceFirstChar { it.uppercase() }, "$id content", "{{content}} {{format}}")

    private val templates = listOf("universal", "concept", "architecture", "workflow", "series").map(::template)
    private val chooser = TemplateChooser("Pick one.\n{{templates}}", templates)

    @Test
    fun `sends the content and the use-when lines without the series template`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""{"primary": "workflow", "alternates": ["architecture"], "reason": "steps"}"""))

        val choice = chooser.choose(agent, "  Request flow  ")

        assertEquals(TemplateChoice(template("workflow"), listOf(template("architecture")), "steps"), choice)
        val (system, user) = agent.calls.single()
        assertEquals(
            "Pick one.\n- universal: universal content\n- concept: concept content\n- architecture: architecture content\n- workflow: workflow content",
            system,
        )
        assertEquals("Technical content:\nRequest flow", user)
    }

    @Test
    fun `drops unknown, repeated and series alternates and keeps at most two`() {
        val reply = """Sure! ```json
            {"primary": " Concept ", "alternates": ["concept", "nope", "series", "workflow", "workflow", "architecture", "universal"]}
            ```"""
        val choice = parseTemplateChoice(reply, templates)!!
        assertEquals("concept", choice.primary.id)
        assertEquals(listOf("workflow", "architecture"), choice.alternates.map { it.id })
        assertEquals("", choice.reason)
    }

    @Test
    fun `an unknown or series primary, or no JSON, is no choice`() {
        assertEquals(null, parseTemplateChoice("""{"primary": "poster"}""", templates))
        assertEquals(null, parseTemplateChoice("""{"primary": "series"}""", templates))
        assertEquals(null, parseTemplateChoice("workflow", templates))
        assertEquals(null, parseTemplateChoice("{not json}", templates))
    }

    @Test
    fun `falls back to the universal template when the call fails or the reply is unusable`() = runTest {
        val universal = TemplateChoice(template("universal"))
        assertEquals(universal, chooser.choose(ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited)), "x"))
        assertEquals(universal, chooser.choose(ScriptedTextAgent(ApiResult.Ok("I'd use a poster.")), "x"))
    }

    @Test
    fun `the prompt asset lists the templates and every id it names exists`() {
        val prompt = File("src/main/assets/prompts/choose_template.md").readText()
        val ids = parseImageTemplates(File("src/main/assets/prompts/image_templates.md").readText()).map { it.id }
        assertTrue(prompt.contains("{{templates}}"))
        val tableIds = Regex("""(?m)^\| [^|]+ \| (\w+) \|$""").findAll(prompt).map { it.groupValues[1] }.filter { it != "Template" }.toList()
        assertEquals(9, tableIds.size)
        assertTrue(tableIds.all { it in ids })
        assertFalse(SERIES_TEMPLATE in tableIds)
    }
}

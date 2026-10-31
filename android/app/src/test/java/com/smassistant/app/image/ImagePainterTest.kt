package com.smassistant.app.image

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePainterTest {
    private val brief = ImageBrief(
        ImageTemplate("workflow", "Workflow", "steps", "Paint {{content}} as a {{format}} workflow for {{audience}}."),
        "1. Request flow",
        ImageSize.PORTRAIT,
        ImageStyle(audience = "devs"),
    )

    private fun agent(service: ApiService) = object : ImageAgent {
        override val service = service
        override suspend fun generate(prompt: String, count: Int): ApiResult<List<File>> = ApiResult.Ok(emptyList())
    }

    private val longBrief = brief.copy(content = "1. Request flow. " + "A detail of the request flow. ".repeat(80))

    @Test
    fun `Gemini gets the filled template in full, however long`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("short"))

        assertEquals(listOf("Paint 1. Request flow as a portrait 2:3 workflow for devs."), ImagePainter("Split.").prompts(writer, agent(ApiService.GEMINI), brief))
        val long = ImagePainter("Split.").prompts(writer, agent(ApiService.GEMINI), longBrief)
        assertEquals(1, long.size)
        assertTrue(long.single().length > 2000)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `a template within a FLUX agent's budget goes as one prompt, with no split call`() = runTest {
        val writer = ScriptedTextAgent()
        assertEquals(listOf("Paint 1. Request flow as a portrait 2:3 workflow for devs."), ImagePainter("Split.").prompts(writer, agent(ApiService.CLOUDFLARE), brief))
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `content over the budget is split into a numbered poster series, nothing cut (AC-6)`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("```json\n{\"prompts\": [\"Workflow poster 1/2: \\\"Request\\\"\", \"Workflow poster 2/2: \\\"Response\\\"\"]}\n```"))

        val prompts = ImagePainter("Split within {{budget}} into up to {{parts}}.").prompts(writer, agent(ApiService.POLLINATIONS), longBrief, listOf("The word Cahce is misspelt"))

        assertEquals(listOf("Workflow poster 1/2: \"Request\"", "Workflow poster 2/2: \"Response\""), prompts)
        assertEquals("Split within 1,500 into up to $MAX_SERIES_PARTS.", writer.calls.single().first)
        assertTrue(writer.calls.single().second.startsWith("Paint 1. Request flow."))
        assertTrue(writer.calls.single().second.endsWith("- The word Cahce is misspelt"))
    }

    @Test
    fun `budgets come from the settings, every part is kept within it, and a failed split still paints one poster`() = runTest {
        val tooLong = "A sentence about the picture. ".repeat(80)
        val reply = ApiResult.Ok("{\"prompts\": [\"$tooLong\", \"short\"]}")
        val prompts = ImagePainter("S.", budgets = { 1000 }).prompts(ScriptedTextAgent(reply), agent(ApiService.CLOUDFLARE), longBrief)
        assertEquals(2, prompts.size)
        assertTrue(prompts[0].length <= 1000 && prompts[0].endsWith("picture."))

        val many = ApiResult.Ok("{\"prompts\": [${List(9) { "\"p$it\"" }.joinToString()}]}")
        assertEquals(MAX_SERIES_PARTS, ImagePainter("S.").prompts(ScriptedTextAgent(many), agent(ApiService.CLOUDFLARE), longBrief).size)

        val fallback = ImagePainter("S.").prompts(ScriptedTextAgent(ApiResult.Failed(ApiError.InvalidKey)), agent(ApiService.CLOUDFLARE), longBrief)
        assertEquals(1, fallback.size)
        assertTrue(fallback.single().length <= 2000)
    }

    @Test
    fun `a reply that isn't JSON is one prompt`() {
        assertEquals(listOf("A flat workflow infographic"), splitPrompts("```\nA flat workflow infographic\n```"))
        assertEquals(emptyList<String>(), splitPrompts("  "))
    }

    @Test
    fun `Both paints images 2 and 4, painted all, drawn none`() {
        assertEquals(listOf(false, true, false, true), List(4) { paints(ImageMode.BOTH, it) })
        assertTrue(List(4) { paints(ImageMode.PAINTED, it) }.all { it })
        assertTrue(List(4) { paints(ImageMode.AUTO, it) }.all { it })
        assertFalse(List(4) { paints(ImageMode.DRAWN, it) }.any { it })
    }

    @Test
    fun `split prompt asset never cuts content and forbids invented facts`() {
        val asset = File("src/main/assets/prompts/image/split_image_prompt.md").readText()
        assertTrue(asset.contains("{{budget}}") && asset.contains("{{parts}}"))
        assertTrue(asset.contains("Never leave content out"))
        assertTrue(asset.contains("Never add facts"))
        assertTrue(asset.contains("\"prompts\""))
    }
}

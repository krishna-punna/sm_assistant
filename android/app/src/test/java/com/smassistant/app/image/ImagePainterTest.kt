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

    @Test
    fun `Gemini gets the filled template in full`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("short"))

        val prompt = ImagePainter("Condense.").prompt(writer, agent(ApiService.GEMINI), brief)

        assertEquals("Paint 1. Request flow as a portrait 2:3 workflow for devs.", prompt)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun `FLUX agents get the text agent's condensed prompt`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("```\nA flat workflow infographic, \"Request flow\".\n```"))

        val prompt = ImagePainter("Condense.").prompt(writer, agent(ApiService.CLOUDFLARE), brief, listOf("The word Cahce is misspelt"))

        assertEquals("A flat workflow infographic, \"Request flow\".", prompt)
        assertEquals("Condense.", writer.calls.single().first)
        assertTrue(writer.calls.single().second.startsWith("Paint 1. Request flow as a portrait 2:3 workflow for devs."))
        assertTrue(writer.calls.single().second.endsWith("- The word Cahce is misspelt"))
    }

    @Test
    fun `a condensed prompt is kept to 1,500 characters and a failed one shortens the template`() = runTest {
        val long = "A sentence about the picture. ".repeat(80)
        val condensed = ImagePainter("C.").prompt(ScriptedTextAgent(ApiResult.Ok(long)), agent(ApiService.POLLINATIONS), brief)
        assertTrue(condensed.length <= SHORT_IMAGE_PROMPT)
        assertTrue(condensed.endsWith("picture."))

        val fallback = ImagePainter("C.").prompt(ScriptedTextAgent(ApiResult.Failed(ApiError.InvalidKey)), agent(ApiService.CLOUDFLARE), brief)
        assertEquals("Paint 1. Request flow as a portrait 2:3 workflow for devs.", fallback)
    }

    @Test
    fun `Both paints images 2 and 4, painted all, drawn none`() {
        assertEquals(listOf(false, true, false, true), List(4) { paints(ImageMode.BOTH, it) })
        assertTrue(List(4) { paints(ImageMode.PAINTED, it) }.all { it })
        assertFalse(List(4) { paints(ImageMode.DRAWN, it) }.any { it })
    }

    @Test
    fun `condense prompt asset keeps the limit and forbids invented facts`() {
        val asset = File("src/main/assets/prompts/image/condense_image_prompt.md").readText()
        assertTrue(asset.contains("1,500 characters"))
        assertTrue(asset.contains("Never add facts"))
    }
}

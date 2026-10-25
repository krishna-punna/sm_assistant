package com.smassistant.app.agents

import com.smassistant.app.data.ApiService
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeSvgImageAgentTest {
    private val dir = Files.createTempDirectory("svg").toFile()
    private val calls = mutableListOf<Pair<String, String>>()
    private val replies = ArrayDeque<ApiResult<String>>()
    private val rendered = mutableListOf<String>()

    private val text = object : TextAgent {
        override val service = ApiService.CLAUDE
        override suspend fun generate(system: String, user: String): ApiResult<String> {
            synchronized(calls) { calls += system to user }
            return synchronized(replies) { replies.removeFirst() }
        }
    }

    private val agent = ClaudeSvgImageAgent(text, "Reply with SVG.", MediaCache(dir)) { svg ->
        synchronized(rendered) { rendered += svg }
        if (svg.contains("broken")) null else svg.toByteArray()
    }

    private fun generate(count: Int) = runBlocking { agent.generate("Draw a URL shortener", count) }

    @Test
    fun `asks Claude once per image and saves each SVG as a PNG`() {
        replies += ApiResult.Ok("```svg\n<svg viewBox=\"0 0 10 10\"><rect/></svg>\n```")
        replies += ApiResult.Ok("<svg><circle/></svg>")

        val files = (generate(2) as ApiResult.Ok).value

        assertEquals(ApiService.CLAUDE, agent.service)
        assertEquals(listOf("Reply with SVG." to "Draw a URL shortener", "Reply with SVG." to "Draw a URL shortener"), calls)
        assertEquals(setOf("<svg viewBox=\"0 0 10 10\"><rect/></svg>", "<svg><circle/></svg>"), rendered.toSet())
        assertTrue(files.all { it.parentFile == dir && it.name.endsWith(".png") && it.exists() })
    }

    @Test
    fun `keeps the images that worked`() {
        replies += ApiResult.Ok("<svg>broken</svg>")
        replies += ApiResult.Ok("<svg><rect/></svg>")

        assertEquals(1, (generate(2) as ApiResult.Ok).value.size)
    }

    @Test
    fun `fails with the reason when no image worked`() {
        replies += ApiResult.Failed(ApiError.RateLimited)
        assertEquals(ApiResult.Failed(ApiError.RateLimited), generate(1))

        replies += ApiResult.Ok("Sorry, I can't draw that.")
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no SVG in the reply")), generate(1))

        replies += ApiResult.Ok("<svg><rect/><text>cut")
        assertEquals(ApiResult.Failed(ApiError.BadResponse("the SVG was cut off")), generate(1))

        replies += ApiResult.Ok("<svg>broken</svg>")
        assertEquals(ApiResult.Failed(ApiError.BadResponse("the SVG couldn't be drawn")), generate(1))
    }

    @Test
    fun `extracts the svg element from around it`() {
        assertEquals("<svg a=\"1\"><g></g></svg>", extractSvg("Here you go:\n<svg a=\"1\"><g></g></svg>\nEnjoy"))
        assertNull(extractSvg("no drawing"))
        assertNull(extractSvg("</svg> <svg>"))
    }

    @Test
    fun `svg system prompt asset asks for svg only`() {
        val prompt = java.io.File("src/main/assets/prompts/image_svg.md").readText()
        assertTrue(prompt.contains("<svg") && prompt.contains("</svg>"))
    }
}

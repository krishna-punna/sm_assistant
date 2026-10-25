package com.smassistant.app.prompt

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeAgent(private val reply: ApiResult<String>) : TextAgent {
    override val service = ApiService.GEMINI
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        calls += system to user
        return reply
    }
}

class PreparePromptTest {
    private val topic = "Design a system for Short URL"

    @Test
    fun `sends the system prompt and topic and parses the JSON reply`() = runTest {
        val agent = FakeAgent(ApiResult.Ok("""{"title": "URL Shortener System Design Prompt", "prompt": "Act as a Senior Principal Engineer."}"""))
        val outcome = PreparePromptUseCase("SYSTEM", { agent })(topic)

        assertEquals(listOf("SYSTEM" to topic), agent.calls)
        assertEquals(Outcome.Ok(PreparedPrompt("URL Shortener System Design Prompt", "Act as a Senior Principal Engineer.")), outcome)
    }

    @Test
    fun `no agent set up gives NoAgent`() = runTest {
        assertEquals(Outcome.NoAgent, PreparePromptUseCase("SYSTEM", { null })(topic))
    }

    @Test
    fun `agent errors carry the service name`() = runTest {
        val outcome = PreparePromptUseCase("SYSTEM", { FakeAgent(ApiResult.Failed(ApiError.RateLimited)) })(topic)
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Gemini"), outcome)
    }

    @Test
    fun `JSON inside fences or text is still read`() {
        val reply = "Here you go:\n```json\n{\"title\": \"T\", \"prompt\": \"Use {braces} here\"}\n```"
        assertEquals(PreparedPrompt("T", "Use {braces} here"), parsePreparedPrompt(reply, topic))
    }

    @Test
    fun `a plain-text reply becomes the prompt, titled from the topic`() {
        assertEquals(
            PreparedPrompt("Design A System For Short URL", "Act as an expert."),
            parsePreparedPrompt("  Act as an expert. ", topic),
        )
    }

    @Test
    fun `a missing title falls back to the topic`() {
        assertEquals(PreparedPrompt("Short Urls", "P"), parsePreparedPrompt("""{"prompt": "P", "title": " "}""", "short  urls"))
    }

    @Test
    fun `long topics give a shortened title`() {
        val title = titleFromTopic("word ".repeat(30))
        assertEquals(60, title.length)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `prompt asset keeps every recipe step and the JSON reply format`() {
        val asset = File("src/main/assets/prompts/prepare_prompt.md").readText()
        listOf("Persona", "Task", "Sections", "Supporting material", "Targets", "\"title\"", "\"prompt\"").forEach {
            assertTrue("prepare_prompt.md is missing $it", asset.contains(it))
        }
    }
}

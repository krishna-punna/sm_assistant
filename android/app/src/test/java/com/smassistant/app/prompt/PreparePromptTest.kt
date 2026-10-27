package com.smassistant.app.prompt

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import org.junit.Assert.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeAgent(private val reply: ApiResult<String>, private val replies: Map<String, ApiResult<String>> = emptyMap()) : TextAgent {
    override val service = ApiService.GEMINI
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        calls += system to user
        return replies.entries.firstOrNull { system.startsWith(it.key) }?.value ?: reply
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

    private fun prompts(agent: TextAgent?, type: PromptType) =
        PreparePromptsUseCase("POST", "CONTENT for {{medium}}. {{structure}}", { agent }, { type })

    private val agent = FakeAgent(
        ApiResult.Ok("unused"),
        mapOf(
            "POST" to ApiResult.Ok("""{"title": "Post Prompt", "prompt": "Write a post."}"""),
            "CONTENT" to ApiResult.Ok("""{"title": "Content Prompt", "prompt": "Write everything."}"""),
        ),
    )

    @Test
    fun `Text prompt type gives only the post prompt`() = runTest {
        val outcome = prompts(agent, PromptType.TEXT)(topic)
        assertEquals(Outcome.Ok(PromptSet(PromptType.TEXT, post = PreparedPrompt("Post Prompt", "Write a post."))), outcome)
        assertEquals(listOf("POST" to topic), agent.calls)
    }

    @Test
    fun `Image and Video prompt types give only the no-limit content prompt for their medium`() = runTest {
        val image = prompts(agent, PromptType.IMAGE)(topic)
        assertEquals(Outcome.Ok(PromptSet(PromptType.IMAGE, content = PreparedPrompt("Content Prompt", "Write everything."))), image)
        val video = prompts(agent, PromptType.VIDEO)(topic)
        assertEquals(PromptType.VIDEO, (video as Outcome.Ok).value.type)

        val (imageSystem, videoSystem) = agent.calls.map { it.first }
        assertTrue(imageSystem.contains("infographic cheat-sheet image"))
        assertTrue(videoSystem.contains("slideshow video"))
        assertFalse(agent.calls.any { "{{" in it.first })
    }

    @Test
    fun `Text and Image and Text and Video give both prompts`() = runTest {
        listOf(PromptType.TEXT_AND_IMAGE, PromptType.TEXT_AND_VIDEO).forEach { type ->
            val set = (prompts(agent, type)(topic) as Outcome.Ok).value
            assertEquals(PreparedPrompt("Post Prompt", "Write a post."), set.post)
            assertEquals(PreparedPrompt("Content Prompt", "Write everything."), set.content)
        }
    }

    @Test
    fun `one failing prompt fails the submit, and no agent gives NoAgent`() = runTest {
        val failing = FakeAgent(ApiResult.Ok("""{"title": "T", "prompt": "P"}"""), mapOf("CONTENT" to ApiResult.Failed(ApiError.RateLimited)))
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Gemini"), prompts(failing, PromptType.TEXT_AND_IMAGE)(topic))
        assertEquals(Outcome.NoAgent, prompts(null, PromptType.IMAGE)(topic))
    }

    @Test
    fun `content prompt asset keeps the recipe, the placeholders and the JSON reply format`() {
        val asset = File("src/main/assets/prompts/prepare_content_prompt.md").readText()
        listOf("Persona", "Task", "Sections", "Visual material", "Targets", "no length limit", "{{medium}}", "{{structure}}", "\"title\"", "\"prompt\"")
            .forEach { assertTrue("prepare_content_prompt.md is missing $it", asset.contains(it)) }
    }
}

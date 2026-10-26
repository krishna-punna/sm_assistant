package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.hashtags.postLength
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTrimTest {
    private val post = listOf(
        "🔧 Caching in Spring Boot",
        "Why it matters: slow reads.",
        "✅ Options\n- Caffeine for local\n- Redis for shared\n- Hazelcast for grids",
        "⚠️ Pitfalls\n- Stale data\n- Memory pressure",
        "📌 In short:\nCache what is read often.",
        "#java #spring",
    ).joinToString("\n\n")

    private fun under(limit: Int): (String) -> Boolean = { postLength(it) <= limit }

    @Test
    fun `a post that fits is unchanged`() {
        assertEquals(post, PostCut.toFit(post, under(1_000)))
    }

    @Test
    fun `cuts lines from the last middle section first and keeps hook, summary and hashtags`() {
        val cut = PostCut.toFit(post, under(postLength(post) - 10))
        assertEquals(
            listOf(
                "🔧 Caching in Spring Boot",
                "Why it matters: slow reads.",
                "✅ Options\n- Caffeine for local\n- Redis for shared\n- Hazelcast for grids",
                "⚠️ Pitfalls\n- Stale data",
                "📌 In short:\nCache what is read often.",
                "#java #spring",
            ).joinToString("\n\n"),
            cut,
        )
    }

    @Test
    fun `a heading left on its own goes with its last line`() {
        val cut = PostCut.toFit(post, under(postLength(post) - 20))
        assertTrue(!cut.contains("⚠️ Pitfalls"))
        assertTrue(cut.contains("- Hazelcast for grids"))
    }

    @Test
    fun `with only the hook and summary left it cuts words and keeps the hashtags`() {
        val cut = PostCut.toFit(post, under(50))
        assertTrue(postLength(cut) <= 50)
        assertTrue(cut.startsWith("🔧 Caching"))
        assertTrue(cut.endsWith("…\n\n#java #spring"))
    }

    @Test
    fun `a table or code block goes as a whole`() {
        val text = "Hook\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n📌 In short: ok."
        assertEquals("Hook\n\n📌 In short: ok.", PostCut.toFit(text, under(30)))
    }

    private class Agent(private val reply: ApiResult<String>) : TextAgent {
        override val service = ApiService.GEMINI
        var request = ""
        override suspend fun generate(system: String, user: String): ApiResult<String> {
            request = user
            return reply
        }
    }

    @Test
    fun `smart trim asks for the budget, keeps key points and returns plain text`() = runTest {
        val agent = Agent(ApiResult.Ok("**Short** post"))
        assertEquals(Outcome.Ok("Short post"), SmartTrimUseCase { agent }("Long post", 200, under(200)))
        assertTrue(agent.request.contains("at most 200 characters"))
        assertTrue(agent.request.contains("Keep every key point"))
        assertTrue(agent.request.endsWith("Long post"))
    }

    @Test
    fun `smart trim cuts a reply that is still too long`() = runTest {
        val agent = Agent(ApiResult.Ok("word ".repeat(100).trim()))
        val trimmed = (SmartTrimUseCase { agent }("x", 50, under(50)) as Outcome.Ok).value
        assertTrue(postLength(trimmed) <= 50)
    }

    @Test
    fun `smart trim reports a missing agent or an error`() = runTest {
        assertEquals(Outcome.NoAgent, SmartTrimUseCase { null }("x", 10, under(10)))
        assertEquals(
            Outcome.Failed(ApiError.RateLimited, "Gemini"),
            SmartTrimUseCase { Agent(ApiResult.Failed(ApiError.RateLimited)) }("x", 10, under(10)),
        )
    }
}

package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeAgent(private val replies: List<ApiResult<String>>) : TextAgent {
    constructor(reply: ApiResult<String>) : this(listOf(reply))

    override val service = ApiService.CLAUDE
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        calls += system to user
        return replies[minOf(calls.size - 1, replies.lastIndex)]
    }
}

class GenerateTextTest {
    @Test
    fun `sends the system prompt with the user's prompt`() = runTest {
        val agent = FakeAgent(ApiResult.Ok("🔧 Post"))
        assertEquals(Outcome.Ok("🔧 Post"), GenerateTextUseCase("SYSTEM", { agent })("Act as an engineer"))
        assertEquals(listOf("SYSTEM" to "Act as an engineer"), agent.calls)
    }

    @Test
    fun `no agent and errors map to outcomes`() = runTest {
        assertEquals(Outcome.NoAgent, GenerateTextUseCase("SYSTEM", { null })("p"))
        assertEquals(
            Outcome.Failed(ApiError.InvalidKey, "Claude"),
            GenerateTextUseCase("SYSTEM", { FakeAgent(ApiResult.Failed(ApiError.InvalidKey)) })("p"),
        )
    }

    @Test
    fun `technical content prompt asks for a LinkedIn-ready post`() {
        val asset = File("src/main/assets/prompts/technical_content.md").readText()
        listOf("Hook", "📌 In short:", "3,000", "length budget", "Do not add hashtags").forEach {
            assertTrue("technical_content.md is missing $it", asset.contains(it))
        }
        assertFalse(asset.isBlank())
    }

    private var tags = listOf("#JavaDeveloper", "#AIEngineer")
    private var tagsOn = true
    private fun guard(limit: Int = 100, attempts: Int = 2) = PostLengthGuard({ limit }, { tags }, { tagsOn }, attempts)

    @Test
    fun `budget leaves room for the default hashtags`() {
        // "#JavaDeveloper #AIEngineer" is 26 characters, plus the blank line before it.
        assertEquals(100 - 28, guard().budget())
        tagsOn = false
        assertEquals(100, guard().budget())
    }

    @Test
    fun `the system prompt carries the budget`() = runTest {
        val agent = FakeAgent(ApiResult.Ok("short"))
        GenerateTextUseCase("SYSTEM", { agent }, guard())("p")
        val system = agent.calls.single().first
        assertTrue(system.startsWith("SYSTEM\n\nLength budget: the post must be at most 72 characters"))
        assertTrue(system.contains("take 28 of LinkedIn's 100 characters"))
    }

    @Test
    fun `a post that fits is returned without asking again`() = runTest {
        val agent = FakeAgent(ApiResult.Ok("x".repeat(72)))
        assertEquals(Outcome.Ok("x".repeat(72)), GenerateTextUseCase("S", { agent }, guard())("p"))
        assertEquals(1, agent.calls.size)
    }

    @Test
    fun `an over-long post is sent back to be shortened`() = runTest {
        val long = "x".repeat(80)
        val agent = FakeAgent(listOf(ApiResult.Ok(long), ApiResult.Ok("x".repeat(60))))
        assertEquals(Outcome.Ok("x".repeat(60)), GenerateTextUseCase("S", { agent }, guard())("p"))
        assertEquals(2, agent.calls.size)
        val request = agent.calls[1].second
        assertTrue(request.contains("8 characters over LinkedIn's 100-character limit"))
        assertTrue(request.contains("at most 72 characters"))
        assertTrue(request.endsWith(long))
    }

    @Test
    fun `stops after the set attempts and keeps the shortest version`() = runTest {
        val agent = FakeAgent(listOf(ApiResult.Ok("x".repeat(90)), ApiResult.Ok("x".repeat(80)), ApiResult.Ok("x".repeat(85))))
        assertEquals(Outcome.Ok("x".repeat(80)), GenerateTextUseCase("S", { agent }, guard(attempts = 2))("p"))
        assertEquals(3, agent.calls.size)
    }

    @Test
    fun `a failed shorten keeps the first post`() = runTest {
        val agent = FakeAgent(listOf(ApiResult.Ok("x".repeat(90)), ApiResult.Failed(ApiError.RateLimited)))
        assertEquals(Outcome.Ok("x".repeat(90)), GenerateTextUseCase("S", { agent }, guard())("p"))
    }

    @Test
    fun `length is measured on the posted text, not the Markdown`() {
        // "**bold**" posts as "bold", so the markup doesn't count.
        tagsOn = false
        assertEquals(4, guard().measure("**bold**").text.length)
    }
}

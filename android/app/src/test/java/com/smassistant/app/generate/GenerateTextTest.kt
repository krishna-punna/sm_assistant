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

private class FakeAgent(private val reply: ApiResult<String>) : TextAgent {
    override val service = ApiService.CLAUDE
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        calls += system to user
        return reply
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
        listOf("Hook", "📌 In short:", "3,000", "Do not add hashtags").forEach {
            assertTrue("technical_content.md is missing $it", asset.contains(it))
        }
        assertFalse(asset.isBlank())
    }
}

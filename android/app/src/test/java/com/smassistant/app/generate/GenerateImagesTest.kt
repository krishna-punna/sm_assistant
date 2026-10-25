package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateImagesTest {
    private val requests = mutableListOf<Pair<String, Int>>()
    private var reply: ApiResult<List<File>> = ApiResult.Ok(listOf(File("a.png"), File("b.png")))
    private val agent = ImageAgent { prompt, count -> requests += prompt to count; reply }

    @Test
    fun `wraps the prompt in the style template and asks for the count`() = runTest {
        val outcome = GenerateImagesUseCase("Style.\nTopic:\n{{prompt}}", { agent }, count = 4)("  URL shortener  ")
        assertEquals(Outcome.Ok(listOf(File("a.png"), File("b.png"))), outcome)
        assertEquals(listOf("Style.\nTopic:\nURL shortener" to 4), requests)
    }

    @Test
    fun `no Gemini set up gives NoAgent and errors name Gemini`() = runTest {
        assertEquals(Outcome.NoAgent, GenerateImagesUseCase("{{prompt}}", { null }, 4)("x"))
        reply = ApiResult.Failed(ApiError.RateLimited)
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Gemini"), GenerateImagesUseCase("{{prompt}}", { agent }, 4)("x"))
    }

    @Test
    fun `image style asset keeps the prompt placeholder`() {
        assertTrue(File("src/main/assets/prompts/image.md").readText().contains("{{prompt}}"))
    }
}

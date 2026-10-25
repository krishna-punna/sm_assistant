package com.smassistant.app.generate

import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.ApiError
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateContentAndImagesTest {
    private val images = listOf(File("a.png"))
    private var text: Outcome<String> = Outcome.Ok("# Post")
    private var image: Outcome<List<File>> = Outcome.Ok(images)
    private val prompts = mutableListOf<String>()
    private val useCase = GenerateContentAndImagesUseCase(
        text = { prompts += "text:$it"; text },
        images = { prompts += "images:$it"; image },
    )

    @Test
    fun `both halves from the same prompt`() = runTest {
        assertEquals(Outcome.Ok(ContentAndImages(markdown = "# Post", images = images)), useCase("p"))
        assertEquals(setOf("text:p", "images:p"), prompts.toSet())
    }

    @Test
    fun `one half failing keeps the other`() = runTest {
        image = Outcome.Failed(ApiError.RateLimited, "Gemini")
        val textOnly = (useCase("p") as Outcome.Ok).value
        assertEquals("# Post", textOnly.markdown)
        assertEquals("Gemini is rate limiting requests. Try again in a minute.", textOnly.imageError)

        text = Outcome.NoAgent
        image = Outcome.Ok(images)
        val imagesOnly = (useCase("p") as Outcome.Ok).value
        assertEquals(images, imagesOnly.images)
        assertEquals(GenerateContentAndImagesUseCase.TEXT_NOT_SET_UP, imagesOnly.textError)
    }

    @Test
    fun `both failing is a failure, and nothing set up is NoAgent`() = runTest {
        text = Outcome.Failed(ApiError.InvalidKey, "Claude")
        image = Outcome.NoAgent
        assertEquals(Outcome.Failed(ApiError.InvalidKey, "Claude"), useCase("p"))

        text = Outcome.NoAgent
        assertEquals(Outcome.NoAgent, useCase("p"))
    }

    @Test
    fun `retry re-runs only the failed half`() = runTest {
        val partial = ContentAndImages(markdown = "# Post", imageError = "boom", retryingImages = true)
        assertEquals(ContentAndImages(markdown = "# Post", images = images), useCase.retryImages("p", partial))
        assertEquals(listOf("images:p"), prompts)

        text = Outcome.Failed(ApiError.RateLimited, "Groq")
        val still = useCase.retryText("p", ContentAndImages(images = images, textError = "x", retryingText = true))
        assertEquals("Groq is rate limiting requests. Try again in a minute.", still.textError)
        assertEquals(false, still.retryingText)
    }
}

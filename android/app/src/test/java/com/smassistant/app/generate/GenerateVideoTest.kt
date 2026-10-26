package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.VideoClipAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.chat.Outcome
import com.smassistant.app.media.SlideshowMaker
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateVideoTest {
    private val prompts = mutableListOf<String>()
    private val failing = mutableSetOf<Int>()
    private var error: ApiError = ApiError.RateLimited
    private val agent = ImageAgent { prompt, count ->
        assertEquals(1, count)
        synchronized(prompts) { prompts += prompt }
        val slide = prompt.substringBefore(" of").substringAfter("slide ").toInt()
        if (slide in failing) ApiResult.Failed(error) else ApiResult.Ok(listOf(File("s$slide.png")))
    }
    private var encoded: List<File>? = null
    private var encodeResult: (File) -> Result<File> = { Result.success(it) }
    private val maker = SlideshowMaker { slides, output -> encoded = slides; encodeResult(output) }

    private fun useCase(agent: ImageAgent? = this.agent) =
        GenerateVideoUseCase("slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, slides = 4)

    @Test
    fun `asks for each slide in order and encodes them into the video`() = runTest {
        val outcome = useCase()("  URL shortener ")

        val slides = (1..4).map { File("s$it.png") }
        assertEquals(Outcome.Ok(GeneratedVideo(File("out.mp4"), slides)), outcome)
        assertEquals((1..4).map { "slide $it of 4: URL shortener" }, prompts.sorted())
        assertEquals(slides, encoded)
    }

    @Test
    fun `failed slides are left out`() = runTest {
        failing += 2
        useCase()("x")
        assertEquals(listOf(File("s1.png"), File("s3.png"), File("s4.png")), encoded)
    }

    @Test
    fun `too few slides fails with the Gemini error and encodes nothing`() = runTest {
        failing += listOf(1, 2, 3)
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Gemini"), useCase()("x"))
        assertEquals(null, encoded)
    }

    @Test
    fun `encoding failure names the video maker`() = runTest {
        encodeResult = { Result.failure(IllegalStateException("no H.264 encoder")) }
        assertEquals(Outcome.Failed(ApiError.BadResponse("no H.264 encoder"), VIDEO_MAKER), useCase()("x"))
    }

    @Test
    fun `no Gemini set up gives NoAgent`() = runTest {
        assertEquals(Outcome.NoAgent, useCase(agent = null)("x"))
    }

    @Test
    fun `video slide asset keeps its placeholders`() {
        val text = File("src/main/assets/prompts/video.md").readText()
        listOf("{{prompt}}", "{{slide}}", "{{slides}}").forEach { assertTrue(it, text.contains(it)) }
    }

    private fun clipAgent(result: ApiResult<File>?) = object : VideoClipAgent {
        override val service = ApiService.HUGGINGFACE
        override suspend fun generate(prompt: String): ApiResult<File>? {
            synchronized(prompts) { prompts += prompt }
            return result
        }
    }

    private fun clipUseCase(clip: VideoClipAgent) = GenerateVideoUseCase(
        "slide {{slide}} of {{slides}}: {{prompt}}",
        { agent },
        maker,
        { File("out.mp4") },
        slides = 4,
        clips = { clip },
        clipTemplate = "clip: {{prompt}}",
    )

    @Test
    fun `a text-to-video model makes a clip instead of a slideshow`() = runTest {
        val outcome = clipUseCase(clipAgent(ApiResult.Ok(File("clip.mp4"))))(" URL shortener ")

        assertEquals(Outcome.Ok(GeneratedVideo(File("clip.mp4"), emptyList())), outcome)
        assertEquals(listOf("clip: URL shortener"), prompts)
        assertEquals(null, encoded)
    }

    @Test
    fun `a model that can't make clips falls back to the slideshow`() = runTest {
        val outcome = clipUseCase(clipAgent(null))("x")

        assertEquals(Outcome.Ok(GeneratedVideo(File("out.mp4"), (1..4).map { File("s$it.png") })), outcome)
        assertEquals(4, encoded?.size)
    }

    @Test
    fun `a failed clip names Hugging Face`() = runTest {
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Hugging Face"), clipUseCase(clipAgent(ApiResult.Failed(ApiError.RateLimited)))("x"))
        assertEquals(null, encoded)
    }

    @Test
    fun `video clip asset keeps its placeholder`() {
        assertTrue(File("src/main/assets/prompts/video_clip.md").readText().contains("{{prompt}}"))
    }
}

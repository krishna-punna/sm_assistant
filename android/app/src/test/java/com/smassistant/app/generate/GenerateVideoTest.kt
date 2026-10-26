package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.infographic.InfographicComposer
import com.smassistant.app.infographic.InfographicPipeline
import com.smassistant.app.infographic.InfographicRenderer
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

    private var undrawable = setOf<String>()
    private val pipeline = InfographicPipeline(
        InfographicRenderer(),
        "{{prompt}}",
        InfographicComposer { rendered, _ ->
            val title = Regex("<tspan>([^<]*)</tspan>").find(rendered.svg)!!.groupValues[1]
            if (title in undrawable) null else File("$title.png")
        },
    )
    private val deck = """{"palette": "teal", "slides": [
        {"title": "One", "hero": "slide 1 of 4: picture"}, {"title": "Two"}, {"title": "Three"}, {"title": "Four"}]}"""

    private fun deckUseCase(writer: ScriptedTextAgent?, agent: ImageAgent? = this.agent) = GenerateVideoUseCase(
        "slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, slides = 4,
        decks = SlideDecks({ writer }, SlideDeckWriter("Plan {{slides}} slides.{{examples}}"), pipeline),
    )

    @Test
    fun `with a text agent one call plans the deck and every slide goes through the pipeline`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(deck))

        val outcome = deckUseCase(writer)(" Retries ")

        val slides = listOf("One", "Two", "Three", "Four").map { File("$it.png") }
        assertEquals(Outcome.Ok(GeneratedVideo(File("out.mp4"), slides)), outcome)
        assertEquals(listOf("Plan 4 slides." to "Topic: Retries"), writer.calls)
        assertEquals(slides, encoded)
        // Only the hero of slide one asked the video agent for a picture.
        assertEquals(listOf("slide 1 of 4: picture"), prompts)
    }

    @Test
    fun `deck slides that can't be drawn are left out, and too few fail`() = runTest {
        undrawable = setOf("Two")
        deckUseCase(ScriptedTextAgent(ApiResult.Ok(deck)))("x")
        assertEquals(listOf("One", "Three", "Four").map { File("$it.png") }, encoded)

        encoded = null
        undrawable = setOf("One", "Two", "Three")
        assertEquals(
            Outcome.Failed(ApiError.BadResponse("only 1 slide could be drawn"), INFOGRAPHIC_RENDERER),
            deckUseCase(ScriptedTextAgent(ApiResult.Ok(deck)))("x"),
        )
        assertEquals(null, encoded)
    }

    @Test
    fun `deck errors name the text agent and the video works without a video agent`() = runTest {
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Groq"), deckUseCase(ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited)))("x"))
        assertTrue(deckUseCase(ScriptedTextAgent(ApiResult.Ok(deck)), agent = null)("x") is Outcome.Ok)
    }

    @Test
    fun `without a text agent the video agent paints the slides as before`() = runTest {
        deckUseCase(writer = null)("x")
        assertEquals((1..4).map { "slide $it of 4: x" }, prompts.sorted())
        assertEquals(Outcome.NoAgent, deckUseCase(writer = null, agent = null)("x"))
    }

    @Test
    fun `video deck asset keeps its placeholders`() {
        val text = File("src/main/assets/prompts/video_deck.md").readText()
        listOf("{{slides}}", "{{examples}}").forEach { assertTrue(it, text.contains(it)) }
    }
}

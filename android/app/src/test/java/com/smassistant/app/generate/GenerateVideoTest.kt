package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.VideoClipAgent
import com.smassistant.app.data.ApiService
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
    private var slideMillis = 0L
    private var encodeResult: (File) -> Result<File> = { Result.success(it) }
    private val maker = SlideshowMaker { slides, output, millis ->
        encoded = slides
        slideMillis = millis
        encodeResult(output)
    }
    private var seconds = DEFAULT_VIDEO_SECONDS

    private fun useCase(agent: ImageAgent? = this.agent) =
        GenerateVideoUseCase("slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, seconds = { seconds })

    @Test
    fun `asks for each slide in order and encodes them into the video`() = runTest {
        val outcome = useCase()("  URL shortener ")

        val slides = (1..4).map { File("s$it.png") }
        assertEquals(Outcome.Ok(GeneratedVideo(File("out.mp4"), slides)), outcome)
        assertEquals((1..4).map { "slide $it of 4: URL shortener" }, prompts.sorted())
        assertEquals(slides, encoded)
        assertEquals(7500L, slideMillis)
    }

    @Test
    fun `the picked length sets the slide count and the slides fill it`() = runTest {
        seconds = 120
        val outcome = useCase()("x")

        assertEquals(10, prompts.size)
        assertEquals(12_000L, slideMillis)
        assertEquals(120, (outcome as Outcome.Ok).value.seconds)
    }

    @Test
    fun `failed slides are left out`() = runTest {
        failing += 2
        useCase()("x")
        assertEquals(listOf(File("s1.png"), File("s3.png"), File("s4.png")), encoded)
        // The slides left share the whole length.
        assertEquals(10_000L, slideMillis)
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
        {"title": "One", "hero": "slide 1 of 4: picture"}, {"title": "Two", "takeaway": "b"},
        {"title": "Three", "takeaway": "c"}, {"title": "Four", "takeaway": "d"}]}"""

    private fun deckUseCase(writer: ScriptedTextAgent?, agent: ImageAgent? = this.agent) = GenerateVideoUseCase(
        "slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, seconds = { seconds },
        decks = SlideDecks({ writer }, SlideDeckWriter("Plan {{slides}} slides of {{seconds}} s.{{examples}}"), pipeline),
    )

    @Test
    fun `with a text agent one call plans the deck and every slide goes through the pipeline`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(deck))

        val outcome = deckUseCase(writer)(" Retries ")

        val slides = listOf("One", "Two", "Three", "Four").map { File("$it.png") }
        assertEquals(Outcome.Ok(GeneratedVideo(File("out.mp4"), slides)), outcome)
        assertEquals(listOf("Plan 4 slides of 7 s." to "Topic: Retries"), writer.calls)
        assertEquals(slides, encoded)
        // Only the hero of slide one asked the video agent for a picture.
        assertEquals(listOf("slide 1 of 4: picture"), prompts)
    }

    @Test
    fun `the deck is planned from the no-limit content the text agent writes first`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Why retries\n2. Backoff"), ApiResult.Ok(deck))
        val useCase = GenerateVideoUseCase(
            "slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, seconds = { seconds },
            decks = SlideDecks({ writer }, SlideDeckWriter("Plan {{slides}} slides of {{seconds}} s.{{examples}}"), pipeline, MediaContentWriter("Write it all.")),
        )

        assertTrue(useCase("Retries") is Outcome.Ok)
        assertEquals(
            listOf(
                "Write it all." to "Retries",
                "Plan 4 slides of 7 s." to "Topic: Retries\n\nTechnical content to represent:\n1. Why retries\n2. Backoff",
            ),
            writer.calls,
        )
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
        seconds = { seconds },
        clips = { clip },
        clipTemplate = "clip: {{prompt}}",
    )

    @Test
    fun `a text-to-video model makes a clip instead of a slideshow`() = runTest {
        val outcome = clipUseCase(clipAgent(ApiResult.Ok(File("clip.mp4"))))(" URL shortener ")

        assertEquals(Outcome.Ok(GeneratedVideo(File("clip.mp4"), emptyList(), seconds = 0)), outcome)
        assertEquals(listOf("clip: URL shortener"), prompts)
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
        listOf("{{slides}}", "{{seconds}}", "{{examples}}").forEach { assertTrue(it, text.contains(it)) }
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

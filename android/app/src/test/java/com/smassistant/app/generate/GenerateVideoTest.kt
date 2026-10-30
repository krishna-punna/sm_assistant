package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.infographic.InfographicComposer
import com.smassistant.app.infographic.InfographicFormat
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
    private var slideMillis = emptyList<Long>()
    private var encodeResult: (File) -> Result<File> = { Result.success(it) }
    private var size = 0 to 0
    private val maker = SlideshowMaker { slides, output, millis, width, height ->
        encoded = slides
        slideMillis = millis
        size = width to height
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
        assertEquals(List(4) { 7500L }, slideMillis)
    }

    @Test
    fun `the picked length sets the slide count and the slides fill it`() = runTest {
        seconds = 120
        val outcome = useCase()("x")

        assertEquals(10, prompts.size)
        assertEquals(List(10) { 12_000L }, slideMillis)
        assertEquals(120, (outcome as Outcome.Ok).value.seconds)
    }

    @Test
    fun `failed slides are left out`() = runTest {
        failing += 2
        useCase()("x")
        assertEquals(listOf(File("s1.png"), File("s3.png"), File("s4.png")), encoded)
        // The slides left share the whole length.
        assertEquals(List(3) { 10_000L }, slideMillis)
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
    fun `each slide stays up as long as its scene asked, scaled to the length`() = runTest {
        val timed = """{"palette": "teal", "slides": [
            {"title": "One", "seconds": 4, "takeaway": "a"}, {"title": "Two", "seconds": 8, "takeaway": "b"},
            {"title": "Three", "seconds": 8, "takeaway": "c"}, {"title": "Four", "takeaway": "d"}]}"""
        undrawable = setOf("Three")

        deckUseCase(ScriptedTextAgent(ApiResult.Ok(timed)))("x")

        // Three is left out; Four asked for nothing, so it weighs the average of One and Two (6 s).
        assertEquals(listOf("One", "Two", "Four").map { File("$it.png") }, encoded)
        assertEquals(listOf(6_666L, 13_334L, 10_000L), slideMillis)
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

    private val videoTemplates = listOf(
        ImageTemplate("universal", "Universal Master", "unsure", "Any video of {{content}}, {{duration}}, {{format}}."),
        ImageTemplate("workflow", "Workflow or Lifecycle", "steps", "Steps of {{content}}, {{duration}}, {{format}}, for {{audience}}."),
    )

    private fun templateUseCase(writer: ScriptedTextAgent, content: MediaContentWriter? = MediaContentWriter("Write it all.")) =
        GenerateVideoUseCase(
            "slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, seconds = { seconds },
            decks = SlideDecks(
                { writer },
                SlideDeckWriter("Plan {{slides}} slides of {{seconds}} s.{{examples}}"),
                pipeline,
                content,
                Templates(videoTemplates, TemplateChooser("Pick a {{media}} template.\n{{templates}}", videoTemplates, media = "video")),
            ),
        )

    @Test
    fun `the chooser picks a video template for the content and the deck follows it`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request arrives\n2. Retry"),
            ApiResult.Ok("""{"primary": "workflow"}"""),
            ApiResult.Ok(deck),
        )

        assertTrue(templateUseCase(writer)("Write a post on retries") is Outcome.Ok)

        val (chooseSystem, chooseUser) = writer.calls[1]
        assertTrue(chooseSystem.startsWith("Pick a video template."))
        // The chooser reads the technical content, not the prompt.
        assertEquals("Technical content:\n1. Request arrives\n2. Retry", chooseUser)
        assertEquals(
            "Topic: Write a post on retries\n\nTechnical content to represent:\n1. Request arrives\n2. Retry" +
                "\n\nVideo template: Workflow or Lifecycle, vertical 9:16, 30 seconds. Plan the slides by this template. " +
                "The app draws every slide from your JSON and plays them in order without sound, so the template decides " +
                "the story and what each slide shows; keep its content rules and turn its scenes into slides.\n\n" +
                "Steps of (the technical content above), 30 seconds, vertical 9:16, for developers, senior engineers and architects.",
            writer.calls[2].second,
        )
        assertEquals(3, writer.calls.size)
    }

    @Test
    fun `the template sets the video size and every slide is drawn at it`() = runTest {
        val wide = videoTemplates + ImageTemplate("architecture", "Architecture Walkthrough", "parts", "Parts of {{content}}, {{format}}.")
        val drawnFormats = mutableListOf<InfographicFormat>()
        val sizing = InfographicPipeline(InfographicRenderer(), "{{prompt}}", InfographicComposer { rendered, _ ->
            synchronized(drawnFormats) { drawnFormats += if (rendered.width > rendered.height) InfographicFormat.WIDE else InfographicFormat.VERTICAL }
            File("${drawnFormats.size}.png")
        })
        val writer = ScriptedTextAgent(ApiResult.Ok("""{"primary": "architecture"}"""), ApiResult.Ok(deck))
        val useCase = GenerateVideoUseCase(
            "slide {{slide}} of {{slides}}: {{prompt}}", { agent }, maker, { File("out.mp4") }, seconds = { seconds },
            decks = SlideDecks({ writer }, SlideDeckWriter("Plan {{format}}."), sizing, templates = Templates(wide, TemplateChooser("{{templates}}", wide))),
        )

        assertTrue(useCase("Order service") is Outcome.Ok)
        assertEquals(1920 to 1080, size)
        assertEquals(List(4) { InfographicFormat.WIDE }, drawnFormats)
        assertEquals("Plan landscape 16:9.", writer.calls[1].first)
    }

    @Test
    fun `videos without a template are vertical 9:16`() = runTest {
        deckUseCase(ScriptedTextAgent(ApiResult.Ok(deck)))("x")
        assertEquals(1080 to 1920, size)
        useCase()("x")
        assertEquals(1080 to 1920, size)
    }

    @Test
    fun `a failed choice gives the universal template and the video is still made`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("Content."), ApiResult.Failed(ApiError.RateLimited), ApiResult.Ok(deck))

        assertTrue(templateUseCase(writer)("Retries") is Outcome.Ok)
        assertTrue(writer.calls[2].second.contains("Video template: Universal Master"))
    }

    @Test
    fun `without a content step the chooser reads the prompt`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("""{"primary": "workflow"}"""), ApiResult.Ok(deck))

        assertTrue(templateUseCase(writer, content = null)(" Retries ") is Outcome.Ok)
        assertEquals("Technical content:\nRetries", writer.calls[0].second)
        assertTrue(writer.calls[1].second.contains("Steps of (the topic above)"))
    }

    @Test
    fun `video deck asset keeps its placeholders`() {
        val text = File("src/main/assets/prompts/video_deck.md").readText()
        listOf("{{slides}}", "{{seconds}}", "{{format}}", "{{examples}}").forEach { assertTrue(it, text.contains(it)) }
    }
}

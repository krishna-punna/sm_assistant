package com.smassistant.app.image

import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageLayout
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.image.render.Carousel
import com.smassistant.app.image.render.ImageFacts
import com.smassistant.app.image.render.ImageInspector
import com.smassistant.app.image.render.InfographicComposer
import com.smassistant.app.image.render.InfographicPipeline
import com.smassistant.app.image.render.InfographicRenderer
import com.smassistant.app.media.SizeGuard
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `puts the matching few-shot examples before the topic`() = runTest {
        val examples = listOf(
            ImageExample("SQL", listOf("sql"), "SQL tuning", "Before/after grid."),
            ImageExample("Kafka", listOf("kafka"), "Kafka patterns", "Five columns."),
        )
        GenerateImagesUseCase("{{examples}}\n---\n{{prompt}}", { agent }, count = 1, examples = examples)("Kafka retries")
        assertEquals("Example 1: Kafka\nTopic: Kafka patterns\nImage: Five columns.\n---\nKafka retries" to 1, requests.single())
    }

    @Test
    fun `no Gemini set up gives NoAgent and errors name Gemini`() = runTest {
        assertEquals(Outcome.NoAgent, GenerateImagesUseCase("{{prompt}}", { null }, 4)("x"))
        reply = ApiResult.Failed(ApiError.RateLimited)
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Gemini"), GenerateImagesUseCase("{{prompt}}", { agent }, 4)("x"))
    }

    @Test
    fun `errors name the agent that made the images`() = runTest {
        val failing = object : ImageAgent {
            override val service = ApiService.GEMINI
            override suspend fun generate(prompt: String, count: Int) = ApiResult.Failed(ApiError.InvalidKey)
        }
        assertEquals(Outcome.Failed(ApiError.InvalidKey, "Gemini"), GenerateImagesUseCase("{{prompt}}", { failing }, 4)("x"))
    }

    @Test
    fun `image style asset keeps the prompt placeholder`() {
        assertTrue(File("src/main/assets/prompts/image/image.md").readText().contains("{{prompt}}"))
    }

    private val drawn = mutableListOf<String>()
    private var drawable = true
    private val pipeline = InfographicPipeline(
        InfographicRenderer(),
        "{{prompt}}",
        InfographicComposer { rendered, _ ->
            val title = Regex("<tspan>([^<]*)</tspan>").find(rendered.svg)!!.groupValues[1]
            synchronized(drawn) { drawn += title }
            if (drawable) File("$title.png") else null
        },
    )

    /** A definition that passes the writer's checks for the topic "Caching". */
    private fun caching(hero: String? = null) = """{"title": "Caching", ${hero?.let { "\"hero\": \"$it\", " }.orEmpty()}"sections": [
        {"heading": "Cache-aside", "items": ["read the cache first"]}, {"heading": "TTL", "items": ["expire entries"]},
        {"heading": "Eviction", "items": ["LRU drops the coldest"]}]}"""

    private fun infographics(agent: ScriptedTextAgent?) =
        Infographics({ agent }, InfographicWriter("Plan.{{examples}}"), pipeline)

    @Test
    fun `with a text agent each image is an infographic with its own layout idea`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(caching(hero = "a server")))

        val outcome = GenerateImagesUseCase("{{prompt}}", { agent }, count = 2, infographics = infographics(writer))(" Caching ")

        assertEquals(Outcome.Ok(listOf(File("Caching.png"), File("Caching.png"))), outcome)
        assertEquals(INFOGRAPHIC_LAYOUT_IDEAS.take(2).map { "Topic: Caching\n\nLayout idea: $it" }.toSet(), writer.calls.map { it.second }.toSet())
        // The image agent only painted the hero illustrations.
        assertEquals(listOf("a server" to 1, "a server" to 1), requests)
    }

    @Test
    fun `the text agent first writes no-limit content and every infographic represents it`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Cache-aside\n- read the cache first"), ApiResult.Ok(caching()))
        val parts = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, ImageContentWriter("Write it all."))

        val outcome = GenerateImagesUseCase("{{prompt}}", { agent }, count = 2, infographics = parts)("Caching")

        assertEquals(Outcome.Ok(listOf(File("Caching.png"), File("Caching.png"))), outcome)
        assertEquals("Write it all." to "Caching", writer.calls.first())
        val plans = writer.calls.drop(1).map { it.second }
        assertEquals(2, plans.size)
        plans.forEach { assertTrue(it.startsWith("Topic: Caching\n\nTechnical content to represent:\n1. Cache-aside\n- read the cache first\n\nLayout idea: ")) }
    }

    @Test
    fun `a failed content step fails the images and names the text agent`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited))
        val parts = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, ImageContentWriter("Write it all."))
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Groq"), GenerateImagesUseCase("{{prompt}}", { agent }, 2, infographics = parts)("x"))
        assertEquals(1, writer.calls.size)
    }

    @Test
    fun `media content asset asks for no-limit sectioned content`() {
        val asset = File("src/main/assets/prompts/image/image_content.md").readText()
        listOf("no length limit", "Sections", "Key takeaways").forEach { assertTrue("media_content.md is missing $it", asset.contains(it)) }
    }

    @Test
    fun `infographics work without an image agent`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(caching()))
        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), GenerateImagesUseCase("{{prompt}}", { null }, 1, infographics = infographics(writer))("x"))
    }

    @Test
    fun `infographic errors name the text agent or the renderer`() = runTest {
        val limited = ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited))
        assertEquals(Outcome.Failed(ApiError.RateLimited, "Groq"), GenerateImagesUseCase("{{prompt}}", { agent }, 2, infographics = infographics(limited))("x"))

        drawable = false
        val writer = ScriptedTextAgent(ApiResult.Ok(caching()))
        assertEquals(
            Outcome.Failed(ApiError.BadResponse("the infographic couldn't be drawn"), INFOGRAPHIC_RENDERER),
            GenerateImagesUseCase("{{prompt}}", { agent }, 1, infographics = infographics(writer))("x"),
        )
    }

    @Test
    fun `without a text agent the image agent makes finished images as before`() = runTest {
        val outcome = GenerateImagesUseCase("Style: {{prompt}}", { agent }, 4, infographics = infographics(null))("x")
        assertEquals(Outcome.Ok(listOf(File("a.png"), File("b.png"))), outcome)
        assertEquals(listOf("Style: x" to 4), requests)
        assertTrue(drawn.isEmpty())
    }

    @Test
    fun `an infographic the review scores too low is written again with the review's problems`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(caching()))
        val scores = ArrayDeque(listOf("""{"score": 4, "problems": ["The TTL card's text is cut off"]}""", """{"score": 9, "problems": []}"""))
        val seeing = object : TextAgent by writer {
            override suspend fun see(system: String, user: String, jpeg: ByteArray) = ApiResult.Ok(scores.removeFirst())
        }
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { null }, 1,
            infographics = Infographics({ seeing }, InfographicWriter("Plan.{{examples}}"), pipeline, reviewer = ImageReviewer("R.", { byteArrayOf(1) })),
        )

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), useCase("Caching"))
        assertEquals(2, writer.calls.size)
        assertTrue(writer.calls[1].second.contains("A review rejected the last image drawn for this topic:\n- The TTL card's text is cut off"))
        assertEquals(2, drawn.size)
        assertTrue(scores.isEmpty())
    }

    @Test
    fun `after the retries the best-scored image is kept`() = runTest {
        var round = 0
        val titles = listOf("Caching", "Caches")
        val writer = object : TextAgent {
            override val service = ApiService.GEMINI
            override suspend fun generate(system: String, user: String) =
                ApiResult.Ok(caching().replace("\"Caching\"", "\"${titles[round++ % 2]}\""))
            override suspend fun see(system: String, user: String, jpeg: ByteArray) =
                ApiResult.Ok(if (user.contains("Planned title: Caches")) """{"score": 6}""" else """{"score": 3}""")
        }
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { null }, 1,
            infographics = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, reviewer = ImageReviewer("R.", { byteArrayOf(1) })),
        )

        assertEquals(Outcome.Ok(listOf(File("Caches.png"))), useCase("Caching"))
        assertEquals(listOf("Caching", "Caches"), drawn)
    }

    @Test
    fun `an image that can't be reviewed is kept as it is`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(caching()))
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { null }, 1,
            infographics = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, reviewer = ImageReviewer("R.", { byteArrayOf(1) })),
        )
        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), useCase("Caching"))
        assertEquals(1, writer.calls.size)
    }

    @Test
    fun `painted images that come back blank are painted once more, then left out`() = runTest {
        val blank = ImageFacts(1024, 1024, 0f, 1f, 1f)
        val fine = ImageFacts(1024, 1024, 40f, 0.4f, 1f)
        val inspector = ImageInspector { if (it.name.startsWith("blank")) blank else fine }
        var replies = ArrayDeque(listOf(listOf(File("a.png"), File("blank1.png")), listOf(File("c.png"))))
        val painter = ImageAgent { prompt, count -> requests += prompt to count; ApiResult.Ok(replies.removeFirst()) }

        assertEquals(Outcome.Ok(listOf(File("a.png"), File("c.png"))), GenerateImagesUseCase("{{prompt}}", { painter }, 2, inspector = inspector)("x"))
        assertEquals(listOf("x" to 2, "x" to 1), requests)

        replies = ArrayDeque(listOf(listOf(File("blank1.png")), listOf(File("blank2.png"))))
        assertEquals(
            Outcome.Failed(ApiError.BadResponse("the images came back blank or too small"), "Gemini"),
            GenerateImagesUseCase("{{prompt}}", { painter }, 1, inspector = inspector)("x"),
        )
    }

    @Test
    fun `painted images get the picked format and are upscaled`() = runTest {
        var asked: ImageSize? = null
        val painter = object : ImageAgent {
            override suspend fun generate(prompt: String, count: Int) = generate(prompt, count, null)
            override suspend fun generate(prompt: String, count: Int, format: ImageSize?): ApiResult<List<File>> {
                asked = format
                return ApiResult.Ok(listOf(File("a.png")))
            }
        }
        val upscaler = ImageUpscaler { File("big-${it.name}") }

        val outcome = GenerateImagesUseCase("{{prompt}}", { painter }, 1, upscaler = upscaler)
            .generate(ImageRequest("x", format = ImageSize.LANDSCAPE))

        assertEquals(Outcome.Ok(listOf(File("big-a.png"))), outcome)
        assertEquals(ImageSize.LANDSCAPE, asked)
    }

    @Test
    fun `without a text agent Auto asks for portrait and every image goes through the size guard`() = runTest {
        var asked: ImageSize? = null
        val painter = object : ImageAgent {
            override suspend fun generate(prompt: String, count: Int) = generate(prompt, count, null)
            override suspend fun generate(prompt: String, count: Int, format: ImageSize?): ApiResult<List<File>> {
                asked = format
                return ApiResult.Ok(listOf(File("a.png")))
            }
        }
        var ratios = emptyList<Pair<Int, Int>>()
        val guard = SizeGuard { file, allowed -> ratios = allowed; File("fit-${file.name}") }

        val outcome = GenerateImagesUseCase("{{prompt}}", { painter }, 1, sizeGuard = guard).generate(ImageRequest("x"))

        assertEquals(ImageSize.PORTRAIT, asked)
        assertEquals(Outcome.Ok(listOf(File("fit-a.png"))), outcome)
        assertEquals(listOf(2 to 3, 1 to 1, 3 to 2), ratios)
    }

    @Test
    fun `images under the painted minimum are upscaled 2x`() {
        assertEquals(2, upscaleFactor(832, 1248))
        assertEquals(2, upscaleFactor(1248, 832))
        assertEquals(1, upscaleFactor(1024, 1536))
        assertEquals(1, upscaleFactor(1536, 1024))
        // A square needs 1536 x 1536, so FLUX's 1024 x 1024 is upscaled.
        assertEquals(2, upscaleFactor(1024, 1024))
        assertEquals(1, upscaleFactor(1536, 1536))
    }

    private fun template(id: String) = ImageTemplate(id, id.replaceFirstChar { it.uppercase() }, "$id use", "Rules for {{content}} in {{format}} for {{audience}}.")

    private val templateList = listOf("universal", "concept", "architecture", "workflow").map(::template)

    private fun templated(
        writer: TextAgent,
        style: ImageStyle = ImageStyle(audience = "devs"),
        painter: ImagePainter? = null,
        reviewer: ImageReviewer? = null,
    ) = Infographics(
        { writer }, InfographicWriter("Plan.{{examples}}"), pipeline, ImageContentWriter("Write it all."),
        reviewer = reviewer,
        templates = Templates(templateList, TemplateChooser("Choose.{{templates}}", templateList), { style }, painter),
    )

    /** A Gemini image agent that records each prompt and format it is asked to paint. */
    /** Paints into [dir] when given (so labels can rename the files), else returns files that don't exist. */
    private class PaintingAgent(
        private val fails: Boolean = false,
        override val onlyFormat: ImageSize? = null,
        private val dir: File? = null,
    ) : ImageAgent {
        val painted = mutableListOf<Pair<String, ImageSize?>>()
        override suspend fun generate(prompt: String, count: Int) = generate(prompt, count, null)
        override suspend fun generate(prompt: String, count: Int, format: ImageSize?): ApiResult<List<File>> {
            val n = synchronized(painted) { painted += prompt to format; painted.size }
            val file = File(dir, "painted-$n.png").also { if (dir != null) it.createNewFile() }
            return if (fails) ApiResult.Failed(ApiError.InvalidKey) else ApiResult.Ok(listOf(file))
        }
    }

    @Test
    fun `in Both mode images 2 and 4 are painted from the filled template`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": ["architecture"]}"""),
            ApiResult.Ok(caching().replace("""{"heading": "TTL", "items": ["expire entries"]}""", """{"heading": "Read path", "type": "flow", "nodes": ["App", "Cache", "DB"]}""")),
        )
        val gemini = PaintingAgent()

        val outcome = GenerateImagesUseCase("{{prompt}}", { gemini }, 4, infographics = templated(writer, ImageStyle(audience = "devs", mode = ImageMode.BOTH), ImagePainter("Condense.")))
            .generate(ImageRequest("Caching"))

        assertEquals(4, (outcome as Outcome.Ok).value.size)
        assertEquals(2, outcome.value.count { it.name.startsWith("painted-") })
        // Images 1 and 3 are drawn: Workflow portrait and Architecture landscape.
        val plans = writer.calls.drop(2).map { it.second }
        assertEquals(2, plans.size)
        assertTrue(plans.any { it.contains("Image template: Workflow, portrait 2:3.") })
        assertTrue(plans.any { it.contains("Image template: Architecture, landscape 3:2.") })
        // Images 2 and 4 get the filled template in full (Gemini), in their format.
        assertEquals(
            setOf(
                "Rules for 1. Request flow in landscape 3:2 for devs." to ImageSize.LANDSCAPE,
                "Rules for 1. Request flow in portrait 2:3 for devs." to ImageSize.PORTRAIT,
            ),
            gemini.painted.toSet(),
        )
    }

    @Test
    fun `one image per request, and in Both mode it is the drawn one`() = runTest {
        assertEquals(1, IMAGES_PER_REQUEST)
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": ["architecture"]}"""),
            // A flow card passes the Workflow template's checks, so the definition is written once.
            ApiResult.Ok(caching().replace("""{"heading": "TTL", "items": ["expire entries"]}""", """{"heading": "Read path", "type": "flow", "nodes": ["App", "Cache", "DB"]}""")),
        )
        val gemini = PaintingAgent()

        val outcome = GenerateImagesUseCase("{{prompt}}", { gemini }, IMAGES_PER_REQUEST, infographics = templated(writer, ImageStyle(audience = "devs", mode = ImageMode.BOTH), ImagePainter("Condense.")))
            .generate(ImageRequest("Caching"))

        assertEquals(1, (outcome as Outcome.Ok).value.size)
        assertTrue(gemini.painted.none { it.first.startsWith("Rules for") })
        assertTrue(writer.calls.drop(2).single().second.contains("Image template: Workflow, portrait 2:3."))
    }

    /** A definition too long for one image at full text size, even as landscape. */
    private fun tooLong(): String {
        val cards = List(8) { i -> "{\"heading\": \"Part $i\", \"items\": [${List(5) { "\"point $it about caching entries and their expiry\"" }.joinToString()}]}" }
        return "{\"title\": \"Caching\", \"sections\": [${cards.joinToString()}]}"
    }

    private val carouselPages = listOf("Caching", "Reads", "Expiry", "Eviction", "Summary")

    private fun carouselReply(): String {
        val cover = "{\"title\": \"Caching\", \"subtitle\": \"What a cache does\"}"
        val pages = carouselPages.drop(1).map { "{\"title\": \"$it\", \"sections\": [{\"heading\": \"$it notes\", \"items\": [\"one point on $it\"]}]}" }
        return "{\"palette\": \"teal\", \"pages\": [${(listOf(cover) + pages).joinToString()}]}"
    }

    @Test
    fun `content too long for one image is condensed into one poster, never a carousel`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(tooLong()), ApiResult.Ok(caching()))
        val parts = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, carousel = CarouselWriter("Pages {{pages}}.{{examples}}"))

        val outcome = GenerateImagesUseCase("{{prompt}}", { null }, IMAGES_PER_REQUEST, infographics = parts)(" Caching ")

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), outcome)
        assertEquals(2, writer.calls.size)
        val retry = writer.calls[1].second
        assertTrue(retry, retry.contains("- it is too dense for one readable poster: its 8 cards need "))
        assertTrue(retry, retry.contains("units of height and the format holds 1620") && retry.contains("keep every point"))
        assertFalse(retry.contains("at most 11"))
        assertTrue(writer.calls.none { it.first.startsWith("Pages") })
    }

    @Test
    fun `the Carousel layout skips the single image`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": []}"""),
            ApiResult.Ok(carouselReply()),
        )
        val parts = templated(writer, ImageStyle(audience = "devs", layout = ImageLayout.CAROUSEL))
        val withCarousel = Infographics(parts.textAgent, parts.writer, pipeline, parts.content, templates = parts.templates, carousel = CarouselWriter("Pages."))

        val outcome = GenerateImagesUseCase("{{prompt}}", { null }, IMAGES_PER_REQUEST, infographics = withCarousel).generate(ImageRequest("Caching"))

        assertEquals(carouselPages.size, (outcome as Outcome.Ok).value.size)
        assertEquals(3, writer.calls.size)
        assertTrue(writer.calls[2].second.contains("Image template: Workflow."))
    }

    @Test
    fun `a poster still too long after condensing is drawn as one`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok(tooLong()), ApiResult.Ok(tooLong()))
        val parts = Infographics({ writer }, InfographicWriter("Plan.{{examples}}"), pipeline, carousel = CarouselWriter("Pages."))

        val outcome = GenerateImagesUseCase("{{prompt}}", { null }, IMAGES_PER_REQUEST, infographics = parts)(" Caching ")

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), outcome)
        assertEquals(2, writer.calls.size)
    }

    @Test
    fun `when the Carousel layout can't make pages one image is drawn`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": []}"""),
            ApiResult.Ok("no JSON here"),
            ApiResult.Ok("no JSON here"),
            ApiResult.Ok(caching()),
        )
        val parts = templated(writer, ImageStyle(audience = "devs", layout = ImageLayout.CAROUSEL))
        val withCarousel = Infographics(parts.textAgent, parts.writer, pipeline, parts.content, templates = parts.templates, carousel = CarouselWriter("Pages."))

        val outcome = GenerateImagesUseCase("{{prompt}}", { null }, IMAGES_PER_REQUEST, infographics = withCarousel).generate(ImageRequest("Caching"))

        assertEquals(1, (outcome as Outcome.Ok).value.size)
        // The carousel was asked for twice before the single image was written.
        assertEquals(2, writer.calls.count { it.first.startsWith("Pages.") })
        assertTrue(writer.calls.last().second.contains("Image template: Workflow"))
    }

    @Test
    fun `an agent that paints only squares is asked for squares and its images are labelled square`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": ["architecture"]}"""),
            ApiResult.Ok(caching().replace("""{"heading": "TTL", "items": ["expire entries"]}""", """{"heading": "Read path", "type": "flow", "nodes": ["App", "Cache", "DB"]}""")),
        )
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val flux = PaintingAgent(onlyFormat = ImageSize.SQUARE, dir = dir)

        val outcome = GenerateImagesUseCase("{{prompt}}", { flux }, 4, infographics = templated(writer, ImageStyle(audience = "devs", mode = ImageMode.BOTH), ImagePainter("Condense.")))
            .generate(ImageRequest("Caching"))

        // Images 2 and 4 would be landscape and portrait; this agent gets square briefs instead.
        assertEquals(
            listOf("Rules for 1. Request flow in square 1:1 for devs." to ImageSize.SQUARE).let { it + it },
            flux.painted,
        )
        val painted = (outcome as Outcome.Ok).value.mapNotNull(::imageLabel).filter { it.painted }
        assertEquals(listOf(ImageSize.SQUARE, ImageSize.SQUARE), painted.map { it.format })
        // The drawn images keep their own shapes.
        assertTrue(writer.calls.drop(2).any { it.second.contains("Image template: Workflow, portrait 2:3.") })
        dir.deleteRecursively()
    }

    @Test
    fun `drawn mode and a missing image agent draw every image`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))
        val gemini = PaintingAgent()

        GenerateImagesUseCase("{{prompt}}", { gemini }, 2, infographics = templated(writer, ImageStyle(mode = ImageMode.DRAWN), ImagePainter("C.")))
            .generate(ImageRequest("Caching", template = "concept"))
        GenerateImagesUseCase("{{prompt}}", { null }, 2, infographics = templated(writer, ImageStyle(mode = ImageMode.PAINTED), ImagePainter("C.")))
            .generate(ImageRequest("Caching", template = "concept"))

        assertTrue(gemini.painted.isEmpty())
        assertEquals(4, drawn.size)
    }

    @Test
    fun `a painted image that can't be made is drawn instead`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))
        val failing = PaintingAgent(fails = true)

        val outcome = GenerateImagesUseCase("{{prompt}}", { failing }, 1, infographics = templated(writer, ImageStyle(mode = ImageMode.PAINTED), ImagePainter("C.")))
            .generate(ImageRequest("Caching", template = "concept"))

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), outcome)
        assertEquals(1, failing.painted.size)
        assertEquals(listOf("Caching"), drawn)
    }

    @Test
    fun `auto mode keeps a painted poster the review passes`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"))
        val seeing = object : TextAgent by writer {
            override suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> = ApiResult.Ok("""{"score": 9}""")
        }
        val gemini = PaintingAgent()
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { gemini }, 1,
            infographics = templated(seeing, ImageStyle(mode = ImageMode.AUTO), ImagePainter("C."), ImageReviewer("R.", { byteArrayOf(1) })),
        )

        assertEquals(Outcome.Ok(listOf(File("painted-1.png"))), useCase.generate(ImageRequest("Caching", template = "concept")))
        assertTrue(drawn.isEmpty())
    }

    @Test
    fun `auto mode draws the poster when the painted one's text keeps failing the review`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))
        val scores = ArrayDeque(listOf("""{"score": 3, "problems": ["The word Cahce is misspelt"]}""", """{"score": 5}""", """{"score": 9}"""))
        val seeing = object : TextAgent by writer {
            override suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> =
                ApiResult.Ok(synchronized(scores) { scores.removeFirst() })
        }
        val gemini = PaintingAgent()
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { gemini }, 1,
            infographics = templated(seeing, ImageStyle(mode = ImageMode.AUTO), ImagePainter("C."), ImageReviewer("R.", { byteArrayOf(1) })),
        )

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), useCase.generate(ImageRequest("Caching", template = "concept")))
        assertEquals(2, gemini.painted.size)
        assertEquals(listOf("Caching"), drawn)
    }

    @Test
    fun `auto mode without a review draws the poster, since its text can't be checked`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))
        val gemini = PaintingAgent()

        val outcome = GenerateImagesUseCase("{{prompt}}", { gemini }, 1, infographics = templated(writer, ImageStyle(mode = ImageMode.AUTO), ImagePainter("C.")))
            .generate(ImageRequest("Caching", template = "concept"))

        assertEquals(Outcome.Ok(listOf(File("Caching.png"))), outcome)
        assertTrue(gemini.painted.isEmpty())
        assertEquals(listOf("Caching"), drawn)
    }

    @Test
    fun `a painted image the review scores too low is painted again with the review's problems`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"))
        val scores = ArrayDeque(listOf("""{"score": 3, "problems": ["The word Cahce is misspelt"]}""", """{"score": 9}"""))
        val requests = mutableListOf<String>()
        val seeing = object : TextAgent by writer {
            override suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> {
                requests += user
                return ApiResult.Ok(scores.removeFirst())
            }
        }
        val gemini = PaintingAgent()
        val upscaler = ImageUpscaler { File("big-${it.name}") }
        val useCase = GenerateImagesUseCase(
            "{{prompt}}", { gemini }, 1, upscaler = upscaler,
            infographics = templated(seeing, ImageStyle(mode = ImageMode.PAINTED), ImagePainter("C."), ImageReviewer("R.", { byteArrayOf(1) })),
        )

        assertEquals(Outcome.Ok(listOf(File("big-painted-2.png"))), useCase.generate(ImageRequest("Caching", template = "concept")))
        assertEquals(2, gemini.painted.size)
        assertTrue(gemini.painted[1].first.endsWith("Avoid these problems:\n- The word Cahce is misspelt"))
        assertTrue(requests[0].contains("skip problem 2"))
        assertTrue(requests[0].contains("Image template: Concept."))
        assertTrue(drawn.isEmpty())
    }

    @Test
    fun `the chooser picks templates for the content and each infographic follows its plan`() = runTest {
        val writer = ScriptedTextAgent(
            ApiResult.Ok("1. Request flow"),
            ApiResult.Ok("""{"primary": "workflow", "alternates": ["architecture"]}"""),
            // A flow card passes both the Workflow and the Architecture template's checks.
            ApiResult.Ok(caching().replace("""{"heading": "TTL", "items": ["expire entries"]}""", """{"heading": "Read path", "type": "flow", "nodes": ["App", "Cache", "DB"]}""")),
        )

        val outcome = GenerateImagesUseCase("{{prompt}}", { null }, 4, infographics = templated(writer)).generate(ImageRequest("Caching"))

        assertEquals(4, (outcome as Outcome.Ok).value.size)
        assertEquals("Technical content:\n1. Request flow", writer.calls[1].second)
        val plans = writer.calls.drop(2).map { it.second }
        listOf(
            "Image template: Workflow, portrait 2:3.",
            "Image template: Workflow, landscape 3:2.",
            "Image template: Architecture, landscape 3:2.",
            "Image template: Universal, portrait 2:3.",
        ).forEach { expected -> assertEquals(expected, 1, plans.count { it.contains(expected) }) }
        plans.forEach {
            assertTrue(it.contains("Rules for (the technical content above) in "))
            assertTrue(it.contains(" for devs."))
            assertEquals(1, Regex("1\\. Request flow").findAll(it).count())
        }
    }

    @Test
    fun `a picked template and format skip the chooser and apply to every image`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))

        GenerateImagesUseCase("{{prompt}}", { null }, 2, infographics = templated(writer))
            .generate(ImageRequest("Caching", template = "concept", format = ImageSize.LANDSCAPE))

        assertEquals(3, writer.calls.size)
        writer.calls.drop(1).forEach { assertTrue(it.second.contains("Image template: Concept, landscape 3:2.")) }
    }

    @Test
    fun `with the series style on every template is wrapped in the series rules`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok(caching()))
        val series = ImageTemplate("series", "Series Style", "never picked", "Series rules for {{content}}: follow {{style}} in {{format}}.")
        val parts = Infographics(
            { writer }, InfographicWriter("Plan.{{examples}}"), pipeline, ImageContentWriter("Write it all."),
            templates = Templates(templateList + series, TemplateChooser("Choose.{{templates}}", templateList), { ImageStyle(audience = "devs", styleGuide = "navy", series = true) }),
        )

        GenerateImagesUseCase("{{prompt}}", { null }, 1, infographics = parts).generate(ImageRequest("Caching", template = "concept"))

        val plan = writer.calls.last().second
        assertTrue(plan.contains("Rules for (the technical content above) in square 1:1 for devs."))
        assertTrue(plan.contains("Series rules for (the technical content above): follow navy in square 1:1."))
    }

    @Test
    fun `a failed choice falls back to the universal template`() = runTest {
        val writer = ScriptedTextAgent(ApiResult.Ok("1. Request flow"), ApiResult.Ok("no idea"), ApiResult.Ok(caching()))

        GenerateImagesUseCase("{{prompt}}", { null }, 1, infographics = templated(writer)).generate(ImageRequest("Caching"))

        assertTrue(writer.calls.last().second.contains("Image template: Universal, portrait 2:3."))
    }

    @Test
    fun `plans the primary in both formats, then an alternate and the universal template`() {
        val universal = template("universal")
        val choice = TemplateChoice(template("workflow"), listOf(template("architecture"), template("concept")))
        assertEquals(
            listOf(
                ImagePlan(template("workflow"), ImageSize.PORTRAIT),
                ImagePlan(template("workflow"), ImageSize.LANDSCAPE),
                ImagePlan(template("architecture"), ImageSize.LANDSCAPE),
                ImagePlan(universal, ImageSize.PORTRAIT),
            ),
            planImages(choice, 4, picked = false, universal = universal),
        )
        // A universal primary takes the second alternate last; a picked format replaces every format.
        assertEquals(
            listOf("universal", "universal", "workflow", "concept"),
            planImages(TemplateChoice(universal, listOf(template("workflow"), template("concept"))), 4, false, universal).map { it.template.id },
        )
        assertEquals(
            List(3) { ImageSize.SQUARE },
            planImages(TemplateChoice(template("concept")), 3, picked = true, universal = universal, format = ImageSize.SQUARE).map { it.format },
        )
    }

    @Test
    fun `every template has a layout`() {
        val ids = parseImageTemplates(File("src/main/assets/prompts/image/image_templates.md").readText()).map { it.id }
        assertEquals(ids.toSet(), TEMPLATE_LAYOUTS.keys)
    }
}

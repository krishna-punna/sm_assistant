package com.smassistant.app.image.render

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The poster text standard (AC-10): spacing, casing and punctuation of every drawn word. */
class TypographyTest {
    private val renderer = InfographicRenderer(ApproxTextMeasurer)

    @Test
    fun `spacing is one plain space, with no invisible characters, and real ellipses and dashes`() {
        assertEquals("Kafka Retries", Typography.clean("  Kafka​ \t  Retries "))
        assertEquals("Wait… then retry – twice — or park it", Typography.clean("Wait... then retry - twice -- or park it"))
        assertEquals("p99-latency", Typography.clean("p99-latency"))
    }

    @Test
    fun `titles are title case and keep acronyms and brand spelling`() {
        assertEquals("How Kafka Retries Work", Typography.title("how kafka retries work"))
        assertEquals("REST vs gRPC for iOS and the Web", Typography.title("REST vs gRPC for iOS and the web"))
        assertEquals("The Cost of a Cache Miss", Typography.title("the cost of a cache miss"))
        assertEquals("Using kubectl with JSON", Typography.title("using kubectl with JSON"))
    }

    @Test
    fun `shouting is calmed, acronyms stay`() {
        assertEquals("Kafka Consumer Groups", Typography.title("KAFKA CONSUMER GROUPS"))
        assertEquals("Cache every API response", Typography.sentence("CACHE EVERY API RESPONSE"))
        assertEquals("Use HTTP/2 for the API", Typography.sentence("use HTTP/2 for the API"))
    }

    @Test
    fun `body text is sentence case without touching terms that start lower case`() {
        assertEquals("Read the cache first", Typography.sentence("read the cache first"))
        assertEquals("iOS apps cache images", Typography.sentence("iOS apps cache images"))
        assertEquals("npm caches packages", Typography.sentence("npm caches packages"))
        assertEquals("p99 under 5 ms", Typography.sentence("p99 under 5 ms"))
    }

    @Test
    fun `the renderer applies the standard to every drawn word and leaves code alone`() {
        val spec = InfographicSpec(
            title = "kafka   retries",
            subtitle = "what happens when a consumer fails...",
            sections = listOf(
                Section("why retry", SectionBody.Bullets(listOf("at least once delivery"))),
                Section("code", SectionBody.Code("int  x = 1;   // keep  spacing")),
                Section("STEPS TO FOLLOW", SectionBody.Steps(listOf("back off  and retry"))),
            ),
            takeaway = "retry with backoff - then park it",
        )
        val svg = renderer.render(spec).svg
        val texts = Regex("<text[^>]*>(.*?)</text>").findAll(svg).map { it.groupValues[1].replace(Regex("</?tspan[^>]*>"), "") }.toList()
        assertTrue(texts.toString(), texts.any { it == "Kafka${WORD_GAP}Retries" })
        assertTrue(texts.toString(), texts.any { it.startsWith("What happens when a consumer fails…") })
        assertTrue(texts.toString(), texts.contains("Why Retry") && texts.contains("Steps to Follow"))
        assertTrue(texts.toString(), texts.contains("Back off and retry"))
        assertTrue(texts.toString(), texts.any { it.startsWith("Retry with backoff – then") })
        assertTrue(svg, svg.contains("int  x = 1;   // keep  spacing".replace(' ', '\u00A0')))
    }

    @Test
    fun `every pair of title words has a visible gap AndroidSVG can't drop, in every format`() {
        val titles = listOf("ArrayList Internals Explained", "REST vs gRPC", "Kafka Consumer Group Rebalancing Explained Simply")
        for (format in InfographicFormat.IMAGE) for (title in titles) {
            val svg = renderer.render(InfographicSpec(title = title, highlight = title.split(' ').last(), format = format,
                sections = listOf(Section("A", SectionBody.Bullets(listOf("a")))))).svg
            val words = Regex("""<text[^>]*font-weight="bold"[^>]*>((?:<tspan[^>]*>[^<]*</tspan>)+)</text>""").findAll(svg)
                .flatMap { line -> Regex("<tspan[^>]*>([^<]*)</tspan>").findAll(line.groupValues[1]).map { it.groupValues[1] }.toList().withIndex() }
                .toList()
            assertEquals("$format $title", title.split(' ').size, words.size)
            words.forEach { (i, word) ->
                // AndroidSVG drops a normal space that starts a <tspan>; every later word is led by a non-breaking one.
                if (i > 0) assertTrue("$format \"$word\"", word.first() == WORD_GAP && word.drop(1).isNotBlank())
                assertFalse("$format \"$word\"", word.startsWith(" ") || word.endsWith(" "))
            }
        }
    }

    @Test
    fun `the prompts and the review state the text standard`() {
        val prompts = File("src/main/assets/prompts/image")
        assertTrue(File(prompts, "infographic.md").readText().contains("never words run together"))
        assertTrue(File(prompts, "image.md").readText().contains("never run words together"))
        assertTrue(File(prompts, "image_review.md").readText().contains("words run together"))
        assertTrue(File(prompts, "split_image_prompt.md").readText().contains("never run together"))
    }
}

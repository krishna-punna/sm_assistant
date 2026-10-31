package com.smassistant.app.image.render

import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.image.ImageBrief
import com.smassistant.app.image.ImageTemplate
import com.smassistant.app.image.InfographicWriter
import com.smassistant.app.image.ScriptedTextAgent
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The richer header and footer (AC-9). */
class HeaderFooterTest {
    private val renderer = InfographicRenderer(ApproxTextMeasurer)
    private val cards = List(3) { Section("Card $it", SectionBody.Bullets(listOf("point $it about caching"))) }
    private val rich = InfographicSpec(
        title = "Caching Strategies",
        highlight = "Caching",
        subtitle = "Keep hot data close",
        hero = "a server holding plates",
        sections = cards,
        category = "System design",
        badges = listOf("Redis", "CDN"),
        takeaway = "Always set a TTL.",
        takeawayLabel = "Interview tip",
        footerArt = "a light bulb",
        recap = listOf(Recap("bolt", "Read cache first"), Recap("clock", "Set a TTL")),
        brand = "SM Assistant",
        handle = "@krishna",
        page = "1 / 2",
    )

    private fun texts(svg: String) = Regex("<text[^>]*>(.*?)</text>").findAll(svg)
        .map { it.groupValues[1].replace(Regex("</?tspan[^>]*>"), "").replace(' ', ' ') }.toList()

    @Test
    fun `the model's header and footer fields are read and capped`() {
        val spec = parseInfographic("""{"title": "Caching", "header_style": "Breakout", "category": "System design",
            "badges": ["Redis", "CDN", "Java 21", "Kafka", "Too many"], "takeaway_label": "Interview tip", "footer_art": "a bulb",
            "recap": [{"icon": "bolt", "label": "Read first"}, {"icon": "nope", "label": "Set a TTL"}, "Plain point", {"icon": "bolt"}, "4", "5"]}""").getOrThrow()
        assertEquals(HeaderStyle.BREAKOUT, spec.headerStyle)
        assertEquals("System design", spec.category)
        assertEquals(listOf("Redis", "CDN", "Java 21", "Kafka"), spec.badges)
        assertEquals("Interview tip", spec.takeawayLabel)
        assertEquals("a bulb", spec.footerArt)
        assertEquals(listOf(Recap("bolt", "Read first"), Recap("check", "Set a TTL"), Recap("check", "Plain point"), Recap("check", "4")), spec.recap)
        assertEquals(HeaderStyle.SPLIT, parseInfographic("""{"title": "x", "header_style": "wavy"}""").getOrThrow().headerStyle)
    }

    @Test
    fun `every part is drawn, the ribbon in capitals, the brand with the handle and the series number`() {
        val rendered = renderer.render(rich)
        val texts = texts(rendered.svg)
        listOf("SYSTEM DESIGN", "Redis", "CDN", "Keep hot data close", "INTERVIEW TIP", "Always set a TTL.", "Read cache first",
            "Set a TTL", "SM Assistant · @krishna", "1 / 2").forEach { assertTrue("$it in $texts", texts.any { t -> t.startsWith(it) }) }
        assertEquals(listOf("a server holding plates", "a light bulb"), rendered.slots.map { it.prompt })
        assertTrue(rendered.svg.contains("<linearGradient id=\"band\""))
        assertTrue(rendered.svg.contains("<path d=\"M0 ") && rendered.svg.contains(" T"))
    }

    @Test
    fun `each header style keeps everything on the page and the breakout picture below the band`() {
        for (style in HeaderStyle.entries) for (format in InfographicFormat.IMAGE) {
            val rendered = renderer.render(rich.copy(headerStyle = style, format = format))
            val numbers = Regex("""(?:x|y|width|height|cx|cy)="(-?[\d.]+)"""").findAll(rendered.svg).map { it.groupValues[1].toFloat() }
            assertTrue("$style $format", numbers.all { it >= 0 })
            rendered.slots.forEach { assertTrue("$style $format", it.x + it.width <= rendered.width && it.y + it.height <= rendered.height) }
        }
        val band = Regex("""<rect x="0" y="0" width="\d+" height="([\d.]+)" fill="url\(#band\)"""")
        val breakout = renderer.render(rich.copy(headerStyle = HeaderStyle.BREAKOUT))
        val hero = breakout.slots.first()
        assertTrue("the picture breaks out below the band", hero.y + hero.height > band.find(breakout.svg)!!.groupValues[1].toFloat())
        val split = renderer.render(rich)
        assertTrue(split.slots.first().let { it.y + it.height } < band.find(split.svg)!!.groupValues[1].toFloat())
        val centered = renderer.render(rich.copy(headerStyle = HeaderStyle.CENTERED))
        assertEquals(centered.width / 2f, centered.slots.first().let { it.x + it.width / 2 }, 1f)
    }

    @Test
    fun `a poster without the extras still gets a footer only when there is something to show`() {
        val bare = renderer.render(InfographicSpec(title = "Caching", sections = cards))
        assertTrue(!bare.svg.contains("url(#foot)"))
        val page = renderer.render(InfographicSpec(title = "Caching", sections = cards, page = "2 / 3"))
        assertTrue(page.svg.contains("url(#foot)") && texts(page.svg).contains("2 / 3"))
        assertTrue(texts(renderer.render(rich.copy(takeawayLabel = null)).svg).contains("KEY TAKEAWAY"))
    }

    @Test
    fun `the brief's handle reaches the poster and the prompt asks for the new fields`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""{"title": "Kafka Retries", "sections": [{"heading": "A", "items": ["a"]}, {"heading": "B", "items": ["b"]}, {"heading": "C", "items": ["c"]}]}"""))
        val brief = ImageBrief(ImageTemplate("t", "T", "", "{{content}}"), "", ImageSize.SQUARE, ImageStyle(brandName = "Acme", handle = " @acme "))
        val spec = (InfographicWriter("Plan.{{examples}}").write(agent, "Kafka retries", "idea", brief = brief) as ApiResult.Ok).value
        assertEquals("@acme", spec.handle)

        val prompt = File("src/main/assets/prompts/image/infographic.md").readText()
        listOf("header_style", "category", "badges", "takeaway_label", "footer_art", "recap").forEach { assertTrue(it, prompt.contains("\"$it\"")) }
    }
}

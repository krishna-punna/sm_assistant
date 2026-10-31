package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.infographic.InfographicFormat
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.MAX_CAROUSEL_PAGES
import com.smassistant.app.infographic.Palette
import com.smassistant.app.infographic.carouselProblems
import com.smassistant.app.infographic.numbered
import com.smassistant.app.infographic.parseCarousel
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The carousel model, its checks and its writer (docs/design/single-image-carousel.md, SC-I3). */
class CarouselTest {
    private fun page(title: String, sections: Int = 1, takeaway: String? = null): String {
        val cards = List(sections) { "{\"heading\": \"$title part $it\", \"type\": \"bullets\", \"items\": [\"point $it of $title\"]}" }
        val last = takeaway?.let { ", \"takeaway\": \"$it\"" }.orEmpty()
        return "{\"title\": \"$title\"$last, \"sections\": [${cards.joinToString()}]}"
    }

    private fun carousel(contentPages: Int = 3, sectionsPerPage: Int = 1): String {
        val cover = "{\"title\": \"Caching Basics\", \"subtitle\": \"What a cache does\", \"hero\": \"a shelf of boxes\"}"
        val pages = listOf(cover) + List(contentPages) { page("Step $it", sectionsPerPage) } + page("Summary", takeaway = "Cache wisely")
        return "{\"palette\": \"teal\", \"takeaway\": \"Cache wisely\", \"pages\": [${pages.joinToString()}]}"
    }

    @Test
    fun `parses square pages in one palette, at most ten`() {
        val parsed = parseCarousel(carousel()).getOrThrow()
        assertEquals(5, parsed.pages.size)
        assertTrue(parsed.pages.all { it.format == InfographicFormat.SQUARE && it.palette == Palette.TEAL })
        assertEquals("Cache wisely", parsed.takeaway)
        assertEquals(MAX_CAROUSEL_PAGES, parseCarousel(carousel(contentPages = 15)).getOrThrow().pages.size)
        assertTrue(parseCarousel("""{"pages": []}""").isFailure)
    }

    @Test
    fun `checks the page count, the cover title, full pages and repeated titles`() {
        assertEquals(emptyList<String>(), carouselProblems(parseCarousel(carousel()).getOrThrow(), "Caching"))

        val short = parseCarousel(carousel(contentPages = 1)).getOrThrow()
        assertTrue(carouselProblems(short, "Caching").single().contains("3 pages"))
        assertTrue(carouselProblems(parseCarousel(carousel()).getOrThrow(), "Kafka").single().contains("cover"))

        val crowded = parseCarousel(carousel(sectionsPerPage = 3)).getOrThrow()
        assertEquals(3, carouselProblems(crowded, "Caching").count { "at most 2" in it })

        val tooFull = carouselProblems(parseCarousel(carousel()).getOrThrow(), "Caching") { it.title != "Step 1" }
        assertTrue(tooFull.single().startsWith("page 3 is too full"))
    }

    @Test
    fun `the writer asks again with the problems, then brands every page`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok(carousel(contentPages = 1)), ApiResult.Ok(carousel()))
        val template = ImageTemplate("workflow", "Workflow", "use", "Rules for {{content}} in {{format}}.")
        val brief = ImageBrief(template, "content", ImageFormat.PORTRAIT, ImageStyle(brandName = "SM"))

        val result = CarouselWriter("Pages: {{pages}}.\n{{examples}}").write(agent, "Caching", "1. Read path", brief)

        val pages = (result as ApiResult.Ok).value.pages
        assertEquals(5, pages.size)
        assertTrue(pages.all { it.brand == "SM" })
        assertEquals(2, agent.calls.size)
        assertTrue(agent.calls[0].first.startsWith("Pages: 10."))
        assertTrue(agent.calls[0].second.contains("Image template: Workflow."))
        assertTrue(agent.calls[1].second.contains("write a cover, at least 2 content pages and a summary"))
    }

    @Test
    fun `pages are numbered, the cover says swipe, and the label is drawn`() {
        val numbered = parseCarousel(carousel()).getOrThrow().numbered()
        assertEquals(listOf("Swipe →", "2 / 5", "3 / 5", "4 / 5", "5 / 5"), numbered.pages.map { it.page })
        assertTrue(">2 / 5<" in InfographicRenderer().render(numbered.pages[1]).svg)
    }

    @Test
    fun `carousel files keep their order and are told apart from separate images`() {
        val pages = (1..3).map { File("carousel-42-p${it.toString().padStart(2, '0')}-page.png") }
        assertEquals(CarouselPage(42, 3), carouselPage(pages[2]))
        assertTrue(isCarousel(pages))
        assertFalse(isCarousel(pages.take(1)))
        assertFalse(isCarousel(listOf(pages[0], File("tpl-concept-portrait-a.png"))))
    }

    @Test
    fun `carousel prompt keeps its placeholders and page rules`() {
        val text = File("src/main/assets/prompts/image/carousel.md").readText()
        assertTrue("{{pages}}" in text && "{{examples}}" in text)
        assertTrue("Page 1 is the cover" in text)
        assertFalse("{{slides}}" in text)
    }
}

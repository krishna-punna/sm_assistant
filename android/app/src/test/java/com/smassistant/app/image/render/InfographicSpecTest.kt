package com.smassistant.app.image.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfographicSpecTest {
    @Test
    fun `reads every section type from a fenced reply`() {
        val reply = """
            Here is the definition:
            ```json
            {
              "title": "ArrayList Internals", "highlight": "Internals", "subtitle": "Behind the scenes",
              "format": "square", "palette": "dark", "icon": "coffee", "hero": "a coffee cup on boxes",
              "takeaway": "Pre-size it when you know the size.",
              "sections": [
                {"heading": "What", "icon": "book", "type": "bullets", "items": ["Resizable", {"text": "Ordered"}]},
                {"heading": "add()", "type": "steps", "items": ["Check capacity", "Grow", "Store"]},
                {"heading": "Use", "type": "pros_cons", "pros": ["Fast get"], "cons": ["Slow insert"]},
                {"heading": "Code", "type": "code", "language": "java", "code": "List<String> l = new ArrayList<>();"},
                {"heading": "Cost", "type": "table", "columns": ["Op", "Time"], "rows": [["get", "O(1)"], ["add", "O(1)*"]]},
                {"heading": "Flow", "type": "flow", "nodes": ["add", "grow", "copy"]},
                {"heading": "Size", "type": "compare", "left": {"label": "Default", "items": ["10"]}, "right": {"items": ["n"]}},
                {"heading": "Numbers", "type": "stats", "items": [{"value": "1.5x", "label": "growth"}]}
              ]
            }
            ```
        """.trimIndent()

        val spec = parseInfographic(reply).getOrThrow()

        assertEquals("ArrayList Internals", spec.title)
        assertEquals(InfographicFormat.SQUARE, spec.format)
        assertEquals(Palette.DARK, spec.palette)
        assertEquals("coffee", spec.icon)
        assertEquals("a coffee cup on boxes", spec.hero)
        assertEquals(
            listOf(
                SectionBody.Bullets(listOf("Resizable", "Ordered")),
                SectionBody.Steps(listOf("Check capacity", "Grow", "Store")),
                SectionBody.ProsCons(listOf("Fast get"), listOf("Slow insert")),
                SectionBody.Code("List<String> l = new ArrayList<>();", "java"),
                SectionBody.Table(listOf("Op", "Time"), listOf(listOf("get", "O(1)"), listOf("add", "O(1)*"))),
                SectionBody.Flow(listOf("add", "grow", "copy")),
                SectionBody.Compare(SectionBody.Compare.Side("Default", listOf("10")), SectionBody.Compare.Side("After", listOf("n"))),
                SectionBody.Stats(listOf(SectionBody.Stats.Stat("1.5x", "growth"))),
            ),
            spec.sections.map { it.body },
        )
        assertEquals("book", spec.sections[0].icon)
    }

    @Test
    fun `unknown values fall back and empty sections are dropped`() {
        val spec = parseInfographic(
            """{"title": "T", "format": "poster", "palette": "rainbow", "icon": "unicorn",
               "sections": [{"heading": "A", "type": "mystery", "items": ["x"]}, {"heading": "B", "type": "table"}, {"type": "flow", "nodes": ["one"]}]}""",
        ).getOrThrow()

        assertEquals(InfographicFormat.PORTRAIT, spec.format)
        assertEquals(Palette.NAVY, spec.palette)
        assertNull(spec.icon)
        assertEquals(listOf(SectionBody.Bullets(listOf("x"))), spec.sections.map { it.body })
    }

    @Test
    fun `long lists are cut to the limits`() {
        val items = (1..20).joinToString(",") { "\"item $it\"" }
        val sections = (1..20).joinToString(",") { """{"heading": "S$it", "items": [$items]}""" }
        val spec = parseInfographic("""{"title": "T", "sections": [$sections]}""").getOrThrow()

        assertEquals(16, Limits.SECTIONS)
        assertEquals(Limits.SECTIONS, spec.sections.size)
        assertEquals(4, spec.droppedSections)
        assertEquals(Limits.ITEMS, (spec.sections[0].body as SectionBody.Bullets).items.size)
    }

    @Test
    fun `16 cards are all kept and nothing is counted as dropped`() {
        val sections = (1..16).joinToString(",") { """{"heading": "S$it", "items": ["a point"]}""" }
        val spec = parseInfographic("""{"title": "T", "sections": [$sections]}""").getOrThrow()
        assertEquals(16, spec.sections.size)
        assertEquals(0, spec.droppedSections)
    }

    @Test
    fun `fails without JSON or a title`() {
        assertTrue(parseInfographic("Sorry, I can't.").isFailure)
        assertTrue(parseInfographic("""{"subtitle": "no title"}""").isFailure)
        assertTrue(parseInfographic("""{"title": "cut off", "sections": [""").isFailure)
    }

    @Test
    fun `an image is never given a video size`() {
        assertEquals(listOf("portrait", "square", "landscape"), InfographicFormat.entries.map { it.key })
        assertEquals(InfographicFormat.PORTRAIT, parseInfographic("""{"title": "T", "format": "vertical"}""").getOrThrow().format)
        assertEquals(InfographicFormat.PORTRAIT, parseInfographic("""{"title": "T", "format": "wide"}""").getOrThrow().format)
    }

    @Test
    fun `any card reads an optional art prompt, and an illustration card keeps its own picture (AC-8)`() {
        val spec = parseInfographic("""{"title": "Caching", "sections": [
            {"heading": "Latency", "items": ["fast"], "art": "a stopwatch"},
            {"heading": "Plain", "items": ["no picture"], "art": "  "},
            {"heading": "Picture", "type": "illustration", "prompt": "a cache", "art": "ignored"}]}""").getOrThrow()
        assertEquals(listOf("a stopwatch", null, null), spec.sections.map { it.art })
    }
}

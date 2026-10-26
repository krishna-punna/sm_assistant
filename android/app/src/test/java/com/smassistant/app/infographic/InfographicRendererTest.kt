package com.smassistant.app.infographic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfographicRendererTest {
    private val renderer = InfographicRenderer(ApproxTextMeasurer)

    private val sections = listOf(
        Section("What it is", SectionBody.Bullets(listOf("A resizable array", "Keeps insertion order")), icon = "book"),
        Section("add(e)", SectionBody.Steps(listOf("Check capacity", "Grow by 1.5x if full", "Store the element"))),
        Section("Cost", SectionBody.Table(listOf("Operation", "Time", "Why"), listOf(listOf("get(i)", "O(1)", "index"), listOf("add", "O(1)*", "amortised")))),
        Section("Code", SectionBody.Code("List<String> names = new ArrayList<>(100);\n    names.add(\"Ada\"); // grows when full")),
        Section("How it grows", SectionBody.Flow(listOf("add", "full?", "new array 1.5x", "copy", "store"))),
        Section("Sizing", SectionBody.Compare(SectionBody.Compare.Side("Before", listOf("new ArrayList<>()")), SectionBody.Compare.Side("After", listOf("new ArrayList<>(n)")))),
        Section("Numbers", SectionBody.Stats(listOf(SectionBody.Stats.Stat("1.5x", "growth"), SectionBody.Stats.Stat("10", "default capacity")))),
        Section("Picture", SectionBody.Illustration("boxes in a row growing", "Elements live in one array")),
    )

    private val spec = InfographicSpec(
        title = "ArrayList Internals",
        highlight = "Internals",
        subtitle = "Behind the scenes of Java's resizable array",
        hero = "a coffee cup on a stack of boxes",
        sections = sections,
        takeaway = "Pre-size the list when you know how many elements it will hold.",
    )

    @Test
    fun `draws every part as SVG with the illustration slots`() {
        val rendered = renderer.render(spec)

        assertTrue(rendered.svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""))
        assertTrue(rendered.svg.endsWith("</svg>"))
        assertEquals(1080, rendered.width)
        assertTrue(rendered.height >= 1620)
        assertTrue(rendered.svg.contains("""<tspan fill="#FACC15"> Internals</tspan>"""))
        assertTrue(rendered.svg.contains("List&lt;String&gt;"))
        assertTrue(rendered.svg.contains("Pre-size the list"))
        assertEquals(listOf("a coffee cup on a stack of boxes", "boxes in a row growing"), rendered.slots.map { it.prompt })
        assertFalse(rendered.svg.contains("<foreignObject") || rendered.svg.contains("<image") || rendered.svg.contains("class="))
    }

    @Test
    fun `everything stays inside the canvas`() {
        for (format in InfographicFormat.entries) {
            val rendered = renderer.render(spec.copy(format = format))
            val numbers = Regex("""(?:x|y|width|height|cx|cy)="(-?[\d.]+)"""").findAll(rendered.svg).map { it.groupValues[1].toFloat() }
            assertTrue("$format: negative coordinate", numbers.all { it >= 0 })
            rendered.slots.forEach {
                assertTrue(it.x + it.width <= rendered.width && it.y + it.height <= rendered.height)
            }
        }
    }

    @Test
    fun `short content is padded to the format and long content grows taller`() {
        val short = spec.copy(sections = sections.take(1), hero = null)
        assertEquals(1620, renderer.render(short).height)
        assertEquals(1080, renderer.render(short.copy(format = InfographicFormat.SQUARE)).height)
        renderer.render(spec.copy(format = InfographicFormat.SQUARE)).let { assertEquals(1080, it.width); assertTrue(it.height > 1080) }
        renderer.render(short.copy(format = InfographicFormat.LANDSCAPE)).let { assertEquals(1620, it.width); assertEquals(1080, it.height) }
    }

    @Test
    fun `wraps text to the width and ends cut text with an ellipsis`() {
        val lines = wrapText("the quick brown fox jumps over the lazy dog", 200f, 24f, ApproxTextMeasurer)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { ApproxTextMeasurer.width(it, 24f, false, FontFamily.SANS) <= 200f })
        assertEquals("the quick brown fox jumps over the lazy dog", lines.joinToString(" "))

        val cut = wrapText("the quick brown fox jumps over the lazy dog", 200f, 24f, ApproxTextMeasurer, maxLines = 2)
        assertEquals(2, cut.size)
        assertTrue(cut.last().endsWith("…"))

        assertTrue(wrapText("Supercalifragilisticexpialidocious", 100f, 24f, ApproxTextMeasurer).size > 1)
    }

    @Test
    fun `mixes colours`() {
        assertEquals("#FFFFFF", mix("#000000", "#FFFFFF", 1f))
        assertEquals("#808080", mix("#000000", "#FFFFFF", 0.5f))
    }
}

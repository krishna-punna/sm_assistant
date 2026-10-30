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
        assertTrue(rendered.width >= 1080)
        assertEquals(rendered.width * 3 / 2f, rendered.height.toFloat(), 1f)
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
    fun `short content is padded to the format and long content is laid out wider at the same ratio`() {
        val short = spec.copy(sections = sections.take(1), hero = null)
        assertEquals(1620, renderer.render(short).height)
        assertEquals(1080, renderer.render(short.copy(format = InfographicFormat.SQUARE)).height)
        renderer.render(short.copy(format = InfographicFormat.LANDSCAPE)).let { assertEquals(1620, it.width); assertEquals(1080, it.height) }
        // The video sizes (docs/design/video-templates.md): 9:16 and 16:9.
        renderer.render(short.copy(format = InfographicFormat.VERTICAL)).let { assertEquals(1080, it.width); assertEquals(1920, it.height) }
        renderer.render(short.copy(format = InfographicFormat.WIDE)).let { assertEquals(1920, it.width); assertEquals(1080, it.height) }
        for ((format, ratio) in listOf(InfographicFormat.PORTRAIT to 1.5f, InfographicFormat.LANDSCAPE to 2 / 3f)) {
            renderer.render(spec.copy(format = format)).let {
                assertTrue("$format", it.width >= format.width)
                assertEquals("$format", it.width * ratio, it.height.toFloat(), 1f)
            }
        }
        // The whole cheat sheet on a square doesn't fit 1080 x 1080, so it is laid out wider.
        val half = spec.copy(format = InfographicFormat.SQUARE, sections = sections.take(4))
        renderer.render(half).let {
            assertTrue(it.width > 1080)
            assertEquals(it.width, it.height)
        }
    }

    @Test
    fun `content too long even for a page twice as wide is centred on a page widened to the ratio`() {
        val long = spec.copy(sections = List(8) { Section("Card $it", SectionBody.Table(listOf("A", "B", "C"), List(7) { i -> listOf("cell $i", "cell", "cell") })) })
        renderer.render(long.copy(format = InfographicFormat.SQUARE)).let {
            assertTrue(it.width > 2160)
            assertEquals(it.width.toFloat(), it.height.toFloat(), 1f)
        }
    }

    @Test
    fun `the brand name is drawn in the footer, with or without a takeaway`() {
        assertTrue(renderer.render(spec.copy(brand = "Acme Labs")).svg.contains(">Acme Labs<"))
        val noTakeaway = renderer.render(spec.copy(sections = sections.take(1), takeaway = null, brand = "Acme Labs"))
        assertTrue(noTakeaway.svg.contains(">Acme Labs<"))
        assertFalse(renderer.render(spec.copy(brand = "  ")).svg.contains("Acme"))
    }

    @Test
    fun `chart and diagram sections stay inside their cards`() {
        val chart = SectionBody.Chart(
            listOf(
                SectionBody.Chart.Bar("Redis", "0.5 ms", 0.5),
                SectionBody.Chart.Bar("Postgres on a managed cloud instance", "12 ms", 12.0),
                SectionBody.Chart.Bar("S3", "120 ms", 120.0),
            ),
            "p99 read latency",
        )
        // A skip-ahead and a backward arrow, so both side lanes are drawn.
        val diagram = SectionBody.Diagram(
            listOf(
                SectionBody.Diagram.Node("api", "API Gateway", "Edge"),
                SectionBody.Diagram.Node("orders", "Order Service", "Backend"),
                SectionBody.Diagram.Node("db", "Orders DB", "Backend"),
                SectionBody.Diagram.Node("queue", "Kafka", "Messaging"),
            ),
            listOf(
                SectionBody.Diagram.Edge("api", "orders", "REST"),
                SectionBody.Diagram.Edge("orders", "db", "SQL"),
                SectionBody.Diagram.Edge("api", "db"),
                SectionBody.Diagram.Edge("db", "queue", "CDC events"),
                SectionBody.Diagram.Edge("queue", "orders", "retry"),
            ),
        )
        val palette = Palette.NAVY
        val card = Regex("""<rect x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)" rx="18" fill="${palette.card}" stroke=""")
        for (format in InfographicFormat.entries) {
            val sections = listOf(Section("Latency", chart), Section("Order flow", diagram), Section("Notes", SectionBody.Bullets(listOf("point"))))
            val svg = renderer.render(InfographicSpec("Orders", format = format, palette = palette, sections = sections)).svg
            val cards = card.findAll(svg).toList()
            assertEquals("$format", 3, cards.size)
            for (i in 0..1) {
                val (x, y, w, h) = cards[i].groupValues.drop(1).map { it.toFloat() }
                val inside = svg.substring(cards[i].range.last + 1, cards[i + 1].range.first)
                drawnBounds(inside).forEach { (left, top, right, bottom) ->
                    assertTrue("$format ${sections[i].heading}: $left,$top..$right,$bottom outside $x,$y ${w}x$h",
                        left >= x - 0.5f && top >= y - 0.5f && right <= x + w + 0.5f && bottom <= y + h + 0.5f)
                }
            }
        }
    }

    /** Left, top, right and bottom of each rect, circle, text (measured as the renderer does), arrow and arrow head. */
    private fun drawnBounds(svg: String): List<List<Float>> {
        fun Regex.all() = findAll(svg).map { m -> m.groupValues.drop(1) }
        val rects = Regex("""<rect x="([\d.]+)" y="([\d.]+)" width="([\d.]+)" height="([\d.]+)"""").all()
            .map { it.map(String::toFloat) }.map { (x, y, w, h) -> listOf(x, y, x + w, y + h) }
        val circles = Regex("""<circle cx="([\d.]+)" cy="([\d.]+)" r="([\d.]+)"""").all()
            .map { it.map(String::toFloat) }.map { (cx, cy, r) -> listOf(cx - r, cy - r, cx + r, cy + r) }
        val texts = Regex("""<text x="([\d.]+)" y="([\d.]+)" font-family="[^"]+" font-size="([\d.]+)"([^>]*)>([^<]*)</text>""").all().map { (x, y, size, rest, text) ->
            val width = ApproxTextMeasurer.width(text, size.toFloat(), "bold" in rest, FontFamily.SANS)
            val left = when {
                "text-anchor=\"middle\"" in rest -> x.toFloat() - width / 2
                "text-anchor=\"end\"" in rest -> x.toFloat() - width
                else -> x.toFloat()
            }
            listOf(left, y.toFloat() - size.toFloat(), left + width, y.toFloat())
        }
        val points = Regex("""(?:d|points)="([^"]+)"""").all().flatMap { (list) ->
            Regex("""(-?[\d.]+)[ ,](-?[\d.]+)""").findAll(list).map { val (px, py) = it.destructured; listOf(px.toFloat(), py.toFloat(), px.toFloat(), py.toFloat()) }
        }
        return (rects + circles + texts + points).toList()
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

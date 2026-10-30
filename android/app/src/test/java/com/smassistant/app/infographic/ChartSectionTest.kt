package com.smassistant.app.infographic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartSectionTest {
    private fun parse(section: String) = parseInfographic("""{"title": "Latency", "sections": [$section]}""").getOrThrow().sections

    @Test
    fun `reads bars with the number from each value and keeps the value as written`() {
        val sections = parse(
            """{"heading": "p99", "type": "chart", "unit": "milliseconds", "bars": [
                {"label": "Redis", "value": "0.5 ms"}, {"label": "Postgres", "value": "1,200 ms"}, {"label": "No number", "value": "fast"}]}""",
        )
        assertEquals(
            SectionBody.Chart(
                listOf(SectionBody.Chart.Bar("Redis", "0.5 ms", 0.5), SectionBody.Chart.Bar("Postgres", "1,200 ms", 1200.0)),
                "milliseconds",
            ),
            sections.single().body,
        )
    }

    @Test
    fun `fewer than two usable bars is no chart`() {
        assertTrue(parse("""{"heading": "One", "type": "chart", "bars": [{"label": "a", "value": "3"}]}""").isEmpty())
        // "items" works as well as "bars".
        assertEquals(2, (parse("""{"heading": "Two", "type": "chart", "items": [{"label": "a", "value": "3"}, {"label": "b", "value": "6"}]}""")
            .single().body as SectionBody.Chart).bars.size)
    }

    @Test
    fun `draws the bars to scale with their values`() {
        val chart = SectionBody.Chart(listOf(SectionBody.Chart.Bar("Redis", "5 ms", 5.0), SectionBody.Chart.Bar("Postgres", "10 ms", 10.0)), "p99")
        val spec = InfographicSpec("Latency", sections = listOf(Section("p99", chart)) + List(2) { Section("Card $it", SectionBody.Bullets(listOf("point"))) })
        val svg = InfographicRenderer().render(spec).svg

        listOf(">Redis<", ">5 ms<", ">10 ms<", ">p99<").forEach { assertTrue(it, svg.contains(it)) }
        // The 10 ms bar is twice as long as the 5 ms one.
        val fill = spec.palette.accent(0)
        val widths = Regex("""<rect [^>]*width="([\d.]+)" height="24" rx="6" fill="$fill"""").findAll(svg).map { it.groupValues[1].toFloat() }.toList()
        assertEquals(2, widths.size)
        assertEquals(widths[0] * 2, widths[1], 1f)
    }
}

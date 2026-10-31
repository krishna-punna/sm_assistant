package com.smassistant.app.video.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagramSectionTest {
    private fun parse(section: String) = parseInfographic("""{"title": "Orders", "sections": [$section]}""").getOrThrow().sections

    private val orders = SectionBody.Diagram(
        listOf(
            SectionBody.Diagram.Node("api", "API Gateway", "Edge"),
            SectionBody.Diagram.Node("orders", "Order Service", "Backend"),
            SectionBody.Diagram.Node("db", "Orders DB", "Backend"),
            SectionBody.Diagram.Node("queue", "Kafka", "Backend"),
        ),
        listOf(
            SectionBody.Diagram.Edge("api", "orders", "REST"),
            SectionBody.Diagram.Edge("orders", "db"),
            SectionBody.Diagram.Edge("orders", "queue", "async"),
        ),
    )

    @Test
    fun `reads nodes and edges by id or label and drops bad edges`() {
        val sections = parse(
            """{"heading": "Flow", "type": "diagram",
                "nodes": [{"id": "api", "label": "API Gateway", "group": "Edge"}, {"id": "orders", "label": "Order Service", "group": "Backend"},
                  {"id": "db", "label": "Orders DB", "group": "Backend"}, {"label": "Kafka", "group": "Backend"}],
                "edges": [{"from": "api", "to": "orders", "label": "REST"}, ["Order Service", "db"], {"from": "orders", "to": "kafka", "label": "async"},
                  {"from": "api", "to": "orders"}, {"from": "db", "to": "db"}, {"from": "db", "to": "nowhere"}]}""",
        )
        assertEquals(orders.copy(nodes = orders.nodes.map { if (it.id == "queue") it.copy(id = "Kafka") else it }, edges = orders.edges.map { if (it.to == "queue") it.copy(to = "Kafka") else it }), sections.single().body)
    }

    @Test
    fun `a single node is no diagram`() {
        assertTrue(parse("""{"heading": "One", "type": "diagram", "nodes": ["only"]}""").isEmpty())
    }

    @Test
    fun `layers follow the longest chain of arrows and ignore cycles`() {
        assertEquals(mapOf("api" to 0, "orders" to 1, "db" to 2, "queue" to 2), diagramLayers(orders))
        val loop = SectionBody.Diagram(
            listOf(SectionBody.Diagram.Node("a", "A"), SectionBody.Diagram.Node("b", "B"), SectionBody.Diagram.Node("c", "C")),
            listOf(SectionBody.Diagram.Edge("a", "b"), SectionBody.Diagram.Edge("b", "c"), SectionBody.Diagram.Edge("c", "a")),
        )
        assertEquals(setOf(0, 1, 2), diagramLayers(loop).values.toSet())
    }

    @Test
    fun `draws boxes, arrows, edge labels and the group legend inside the canvas`() {
        for (format in InfographicFormat.entries) {
            val spec = InfographicSpec("Orders", format = format, sections = listOf(Section("Flow", orders)) + List(2) { Section("Card $it", SectionBody.Bullets(listOf("point"))) })
            val rendered = InfographicRenderer().render(spec)
            listOf(">API Gateway<", ">Kafka<", ">REST<", ">async<", ">Edge<", ">Backend<").forEach { assertTrue("$format $it", rendered.svg.contains(it)) }
            assertEquals(3, Regex("<polygon").findAll(rendered.svg).count())
            val numbers = Regex("""(?:x|y|width|height)="(-?[\d.]+)"""").findAll(rendered.svg).map { it.groupValues[1].toFloat() }
            assertTrue(numbers.all { it >= 0 })
        }
    }
}

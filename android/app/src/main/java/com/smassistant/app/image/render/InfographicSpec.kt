package com.smassistant.app.image.render

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The structured definition of one infographic (docs/design/image-video-pipeline.md): what the
 * text agent writes as JSON, and what [InfographicRenderer] draws. All text is drawn by the app,
 * so it is always spelled right; only [hero] and [SectionBody.Illustration] are AI-painted.
 */
data class InfographicSpec(
    val title: String,
    /** Words of [title] drawn in the highlight colour, e.g. "Internals" in "ArrayList Internals". */
    val highlight: String? = null,
    val subtitle: String? = null,
    val format: InfographicFormat = InfographicFormat.PORTRAIT,
    val palette: Palette = Palette.NAVY,
    /** A built-in icon name ([ICONS]) shown in the header when there is no [hero]. */
    val icon: String? = null,
    /** Prompt for an AI illustration in the header, e.g. "a coffee cup on a stack of boxes". */
    val hero: String? = null,
    val sections: List<Section> = emptyList(),
    val takeaway: String? = null,
    /** The user's brand name (app.yml `image_style`), drawn small in the footer; never written by the model. */
    val brand: String? = null,
    /**
     * For a video's step-by-step reveal (docs/tasks VT-I5): only this many sections are drawn, each
     * where the whole layout puts it, and the footer only once all are shown. Null draws everything.
     */
    val revealed: Int? = null,
    /** A series poster's label, drawn small in the header's corner: "2 / 3" (AC-7). */
    val page: String? = null,
    /**
     * Cards the model wrote beyond [Limits.SECTIONS] that were left out (AC-2). Never silent: the
     * spec checks report it, so the writer is asked to merge them instead of losing them.
     */
    val droppedSections: Int = 0,
)

enum class InfographicFormat(val key: String, val width: Int) {
    /** Tall cheat sheet like most sample images; at least 2:3 and taller when the content needs it. */
    PORTRAIT("portrait", 1080),

    /** 1:1, for grids of a few equal cards. */
    SQUARE("square", 1080),

    /** 3:2 wide poster for architectures. */
    LANDSCAPE("landscape", 1620),
    ;

    companion object {
        /** The formats an image may have: 2:3, 1:1 and 3:2, and no video size (docs/requirements/19, SP-3). */
        val IMAGE = listOf(PORTRAIT, SQUARE, LANDSCAPE)
    }
}

data class Section(val heading: String, val body: SectionBody, val icon: String? = null)

sealed interface SectionBody {
    data class Bullets(val items: List<String>) : SectionBody
    data class Steps(val items: List<String>) : SectionBody
    data class ProsCons(val pros: List<String>, val cons: List<String>) : SectionBody
    data class Code(val code: String, val language: String? = null) : SectionBody
    data class Table(val columns: List<String>, val rows: List<List<String>>) : SectionBody
    data class Flow(val nodes: List<String>) : SectionBody
    data class Compare(val left: Side, val right: Side) : SectionBody {
        data class Side(val label: String, val items: List<String>)
    }
    data class Stats(val items: List<Stat>) : SectionBody {
        data class Stat(val value: String, val label: String)
    }
    data class Illustration(val prompt: String, val caption: String? = null) : SectionBody

    /**
     * A bar chart of measured values (the Data template, docs/design/image-templates.md): each bar's
     * length is drawn to scale from its [Bar.amount], and its [Bar.value] is printed exactly as written.
     */
    data class Chart(val bars: List<Bar>, val unit: String? = null) : SectionBody {
        data class Bar(val label: String, val value: String, val amount: Double)
    }

    /**
     * Components and the arrows between them (the Architecture and Workflow templates): each node
     * may belong to a [Node.group] (a service boundary, a tier), and each edge may carry a label
     * ("REST", "async", "no"). Branches are edges from one node to several.
     */
    data class Diagram(val nodes: List<Node>, val edges: List<Edge>) : SectionBody {
        data class Node(val id: String, val label: String, val group: String? = null)
        data class Edge(val from: String, val to: String, val label: String? = null)
    }
}

/** Upper limits that keep a definition drawable, whatever the model wrote. */
internal object Limits {
    /**
     * Up to 16 cards (AC-2): a guard against runaway replies, not a design target. The AI decides
     * how many cards a poster needs; the readability check decides whether they fit one poster.
     */
    const val SECTIONS = 16
    const val ITEMS = 7
    const val CODE_LINES = 14
    const val TABLE_COLUMNS = 4
    const val TABLE_ROWS = 7
    const val FLOW_NODES = 8
    const val SLIDES = 8
    const val CHART_BARS = 8
    const val DIAGRAM_NODES = 10
    const val DIAGRAM_EDGES = 14
    const val TEXT = 220
}

/**
 * Reads an [InfographicSpec] from a model reply: the JSON object in it, ignoring a code fence
 * or words around it. Unknown fields are ignored, unknown section types become bullets when
 * they have items, and lists are cut to [Limits]. Fails when there is no object or no title.
 */
fun parseInfographic(reply: String): Result<InfographicSpec> =
    jsonObjectIn(reply).mapCatching { spec(it, null, null) }

private fun jsonObjectIn(reply: String): Result<JsonObject> {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start < 0 || end < start) return Result.failure(IllegalArgumentException("no JSON in the reply"))
    return try {
        (Json.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject)?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("the JSON isn't an object"))
    } catch (e: SerializationException) {
        Result.failure(IllegalArgumentException("the JSON couldn't be read", e))
    }
}

private fun spec(json: JsonObject, format: InfographicFormat?, palette: Palette?): InfographicSpec {
    val title = json.string("title")
    require(!title.isNullOrBlank()) { "the infographic has no title" }
    val sections = json.array("sections").filterIsInstance<JsonObject>().mapNotNull(::section)
    return InfographicSpec(
        title = title.clip(80),
        highlight = json.string("highlight")?.takeIf { it.isNotBlank() },
        subtitle = json.string("subtitle")?.takeIf { it.isNotBlank() }?.clip(),
        format = format ?: InfographicFormat.IMAGE.firstOrNull { it.key == json.string("format")?.lowercase() }
            ?: InfographicFormat.PORTRAIT,
        palette = palette ?: Palette.of(json.string("palette")),
        icon = json.string("icon")?.lowercase()?.takeIf { it in ICONS },
        hero = json.string("hero")?.takeIf { it.isNotBlank() }?.clip(),
        sections = sections.take(Limits.SECTIONS),
        takeaway = json.string("takeaway")?.takeIf { it.isNotBlank() }?.clip(),
        droppedSections = (sections.size - Limits.SECTIONS).coerceAtLeast(0),
    )
}

private fun section(json: JsonObject): Section? {
    val items = json.texts("items")
    val body: SectionBody? = when (json.string("type")?.lowercase()?.replace('-', '_')) {
        "steps" -> SectionBody.Steps(items).takeIf { items.isNotEmpty() }
        "pros_cons", "proscons", "checklist" -> SectionBody.ProsCons(json.texts("pros"), json.texts("cons"))
            .takeIf { it.pros.isNotEmpty() || it.cons.isNotEmpty() }
        "code" -> json.string("code")?.takeIf { it.isNotBlank() }?.let { code ->
            SectionBody.Code(code.trimEnd().lines().take(Limits.CODE_LINES).joinToString("\n"), json.string("language"))
        }
        "table" -> {
            val columns = json.texts("columns").take(Limits.TABLE_COLUMNS)
            val rows = json.array("rows").filterIsInstance<JsonArray>()
                .map { row -> row.map { it.text().clip(80) }.take(Limits.TABLE_COLUMNS) }
                .take(Limits.TABLE_ROWS)
            SectionBody.Table(columns, rows).takeIf { columns.isNotEmpty() && rows.isNotEmpty() }
        }
        "flow" -> json.texts("nodes").ifEmpty { items }.take(Limits.FLOW_NODES)
            .takeIf { it.size >= 2 }?.let { SectionBody.Flow(it.map { node -> node.clip(40) }) }
        "compare" -> {
            val left = side(json.obj("left"), "Before")
            val right = side(json.obj("right"), "After")
            if (left != null && right != null) SectionBody.Compare(left, right) else null
        }
        "stats" -> json.array("items").filterIsInstance<JsonObject>()
            .mapNotNull { stat ->
                val value = stat.string("value")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SectionBody.Stats.Stat(value.clip(12), stat.string("label").orEmpty().clip(60))
            }
            .take(4).takeIf { it.isNotEmpty() }?.let { SectionBody.Stats(it) }
        "chart", "bar_chart", "bars" -> chart(json)
        "diagram", "architecture", "graph" -> diagram(json)
        "illustration" -> json.string("prompt")?.takeIf { it.isNotBlank() }
            ?.let { SectionBody.Illustration(it.clip(), json.string("caption")?.takeIf { c -> c.isNotBlank() }?.clip()) }
        else -> SectionBody.Bullets(items).takeIf { items.isNotEmpty() }
    }
    body ?: return null
    return Section(
        heading = json.string("heading").orEmpty().clip(60),
        body = body,
        icon = json.string("icon")?.lowercase()?.takeIf { it in ICONS },
    )
}

/** Bars as `{"label": "Redis", "value": "0.5 ms"}`; a value without a number, or fewer than 2 bars, is no chart. */
private fun chart(json: JsonObject): SectionBody.Chart? {
    val bars = json.array("bars").ifEmpty { json.array("items") }.filterIsInstance<JsonObject>()
        .mapNotNull { bar ->
            val value = bar.string("value")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val amount = NUMBER.find(value.replace(",", ""))?.value?.toDoubleOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            SectionBody.Chart.Bar(bar.string("label").orEmpty().clip(40), value.clip(16), amount)
        }
        .take(Limits.CHART_BARS)
    if (bars.size < 2) return null
    return SectionBody.Chart(bars, json.string("unit")?.takeIf { it.isNotBlank() }?.clip(30))
}

private val NUMBER = Regex("""\d+(?:\.\d+)?""")

/**
 * Nodes as `{"id": "api", "label": "API Gateway", "group": "Backend"}` or plain labels, and edges as
 * `{"from": "api", "to": "orders", "label": "REST"}` or `["api", "orders"]`, matched to nodes by id or
 * label. Edges to unknown nodes, to themselves and repeats are dropped; fewer than 2 nodes is no diagram.
 */
private fun diagram(json: JsonObject): SectionBody.Diagram? {
    val nodes = json.array("nodes").mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { SectionBody.Diagram.Node(it, it.clip(40)) }
            is JsonObject -> {
                val label = (element.string("label") ?: element.string("name") ?: element.string("id"))?.takeIf { it.isNotBlank() }
                label?.let { SectionBody.Diagram.Node(element.string("id")?.takeIf { id -> id.isNotBlank() } ?: it, it.clip(40), element.string("group")?.takeIf { g -> g.isNotBlank() }?.clip(30)) }
            }
            else -> null
        }
    }.distinctBy { it.id.lowercase() }.take(Limits.DIAGRAM_NODES)
    if (nodes.size < 2) return null
    fun find(key: String?) = key?.trim()?.lowercase()?.let { k -> nodes.firstOrNull { it.id.lowercase() == k } ?: nodes.firstOrNull { it.label.lowercase() == k } }
    val edges = json.array("edges").mapNotNull { element ->
        val (from, to, label) = when (element) {
            is JsonArray -> Triple(element.getOrNull(0)?.text(), element.getOrNull(1)?.text(), element.getOrNull(2)?.text())
            is JsonObject -> Triple(element.string("from"), element.string("to"), element.string("label"))
            else -> return@mapNotNull null
        }
        val a = find(from) ?: return@mapNotNull null
        val b = find(to)?.takeIf { it != a } ?: return@mapNotNull null
        SectionBody.Diagram.Edge(a.id, b.id, label?.takeIf { it.isNotBlank() }?.clip(20))
    }.distinctBy { it.from to it.to }.take(Limits.DIAGRAM_EDGES)
    return SectionBody.Diagram(nodes, edges)
}

private fun side(json: JsonObject?, defaultLabel: String): SectionBody.Compare.Side? {
    json ?: return null
    val items = json.texts("items")
    if (items.isEmpty()) return null
    return SectionBody.Compare.Side(json.string("label")?.takeIf { it.isNotBlank() }?.clip(30) ?: defaultLabel, items)
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()

private fun JsonObject.array(key: String): List<JsonElement> = this[key] as? JsonArray ?: emptyList()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** A list of strings; items written as `{"text": …}` objects count too. */
private fun JsonObject.texts(key: String): List<String> =
    array(key).map { it.text().clip() }.filter { it.isNotBlank() }.take(Limits.ITEMS)

private fun JsonElement.text(): String = when (this) {
    is JsonPrimitive -> contentOrNull.orEmpty()
    is JsonObject -> string("text") ?: string("label") ?: string("value") ?: ""
    else -> ""
}.trim()

private fun String.clip(max: Int = Limits.TEXT): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

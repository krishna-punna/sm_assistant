package com.smassistant.app.infographic

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
)

enum class InfographicFormat(val key: String, val width: Int) {
    /** Tall cheat sheet like most sample images; at least 2:3 and taller when the content needs it. */
    PORTRAIT("portrait", 1080),

    /** 1:1, for grids and video slides. */
    SQUARE("square", 1080),

    /** 3:2 wide poster for architectures. */
    LANDSCAPE("landscape", 1620),
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
}

/** A video: slides that share one palette, each a square infographic. */
data class SlideDeck(val slides: List<InfographicSpec>)

/** Upper limits that keep a definition drawable, whatever the model wrote. */
internal object Limits {
    const val SECTIONS = 8
    const val ITEMS = 7
    const val CODE_LINES = 14
    const val TABLE_COLUMNS = 4
    const val TABLE_ROWS = 7
    const val FLOW_NODES = 8
    const val SLIDES = 8
    const val TEXT = 220
}

/**
 * Reads an [InfographicSpec] from a model reply: the JSON object in it, ignoring a code fence
 * or words around it. Unknown fields are ignored, unknown section types become bullets when
 * they have items, and lists are cut to [Limits]. Fails when there is no object or no title.
 */
fun parseInfographic(reply: String): Result<InfographicSpec> =
    jsonObjectIn(reply).mapCatching { spec(it, null, null) }

/**
 * Reads a [SlideDeck] (`{"palette": …, "slides": [ … ]}`): every slide is square and uses the
 * deck's palette. Slides without a title are left out; fails when none is left.
 */
fun parseSlideDeck(reply: String): Result<SlideDeck> = jsonObjectIn(reply).mapCatching { root ->
    val palette = Palette.of(root.string("palette"))
    val slides = root.array("slides").filterIsInstance<JsonObject>()
        .mapNotNull { runCatching { spec(it, InfographicFormat.SQUARE, palette) }.getOrNull() }
        .take(Limits.SLIDES)
    require(slides.isNotEmpty()) { "no slides in the reply" }
    SlideDeck(slides)
}

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
    return InfographicSpec(
        title = title.clip(80),
        highlight = json.string("highlight")?.takeIf { it.isNotBlank() },
        subtitle = json.string("subtitle")?.takeIf { it.isNotBlank() }?.clip(),
        format = format ?: InfographicFormat.entries.firstOrNull { it.key == json.string("format")?.lowercase() }
            ?: InfographicFormat.PORTRAIT,
        palette = palette ?: Palette.of(json.string("palette")),
        icon = json.string("icon")?.lowercase()?.takeIf { it in ICONS },
        hero = json.string("hero")?.takeIf { it.isNotBlank() }?.clip(),
        sections = json.array("sections").filterIsInstance<JsonObject>().mapNotNull(::section).take(Limits.SECTIONS),
        takeaway = json.string("takeaway")?.takeIf { it.isNotBlank() }?.clip(),
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

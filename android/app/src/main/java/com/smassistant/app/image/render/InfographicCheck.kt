package com.smassistant.app.image.render

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fewest cards a Generate Image infographic may have; fewer reads as an unfinished image. */
const val MIN_SECTIONS = 3

/** Most cards one poster definition may have ([Limits.SECTIONS], AC-2). */
val MAX_CARDS: Int get() = Limits.SECTIONS

/** Smallest side an AI illustration may have before it is painted into its slot. */
const val MIN_ILLUSTRATION_SIDE = 256

/** Smallest side a finished infographic may have: the sample images' 1024 px (video slides are 1080). */
const val MIN_INFOGRAPHIC_SIDE = 1024

/**
 * The checks on a definition before it is drawn (docs/design/image-video-pipeline.md, "Checks
 * before an image is finished"). Each problem is a sentence the text agent is shown when it is
 * asked to write the definition again. [subject] is the topic and the content it was written from:
 * the title has to share a word with it.
 */
fun infographicProblems(spec: InfographicSpec, subject: String, minSections: Int = MIN_SECTIONS): List<String> = buildList {
    if (spec.sections.size < minSections) {
        add("it has ${spec.sections.size} sections; write at least $minSections cards that cover the topic")
    }
    if (spec.droppedSections > 0) {
        add("it has ${spec.sections.size + spec.droppedSections} cards, more than the $MAX_CARDS one poster can hold; merge related cards so every point is kept in at most $MAX_CARDS")
    }
    if (subject.isNotBlank() && !sharesAWord(spec.title, subject)) {
        add("the title \"${spec.title}\" doesn't name the topic; use the topic's own words in the title")
    }
    addAll(sectionProblems(spec, ""))
}

/**
 * The checks on a poster series (AC-7): at least 2 posters, each a complete poster ([infographicProblems]
 * with [subject]) that [fits] one readable poster, with no title repeated, since each poster covers the
 * next part of the content.
 */
fun seriesProblems(posters: List<InfographicSpec>, subject: String, fits: (InfographicSpec) -> Boolean = { true }): List<String> = buildList {
    if (posters.size < 2) add("it has ${posters.size} poster; a series has 2 or more, each covering the next part of the content")
    posters.forEachIndexed { i, poster ->
        infographicProblems(poster, subject).forEach { add("poster ${i + 1}: $it") }
        if (!fits(poster)) add("poster ${i + 1} (\"${poster.title}\") is still too dense to read; move some of its cards to another poster")
    }
    posters.groupBy { it.title.trim().lowercase() }.filterValues { it.size > 1 }.keys.forEach {
        add("more than one poster is titled \"$it\"; give each poster a title for its own part")
    }
}

/**
 * The checks of the image template the definition follows (docs/design/image-templates.md, "Checks
 * and review"), on top of [infographicProblems]: the cards the template needs, and for the Data and
 * Code templates that the numbers and code shown come from the [content]. Any other template id
 * has no extra checks.
 */
fun templateProblems(spec: InfographicSpec, templateId: String?, content: String): List<String> = buildList {
    val bodies = spec.sections.map { it.body }
    when (templateId) {
        "comparison" -> if (bodies.none { it is SectionBody.Compare || it is SectionBody.Table || it is SectionBody.ProsCons }) {
            add("the Comparison template needs a compare, table or pros_cons card that sets the options side by side")
        }
        "workflow" -> if (bodies.none { it is SectionBody.Steps || it is SectionBody.Flow || it is SectionBody.Diagram }) {
            add("the Workflow template needs a steps, flow or diagram card with the steps in order")
        }
        "architecture" -> if (bodies.none { it is SectionBody.Diagram || it is SectionBody.Flow }) {
            add("the Architecture template needs a diagram card of the components and how they connect")
        }
        "code" -> {
            val code = bodies.filterIsInstance<SectionBody.Code>()
            if (code.isEmpty()) add("the Code template needs a code card with code from the content")
            if (content.isNotBlank()) {
                val source = squash(content)
                code.flatMap { it.code.lines() }.map { it.trim() }.filter { it.length > 3 && !it.startsWith("//") && !it.startsWith("#") }
                    .firstOrNull { squash(it) !in source }
                    ?.let { add("the code line \"${it.take(50)}\" isn't in the content; show code from the content only") }
            }
        }
        "data" -> if (content.isNotBlank()) {
            val numbers = NUMBER_IN_TEXT.findAll(content.replace(",", "")).map { it.value.trimEnd('.') }.toSet()
            bodies.flatMap { body ->
                when (body) {
                    is SectionBody.Chart -> body.bars.map { it.value }
                    is SectionBody.Stats -> body.items.map { it.value }
                    else -> emptyList()
                }
            }.firstOrNull { value -> NUMBER_IN_TEXT.findAll(value.replace(",", "")).any { it.value.trimEnd('.') !in numbers } }
                ?.let { add("the value \"$it\" isn't in the content; the Data template shows only numbers from the content") }
        }
    }
}

private val NUMBER_IN_TEXT = Regex("""\d+(?:\.\d+)?""")

/** [text] without spaces, lower case, so code compares however it was indented or spaced. */
private fun squash(text: String) = text.lowercase().filterNot { it.isWhitespace() }

private fun sectionProblems(spec: InfographicSpec, prefix: String): List<String> = buildList {
    spec.sections.forEachIndexed { i, section ->
        if (section.heading.isBlank()) add("${prefix}section ${i + 1} has no heading")
    }
    spec.sections.map { it.heading.trim().lowercase() }.filter { it.isNotEmpty() }
        .groupBy { it }.filterValues { it.size > 1 }.keys
        .forEach { add("${prefix}more than one section is headed \"$it\"") }
    spec.texts().firstOrNull { PLACEHOLDER.containsMatchIn(it) || it.trim().trim('.', '…').isEmpty() }?.let {
        add("${prefix}it still has placeholder text (\"${it.take(40)}\"); write the real content")
    }
    spec.illustrationPrompts().firstOrNull { asksForText(it) }?.let {
        add("${prefix}the illustration \"${it.take(60)}\" asks for text or a diagram; AI illustrations garble text, so describe a picture with no words")
    }
}

/** Every piece of text the renderer draws. */
private fun InfographicSpec.texts(): List<String> = listOfNotNull(title, subtitle, takeaway) + sections.flatMap { section ->
    listOf(section.heading).filter { it.isNotBlank() } + when (val body = section.body) {
        is SectionBody.Bullets -> body.items
        is SectionBody.Steps -> body.items
        is SectionBody.ProsCons -> body.pros + body.cons
        is SectionBody.Code -> emptyList()
        is SectionBody.Table -> body.columns + body.rows.flatten().filter { it.isNotBlank() }
        is SectionBody.Flow -> body.nodes
        is SectionBody.Compare -> listOf(body.left.label, body.right.label) + body.left.items + body.right.items
        is SectionBody.Stats -> body.items.flatMap { listOf(it.value, it.label) }.filter { it.isNotBlank() }
        is SectionBody.Illustration -> listOfNotNull(body.caption)
        is SectionBody.Chart -> body.bars.flatMap { listOf(it.label, it.value) }.filter { it.isNotBlank() } + listOfNotNull(body.unit)
        is SectionBody.Diagram -> body.nodes.flatMap { listOfNotNull(it.label, it.group) }.distinct() + body.edges.mapNotNull { it.label }
    }
}

private fun InfographicSpec.illustrationPrompts(): List<String> =
    listOfNotNull(hero) + sections.mapNotNull { (it.body as? SectionBody.Illustration)?.prompt } + sections.mapNotNull { it.art }

private val PLACEHOLDER = Regex("""lorem ipsum|\bTODO\b|\bTBD\b|\[insert|\{\{|<placeholder|\bxxx\b""", RegexOption.IGNORE_CASE)

private val NO_TEXT = Regex("""\b(no|without|free of)\s+(any\s+)?(text|labels?|words|letters|logos?|captions?)(\s+or\s+\w+)*""", RegexOption.IGNORE_CASE)
private val TEXT_WORDS = Regex(
    """\b(text|labels?|labell?ed|captions?|words?|letters?|logos?|typography|written|writing|chart|graph|diagram|flowchart)\b""",
    RegexOption.IGNORE_CASE,
)

/** True when an illustration prompt asks for something diffusion models draw as gibberish. */
internal fun asksForText(prompt: String) = TEXT_WORDS.containsMatchIn(NO_TEXT.replace(prompt, ""))

private val STOP_WORDS = setOf(
    "the", "and", "for", "with", "how", "what", "why", "when", "your", "you", "are", "into", "from", "this",
    "that", "its", "guide", "cheat", "sheet", "explained", "overview", "basics", "deep", "dive", "inside", "about",
    "complete", "ultimate", "best", "practices", "works", "work", "under", "hood",
)

/** True when a meaningful word of [title] (or its first four letters) appears in [subject]. */
internal fun sharesAWord(title: String, subject: String): Boolean {
    val words = title.lowercase().split(Regex("[^\\p{L}\\p{N}+#]+")).filter { it.length >= 2 && it !in STOP_WORDS }
    if (words.isEmpty()) return true
    val haystack = subject.lowercase()
    return words.any { haystack.contains(it.take(4)) }
}

/**
 * What the app can tell about a picture without looking at it: its size, how much the brightness
 * varies ([contrast], a standard deviation from 0 to 255), how much of it is one colour
 * ([dominantShare]) and how much isn't transparent ([opaqueShare]).
 */
data class ImageFacts(val width: Int, val height: Int, val contrast: Float, val dominantShare: Float, val opaqueShare: Float)

/** Reads [ImageFacts] from an image file; null when the file isn't an image. */
fun interface ImageInspector {
    suspend fun facts(file: File): ImageFacts?
}

/**
 * Why an image can't be used, or null when it can: it isn't an image, is smaller than [minSide]
 * on its short side, is (almost) fully transparent, or is one flat colour (a blank or failed render).
 */
fun imageProblem(facts: ImageFacts?, minSide: Int): String? = when {
    facts == null -> "the file isn't an image"
    min(facts.width, facts.height) < minSide -> "it is ${facts.width}x${facts.height}, smaller than $minSide px"
    facts.opaqueShare < 0.05f -> "it is transparent"
    facts.dominantShare > 0.985f || facts.contrast < 3f -> "it is blank"
    else -> null
}

/** [ImageFacts] from a sample of an image's ARGB [pixels] (any sample size; the app reads about 100x100). */
fun imageFacts(width: Int, height: Int, pixels: IntArray): ImageFacts {
    if (pixels.isEmpty()) return ImageFacts(width, height, 0f, 1f, 0f)
    var sum = 0.0
    var squares = 0.0
    var opaque = 0
    val buckets = HashMap<Int, Int>()
    for (argb in pixels) {
        val alpha = argb ushr 24 and 0xFF
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        if (alpha >= 128) opaque++
        val luminance = 0.299 * r + 0.587 * g + 0.114 * b
        sum += luminance
        squares += luminance * luminance
        // Colours within 16 levels of each other count as one, so JPEG noise doesn't hide a blank image.
        val bucket = if (alpha < 128) -1 else (r / 16 shl 8) or (g / 16 shl 4) or (b / 16)
        buckets[bucket] = (buckets[bucket] ?: 0) + 1
    }
    val mean = sum / pixels.size
    val variance = max(0.0, squares / pixels.size - mean * mean)
    return ImageFacts(
        width = width,
        height = height,
        contrast = sqrt(variance).toFloat(),
        dominantShare = buckets.values.max().toFloat() / pixels.size,
        opaqueShare = opaque.toFloat() / pixels.size,
    )
}

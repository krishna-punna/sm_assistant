package com.smassistant.app.infographic

import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fewest cards a Generate Image infographic may have; fewer reads as an unfinished image. */
const val MIN_SECTIONS = 3

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
    if (subject.isNotBlank() && !sharesAWord(spec.title, subject)) {
        add("the title \"${spec.title}\" doesn't name the topic; use the topic's own words in the title")
    }
    addAll(sectionProblems(spec, ""))
}

/** The checks on a video's deck: every slide has something on it, no two slides repeat, no placeholders. */
fun slideDeckProblems(deck: SlideDeck): List<String> = buildList {
    deck.slides.forEachIndexed { i, slide ->
        if (slide.sections.isEmpty() && slide.hero == null && slide.takeaway == null) {
            add("slide ${i + 1} (\"${slide.title}\") has nothing on it but its title")
        }
        addAll(sectionProblems(slide, "slide ${i + 1}: "))
    }
    deck.slides.groupBy { it.title.lowercase() }.filterValues { it.size > 1 }.keys.forEach {
        add("more than one slide is titled \"$it\"; every slide should move the story on")
    }
}

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
    listOfNotNull(hero) + sections.mapNotNull { (it.body as? SectionBody.Illustration)?.prompt }

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

package com.smassistant.app.image

import com.smassistant.app.data.ImageSize
import com.smassistant.app.image.render.InfographicFormat
import com.smassistant.app.image.render.mix
import java.io.File

/**
 * How a template is drawn by the app (docs/design/image-templates.md, section 6): the formats that
 * suit it, best first, and the section types the infographic writer should lean on.
 */
data class TemplateLayout(val formats: List<ImageSize>, val sections: String)

/** The layout for each template id; any other id gets the Universal Master's. */
val TEMPLATE_LAYOUTS: Map<String, TemplateLayout> = mapOf(
    UNIVERSAL_TEMPLATE to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.LANDSCAPE),
        "the mix of section types that best explains the central message",
    ),
    "concept" to TemplateLayout(
        listOf(ImageSize.SQUARE, ImageSize.PORTRAIT),
        "a few bullets cards around the one concept and its mechanism, at most one illustration, and a takeaway",
    ),
    "architecture" to TemplateLayout(
        listOf(ImageSize.LANDSCAPE, ImageSize.PORTRAIT),
        "a diagram of the components grouped by boundary with labelled arrows, a table of the components and their roles, and bullets for failure paths and asynchronous parts",
    ),
    "comparison" to TemplateLayout(
        listOf(ImageSize.LANDSCAPE, ImageSize.PORTRAIT),
        "compare, table and pros_cons cards with the same criteria for each side, and a when-to-choose takeaway",
    ),
    "workflow" to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.LANDSCAPE),
        "steps and flow cards in the order the content gives, with short step labels, and a diagram when the flow branches or loops",
    ),
    "code" to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.SQUARE),
        "one short code card taken from the content, and bullets or steps that explain what it does",
    ),
    "checklist" to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.SQUARE),
        "pros_cons cards for do and avoid, and bullets cards grouping the practices by theme",
    ),
    "data" to TemplateLayout(
        listOf(ImageSize.LANDSCAPE, ImageSize.SQUARE),
        "chart, stats and table cards with the content's exact numbers, units and baselines",
    ),
    "learning" to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.SQUARE),
        "cards that go concept, explanation, example, takeaway",
    ),
    SERIES_TEMPLATE to TemplateLayout(
        listOf(ImageSize.PORTRAIT, ImageSize.SQUARE),
        "the section types that fit the content, in the series' style",
    ),
)

fun layoutOf(template: ImageTemplate): TemplateLayout = TEMPLATE_LAYOUTS[template.id] ?: TEMPLATE_LAYOUTS.getValue(UNIVERSAL_TEMPLATE)

/** One image of a submit: the template it follows and its format. */
data class ImagePlan(val template: ImageTemplate, val format: ImageSize)

/**
 * The [count] images of a submit (docs/design/image-templates.md, section 4): the primary template
 * in its best and its other format, then the first alternate, then the Universal Master (or the
 * second alternate when the primary is already the Universal). A template the user picked is used
 * for every image, alternating its formats. A [format] the user picked replaces every format.
 */
fun planImages(choice: TemplateChoice, count: Int, picked: Boolean, universal: ImageTemplate, format: ImageSize? = null): List<ImagePlan> {
    val primary = choice.primary
    val templates = if (picked) {
        List(count) { primary }
    } else {
        val fourth = if (primary.id == UNIVERSAL_TEMPLATE) choice.alternates.getOrNull(1) ?: universal else universal
        listOf(primary, primary, choice.alternates.firstOrNull() ?: universal, fourth)
    }
    return List(count) { i ->
        val template = templates[i % templates.size]
        val formats = layoutOf(template).formats
        // The second image of the primary template (and every other one of a picked template) takes its other format.
        val second = (picked && i % 2 == 1) || (!picked && i == 1)
        ImagePlan(template, format ?: if (second) formats.last() else formats.first())
    }
}

/** The renderer's format for an image format. */
val ImageSize.infographic: InfographicFormat
    get() = when (this) {
        ImageSize.PORTRAIT -> InfographicFormat.PORTRAIT
        ImageSize.SQUARE -> InfographicFormat.SQUARE
        ImageSize.LANDSCAPE -> InfographicFormat.LANDSCAPE
    }

/** The image size of a renderer format. */
val InfographicFormat.image: ImageSize
    get() = when (this) {
        InfographicFormat.PORTRAIT -> ImageSize.PORTRAIT
        InfographicFormat.SQUARE -> ImageSize.SQUARE
        InfographicFormat.LANDSCAPE -> ImageSize.LANDSCAPE
    }

/**
 * The template and format an image was made with, and whether it was AI-[painted], kept in its file
 * name so saved results keep it too.
 */
data class ImageLabel(val templateId: String, val format: ImageSize, val painted: Boolean = false)

private val LABELLED = Regex("""^tpl-([a-z]+)-([a-z]+)-(painted-)?""")

/** [file] renamed to carry its template and format; the file itself when it can't be renamed. */
fun labelImage(file: File, templateId: String, format: ImageSize, painted: Boolean = false): File {
    val labelled = File(file.parentFile, "tpl-$templateId-${format.key}-${if (painted) "painted-" else ""}${file.name}")
    return if (file.renameTo(labelled)) labelled else file
}

/** The template and format in a file name written by [labelImage]; null for other files. */
fun imageLabel(file: File): ImageLabel? {
    val match = LABELLED.find(file.name) ?: return null
    val format = ImageSize.of(match.groupValues[2]) ?: return null
    return ImageLabel(match.groupValues[1], format, match.groupValues[3].isNotEmpty())
}

/**
 * "Workflow · 2:3" ("Workflow · 2:3 · AI" when painted): the template's name from [templates] and the
 * format's ratio, or the image's real [size] ("2:3 · 2160×3240") when it is known.
 */
fun ImageLabel.text(templates: List<ImageTemplate>, size: String? = null): String =
    "${templates.firstOrNull { it.id == templateId }?.name ?: templateId.replaceFirstChar { it.uppercase() }} · ${size ?: format.words.substringAfter(' ')}" +
        if (painted) " · AI" else ""

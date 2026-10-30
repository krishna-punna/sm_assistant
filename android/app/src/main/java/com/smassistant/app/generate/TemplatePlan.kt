package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
import com.smassistant.app.infographic.InfographicFormat

/**
 * How a template is drawn by the app (docs/design/image-templates.md, section 6): the formats that
 * suit it, best first, and the section types the infographic writer should lean on.
 */
data class TemplateLayout(val formats: List<ImageFormat>, val sections: String)

/** The layout for each template id; any other id gets the Universal Master's. */
val TEMPLATE_LAYOUTS: Map<String, TemplateLayout> = mapOf(
    UNIVERSAL_TEMPLATE to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.LANDSCAPE),
        "the mix of section types that best explains the central message",
    ),
    "concept" to TemplateLayout(
        listOf(ImageFormat.SQUARE, ImageFormat.PORTRAIT),
        "a few bullets cards around the one concept and its mechanism, at most one illustration, and a takeaway",
    ),
    "architecture" to TemplateLayout(
        listOf(ImageFormat.LANDSCAPE, ImageFormat.PORTRAIT),
        "a flow of the components in the order data moves, a table of the components and their roles, and bullets for boundaries, failure paths and asynchronous parts",
    ),
    "comparison" to TemplateLayout(
        listOf(ImageFormat.LANDSCAPE, ImageFormat.PORTRAIT),
        "compare, table and pros_cons cards with the same criteria for each side, and a when-to-choose takeaway",
    ),
    "workflow" to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.LANDSCAPE),
        "steps and flow cards in the order the content gives, with short step labels",
    ),
    "code" to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.SQUARE),
        "one short code card taken from the content, and bullets or steps that explain what it does",
    ),
    "checklist" to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.SQUARE),
        "pros_cons cards for do and avoid, and bullets cards grouping the practices by theme",
    ),
    "data" to TemplateLayout(
        listOf(ImageFormat.LANDSCAPE, ImageFormat.SQUARE),
        "stats and table cards with the content's exact numbers, units and baselines",
    ),
    "learning" to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.SQUARE),
        "cards that go concept, explanation, example, takeaway",
    ),
    SERIES_TEMPLATE to TemplateLayout(
        listOf(ImageFormat.PORTRAIT, ImageFormat.SQUARE),
        "the section types that fit the content, in the series' style",
    ),
)

fun layoutOf(template: ImageTemplate): TemplateLayout = TEMPLATE_LAYOUTS[template.id] ?: TEMPLATE_LAYOUTS.getValue(UNIVERSAL_TEMPLATE)

/** One image of a submit: the template it follows and its format. */
data class ImagePlan(val template: ImageTemplate, val format: ImageFormat)

/**
 * The [count] images of a submit (docs/design/image-templates.md, section 4): the primary template
 * in its best and its other format, then the first alternate, then the Universal Master (or the
 * second alternate when the primary is already the Universal). A template the user picked is used
 * for every image, alternating its formats. A [format] the user picked replaces every format.
 */
fun planImages(choice: TemplateChoice, count: Int, picked: Boolean, universal: ImageTemplate, format: ImageFormat? = null): List<ImagePlan> {
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
val ImageFormat.infographic: InfographicFormat
    get() = when (this) {
        ImageFormat.PORTRAIT -> InfographicFormat.PORTRAIT
        ImageFormat.SQUARE -> InfographicFormat.SQUARE
        ImageFormat.LANDSCAPE -> InfographicFormat.LANDSCAPE
    }

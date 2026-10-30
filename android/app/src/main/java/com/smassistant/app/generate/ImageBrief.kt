package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
import com.smassistant.app.data.ImageStyle

/**
 * What a Generate Image submit asks for: the [prompt], and the template id and format the user
 * picked, where null is Auto (the template chooser and the app decide).
 */
data class ImageRequest(
    val prompt: String,
    val template: String? = null,
    val format: ImageFormat? = null,
)

/**
 * Everything one image is made from (the template file's "If you are automating" fields): the
 * [template], the technical [content] it represents, its [format] and the user's [style] inputs.
 * A [painted] image is painted by the image agent from the filled template instead of drawn by the app.
 * With the style's "Keep a series style" on, the [series] template wraps the template.
 */
data class ImageBrief(
    val template: ImageTemplate,
    val content: String,
    val format: ImageFormat,
    val style: ImageStyle = ImageStyle(),
    val painted: Boolean = false,
    val series: ImageTemplate? = null,
) {
    /** The template filled with the content and the style inputs, then the series rules when there are any. */
    fun filled(): String {
        val filled = template.fill(content, format.words, style.audience, style.styleGuide, style.brandName)
        val wrap = series ?: return filled
        // The content and the audience and brand lines are already in the template above.
        return filled.trimEnd() + "\n\n" + wrap.fill("(the technical content above)", format.words, style = style.styleGuide)
    }
}

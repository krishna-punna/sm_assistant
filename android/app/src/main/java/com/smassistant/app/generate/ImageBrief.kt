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
 */
data class ImageBrief(
    val template: ImageTemplate,
    val content: String,
    val format: ImageFormat,
    val style: ImageStyle = ImageStyle(),
) {
    /** The template filled with the content and the style inputs. */
    fun filled(): String = template.fill(content, format.words, style.audience, style.styleGuide, style.brandName)
}

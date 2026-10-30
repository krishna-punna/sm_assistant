package com.smassistant.app.generate

import com.smassistant.app.data.ImageStyle

/** The size every video is made in until each template picks its own (docs/tasks VT-I4). */
const val VIDEO_FORMAT_WORDS = "square 1:1"

/**
 * Everything one video is made from (docs/design/video-templates.md): the video [template] the
 * chooser picked, the technical [content] it represents, its length in [seconds], its [format] in
 * words and the user's [style] inputs.
 */
data class VideoBrief(
    val template: ImageTemplate,
    val content: String,
    val seconds: Int,
    val format: String = VIDEO_FORMAT_WORDS,
    val style: ImageStyle = ImageStyle(),
) {
    /** The template filled with the content, the length, the format and the style inputs. */
    fun filled(): String =
        template.fill(content, format, style.audience, style.styleGuide, style.brandName, duration = "$seconds seconds")
}

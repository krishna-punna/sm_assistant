package com.smassistant.app.video

import com.smassistant.app.data.VideoStyle
import com.smassistant.app.video.render.InfographicFormat
import java.io.File

/**
 * The sizes a video is made in: the standard video sizes, never the image ones (Krishna,
 * 2026-10-30, docs/design/video-templates.md). [width] x [height] is the MP4's size.
 */
enum class VideoSize(val key: String, val words: String, val width: Int, val height: Int, val infographic: InfographicFormat) {
    VERTICAL("vertical", "vertical 9:16", 1080, 1920, InfographicFormat.VERTICAL),
    SQUARE("square", "square 1:1", 1080, 1080, InfographicFormat.SQUARE),
    LANDSCAPE("landscape", "landscape 16:9", 1920, 1080, InfographicFormat.WIDE),
    ;

    /** The ratio alone, e.g. "9:16". */
    val ratio: String get() = words.substringAfter(' ')

    companion object {
        fun of(key: String?): VideoSize? = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/** The size a video is made in when its template has none: vertical, the templates' default. */
val DEFAULT_VIDEO_SIZE = VideoSize.VERTICAL

/** The templates whose videos are wide: diagrams, side-by-side comparisons and charts need the width. */
private val LANDSCAPE_VIDEO_TEMPLATES = setOf("architecture", "comparison", "data")

/** The video size for a template (docs/tasks VT-I4): landscape for architecture, comparison and data; vertical otherwise. */
fun videoSizeOf(template: VideoTemplate): VideoSize =
    if (template.id in LANDSCAPE_VIDEO_TEMPLATES) VideoSize.LANDSCAPE else DEFAULT_VIDEO_SIZE

/**
 * Everything one video is made from (docs/design/video-templates.md): the video [template] the
 * chooser picked, the technical [content] it represents, its length in [seconds], its [format]
 * and the user's [style] inputs.
 */
data class VideoBrief(
    val template: VideoTemplate,
    val content: String,
    val seconds: Int,
    val format: VideoSize = videoSizeOf(template),
    val style: VideoStyle = VideoStyle(),
) {
    /** The template filled with the content, the length, the format and the style inputs. */
    fun filled(): String =
        template.fill(content, format.words, style.audience, style.styleGuide, style.brandName, duration = "$seconds seconds")
}

/** The template and size a video was made with, kept in its file name like an image's ([labelVideo]). */
data class VideoLabel(val templateId: String, val format: VideoSize)

private val VIDEO_LABELLED = Regex("""^vtpl-([a-z]+)-([a-z]+)-""")

/** Where a video made from [templateId] at [format] is written: [file] with both in its name. */
fun labelVideo(file: File, templateId: String, format: VideoSize): File =
    File(file.parentFile, "vtpl-$templateId-${format.key}-${file.name}")

/** The template and size in a file name written by [labelVideo]; null for other files. */
fun videoLabel(file: File): VideoLabel? {
    val match = VIDEO_LABELLED.find(file.name) ?: return null
    val format = VideoSize.of(match.groupValues[2]) ?: return null
    return VideoLabel(match.groupValues[1], format)
}

/** "Workflow or Lifecycle · 9:16": the template's name from [templates] and the size's ratio. */
fun VideoLabel.text(templates: List<VideoTemplate>): String = "${name(templates)} · ${format.ratio}"

/** The name of the template the video was made from, from [templates]. */
fun VideoLabel.name(templates: List<VideoTemplate>): String =
    templates.firstOrNull { it.id == templateId }?.name ?: templateId.replaceFirstChar { it.uppercase() }

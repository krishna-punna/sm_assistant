package com.smassistant.app.video

import com.smassistant.app.data.VideoStyle

/** Id of the template used when no other fits or the choice fails (docs/design/image-templates.md). */
const val UNIVERSAL_TEMPLATE = "universal"

/** What an optional input that the user left empty reads as in a filled template. */
const val NOT_SPECIFIED = "not specified"

/**
 * One of Krishna's video templates from `assets/prompts/video/video_templates.md`
 * (docs/requirements/16): its [id] (e.g. "workflow"), its [name], the "Use when" line the video
 * template chooser reads, and the [body] with `{{content}}`, `{{audience}}`, `{{duration}}`,
 * `{{style}}`, `{{brand}}` and `{{format}}` slots.
 */
data class VideoTemplate(
    val id: String,
    val name: String,
    val useWhen: String,
    val body: String,
) {
    /**
     * The template with its slots filled. [format] is the video size in words, e.g.
     * "vertical 9:16". An optional input left blank reads [NOT_SPECIFIED]; one the template has no
     * slot for is added at the end when it is set, so audience and brand reach every template.
     * [duration] is the video length in words, e.g. "45 seconds".
     */
    fun fill(
        content: String,
        format: String,
        audience: String = "",
        style: String = "",
        brand: String = "",
        duration: String = "",
    ): String {
        val optional = listOf(
            Triple("audience", "Target audience", audience),
            Triple("duration", "Target duration", duration),
            Triple("style", "Brand/style preferences", style),
            Triple("brand", "Brand name", brand),
        )
        var text = body.replace("{{content}}", content.trim()).replace("{{format}}", format.trim())
        val extra = mutableListOf<String>()
        for ((slot, label, value) in optional) {
            val token = "{{$slot}}"
            if (text.contains(token)) {
                text = text.replace(token, value.trim().ifEmpty { NOT_SPECIFIED })
            } else if (value.isNotBlank()) {
                extra += "$label: ${value.trim()}"
            }
        }
        return if (extra.isEmpty()) text else text.trimEnd() + "\n\n" + extra.joinToString("\n")
    }
}

/**
 * Reads `video_templates.md`: blocks separated by a `---` line, each with `Id:`, `Name:` and
 * `Use when:` lines and the template after a `Template:` line. The intro before the first `---`
 * and blocks without an id, a name or a template are skipped.
 */
fun parseVideoTemplates(text: String): List<VideoTemplate> =
    text.split(Regex("""(?m)^---\s*$""")).drop(1).mapNotNull { block ->
        val lines = block.trim('\n').lines()
        val start = lines.indexOfFirst { it.trim().equals("Template:", ignoreCase = true) }
        if (start < 0) return@mapNotNull null
        val fields = lines.take(start).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        val id = fields["id"].orEmpty().lowercase()
        val name = fields["name"].orEmpty()
        val body = lines.drop(start + 1).joinToString("\n").trim()
        if (id.isEmpty() || name.isEmpty() || body.isEmpty()) return@mapNotNull null
        VideoTemplate(id, name, fields["use when"].orEmpty(), body)
    }

/** The template with [id] (any case), else the Universal Master, else the first one. */
fun List<VideoTemplate>.byId(id: String?): VideoTemplate =
    firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }
        ?: firstOrNull { it.id == UNIVERSAL_TEMPLATE }
        ?: first()

/** The video templates, their chooser and the Video Style settings (docs/design/video-templates.md). */
class VideoTemplateSet(
    val all: List<VideoTemplate>,
    val chooser: VideoTemplateChooser,
    val style: () -> VideoStyle = { VideoStyle() },
)

package com.smassistant.app.video

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.net.ApiResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Id of the Series template, which the user turns on rather than the chooser picking it. */
const val SERIES_TEMPLATE = "series"

/** How many alternates a choice keeps besides its primary template. */
const val TEMPLATE_ALTERNATES = 2

/** The templates picked for some content: the best fit, the next best ones, and the model's reason. */
data class VideoTemplateChoice(
    val primary: VideoTemplate,
    val alternates: List<VideoTemplate> = emptyList(),
    val reason: String = "",
)

/**
 * Picks the image or video template for the technical content (docs/design/image-templates.md, "Choosing
 * the template"): the text agent reads the content with [systemPrompt] (`prompts/image/choose_image_template.md`
 * or `prompts/video/choose_video_template.md`, each its own pipeline's, its `{{templates}}` filled with each template's "Use when" line) and names a primary template and
 * up to [TEMPLATE_ALTERNATES] alternates. A failed call or an unusable reply gives the Universal
 * Master, so choosing a template never fails an image or a video.
 */
class VideoTemplateChooser(
    private val systemPrompt: String,
    private val templates: List<VideoTemplate>,
) {
    suspend fun choose(agent: TextAgent, content: String): VideoTemplateChoice {
        val system = systemPrompt.replace("{{templates}}", templateList())
        return when (val result = agent.generate(system, "Technical content:\n${content.trim()}")) {
            is ApiResult.Failed -> fallback()
            is ApiResult.Ok -> parseVideoTemplateChoice(result.value, templates) ?: fallback()
        }
    }

    /** The Universal Master on its own, used whenever the choice can't be made. */
    fun fallback() = VideoTemplateChoice(templates.byId(UNIVERSAL_TEMPLATE))

    private fun templateList() = templates.filter { it.id != SERIES_TEMPLATE }.joinToString("\n") { "- ${it.id}: ${it.useWhen}" }
}

/**
 * Reads `{"primary": "…", "alternates": ["…"], "reason": "…"}` from a reply, ignoring a code fence or
 * words around it. Unknown ids, the Series template and repeats are dropped. Null when there is no
 * known primary.
 */
fun parseVideoTemplateChoice(reply: String, templates: List<VideoTemplate>): VideoTemplateChoice? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start < 0 || end < start) return null
    val json = try {
        Json.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return null
    fun known(id: String?) = templates.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) && it.id != SERIES_TEMPLATE }
    val primary = known((json["primary"] as? JsonPrimitive)?.contentOrNull) ?: return null
    val alternates = (json["alternates"] as? JsonArray).orEmpty()
        .mapNotNull { known((it as? JsonPrimitive)?.contentOrNull) }
        .filter { it != primary }
        .distinct()
        .take(TEMPLATE_ALTERNATES)
    val reason = (json["reason"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return VideoTemplateChoice(primary, alternates, reason)
}

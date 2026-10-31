package com.smassistant.app.video

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.net.ApiResult
import com.smassistant.app.video.render.InfographicSpec
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** What a vision model thought of a finished slide: a [score] out of 10 and what is wrong with it. */
data class SlideReview(val score: Int, val problems: List<String>)

/**
 * The last check on each video slide (docs/requirements/19, SP-14): the text agent looks at the
 * slide ([preview] makes the JPEG it is sent) with [systemPrompt] (`prompts/video/video_review.md`)
 * and scores how well it matches the topic and the slide's plan, whether its text is readable and
 * whether its illustration is clean. A slide passes with at least [minScore].
 */
class VideoReviewer(
    private val systemPrompt: String,
    private val preview: suspend (File) -> ByteArray?,
    val minScore: Int = DEFAULT_MIN_REVIEW_SCORE,
) {
    /**
     * Null when the slide couldn't be reviewed: the model can't see images, the call failed or
     * the reply had no score. The slide is then kept, so a review never stops Generate Video.
     */
    suspend fun review(agent: TextAgent, topic: String, spec: InfographicSpec, image: File, template: VideoTemplate? = null): SlideReview? {
        val jpeg = preview(image) ?: return null
        return when (val result = agent.see(systemPrompt, reviewRequest(topic, spec, template), jpeg)) {
            is ApiResult.Failed -> null
            is ApiResult.Ok -> parseSlideReview(result.value)
        }
    }

    fun passes(review: SlideReview) = review.score >= minScore
}

/** Score a slide needs by default, out of 10. */
const val DEFAULT_MIN_REVIEW_SCORE = 7

internal fun reviewRequest(topic: String, spec: InfographicSpec, template: VideoTemplate? = null) = buildString {
    append("Topic: ").append(topic.trim()).append("\n\n")
    append("Planned title: ").append(spec.title).append('\n')
    spec.subtitle?.let { append("Planned subtitle: ").append(it).append('\n') }
    if (spec.sections.isNotEmpty()) append("Planned sections: ").append(spec.sections.joinToString("; ") { it.heading }).append('\n')
    spec.takeaway?.let { append("Planned takeaway: ").append(it).append('\n') }
    val rules = template?.let(::templateRules).orEmpty()
    if (rules.isNotEmpty()) {
        append("\nVideo template: ").append(template!!.name).append(". Also check these rules of the template (as problem 7):\n")
        rules.forEach { append("- ").append(it).append('\n') }
    }
}.trimEnd()

/** The rules a template lists as "- " lines (its content rules, and design rules where it has them). */
internal fun templateRules(template: VideoTemplate): List<String> =
    template.body.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.filter { it.isNotEmpty() }

/**
 * Reads `{"score": 8, "problems": ["…"]}` from a reply, ignoring a code fence or words around it.
 * Scores are kept to 0..10. Null when there is no score.
 */
fun parseSlideReview(reply: String): SlideReview? {
    val start = reply.indexOf('{')
    val end = reply.lastIndexOf('}')
    if (start < 0 || end < start) return null
    val json = try {
        Json.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject
    } catch (e: SerializationException) {
        null
    } ?: return null
    val score = (json["score"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() } ?: return null
    val problems = (json["problems"] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { text -> text.isNotEmpty() } }
        .take(6)
    return SlideReview(score.coerceIn(0, 10), problems)
}

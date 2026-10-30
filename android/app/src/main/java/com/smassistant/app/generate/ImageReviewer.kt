package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** What a vision model thought of a finished image: a [score] out of 10 and what is wrong with it. */
data class ImageReview(val score: Int, val problems: List<String>)

/**
 * The last check before an infographic is finished (docs/design/image-video-pipeline.md, "Checks
 * before an image is finished"): the text agent looks at the image ([preview] makes the JPEG it
 * is sent) with [systemPrompt] (`prompts/image_review.md`) and scores how well it matches the
 * topic and the plan, whether its text is readable and whether its illustrations are clean.
 * An image passes with at least [minScore].
 */
class ImageReviewer(
    private val systemPrompt: String,
    private val preview: suspend (File) -> ByteArray?,
    val minScore: Int = DEFAULT_MIN_REVIEW_SCORE,
) {
    /**
     * Null when the image couldn't be reviewed: the model can't see images, the call failed or
     * the reply had no score. The image is then kept, so a review never stops Generate Image.
     */
    suspend fun review(agent: TextAgent, topic: String, spec: InfographicSpec, image: File, template: ImageTemplate? = null): ImageReview? {
        val jpeg = preview(image) ?: return null
        return when (val result = agent.see(systemPrompt, reviewRequest(topic, spec, template), jpeg)) {
            is ApiResult.Failed -> null
            is ApiResult.Ok -> parseImageReview(result.value)
        }
    }

    /**
     * Like [review], for an image the image agent painted from [template]: there is no plan to
     * compare with, and garbled or misspelt text, the main risk of a painted image, counts as a problem.
     */
    suspend fun reviewPainted(agent: TextAgent, topic: String, image: File, template: ImageTemplate): ImageReview? {
        val jpeg = preview(image) ?: return null
        return when (val result = agent.see(systemPrompt, paintedReviewRequest(topic, template), jpeg)) {
            is ApiResult.Failed -> null
            is ApiResult.Ok -> parseImageReview(result.value)
        }
    }

    fun passes(review: ImageReview) = review.score >= minScore
}

/** Score an image needs by default, out of 10. */
const val DEFAULT_MIN_REVIEW_SCORE = 7

internal fun reviewRequest(topic: String, spec: InfographicSpec, template: ImageTemplate? = null) = buildString {
    append("Topic: ").append(topic.trim()).append("\n\n")
    append("Planned title: ").append(spec.title).append('\n')
    spec.subtitle?.let { append("Planned subtitle: ").append(it).append('\n') }
    if (spec.sections.isNotEmpty()) append("Planned sections: ").append(spec.sections.joinToString("; ") { it.heading }).append('\n')
    spec.takeaway?.let { append("Planned takeaway: ").append(it).append('\n') }
    val rules = template?.let(::templateRules).orEmpty()
    if (rules.isNotEmpty()) {
        append("\nImage template: ").append(template!!.name).append(". Also check these rules of the template (as problem 7):\n")
        rules.forEach { append("- ").append(it).append('\n') }
    }
}.trimEnd()

internal fun paintedReviewRequest(topic: String, template: ImageTemplate) = buildString {
    append("Topic: ").append(topic.trim()).append("\n\n")
    append("This image was painted by an image model from the image template below, so there is no planned title or sections: skip problem 2. ")
    append("Be strict about problems 3 and 4: any misspelt, garbled or made-up word in the image is a problem.\n")
    append("\nImage template: ").append(template.name).append(". Also check these rules of the template (as problem 7):\n")
    templateRules(template).forEach { append("- ").append(it).append('\n') }
}.trimEnd()

/** The rules a template lists as "- " lines (its content rules, and design rules where it has them). */
internal fun templateRules(template: ImageTemplate): List<String> =
    template.body.lines().map { it.trim() }.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.filter { it.isNotEmpty() }

/**
 * Reads `{"score": 8, "problems": ["…"]}` from a reply, ignoring a code fence or words around it.
 * Scores are kept to 0..10. Null when there is no score.
 */
fun parseImageReview(reply: String): ImageReview? {
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
    return ImageReview(score.coerceIn(0, 10), problems)
}

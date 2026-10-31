package com.smassistant.app.video

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.video.render.SlideDeck
import com.smassistant.app.video.render.parseSlideDeck
import com.smassistant.app.video.render.slideDeckProblems
import com.smassistant.app.video.render.storyboardProblems

/**
 * The video's "LLM generates content" step: one call plans every slide ([systemPrompt] is
 * `prompts/video/video_deck.md`, `{{slides}}` the slide count, `{{seconds}}` how long each slide
 * shows, `{{format}}` the video's size, `{{examples}}` the closest few-shot examples), so the slides tell one story in one palette. Asked once more if the reply isn't a usable deck
 * or fails [slideDeckProblems]. A [brief] adds the video template the storyboard follows, and its
 * checks ([storyboardProblems]). [feedback] carries what a review found wrong with the slides
 * drawn from the last plan.
 */
class SlideDeckWriter(
    private val systemPrompt: String,
    private val examples: List<VideoExample> = emptyList(),
) {
    suspend fun write(
        agent: TextAgent,
        topic: String,
        slides: Int,
        secondsPerSlide: Int,
        content: String = "",
        brief: VideoBrief? = null,
        feedback: List<String> = emptyList(),
    ): ApiResult<SlideDeck> {
        val system = systemPrompt
            .replace("{{slides}}", slides.toString())
            .replace("{{seconds}}", secondsPerSlide.toString())
            .replace("{{format}}", (brief?.format ?: DEFAULT_VIDEO_SIZE).words)
            .replace("{{examples}}", formatVideoExamples(pickVideoExamples(examples, topic, template = brief?.template?.id)))
        val user = "Topic: $topic${contentBlock(content)}${videoTemplateBlock(brief, content)}${deckFeedbackBlock(feedback)}"
        return askTwice(agent, system, user, ::parseSlideDeck) { deck ->
            slideDeckProblems(deck) + brief?.let { storyboardProblems(deck, it.template.id, content, it.seconds) }.orEmpty()
        }
    }
}

/**
 * The video template the storyboard follows (docs/design/video-templates.md, section 3), filled
 * with the length, format and style inputs. The content isn't repeated; the template points at it.
 */
private fun videoTemplateBlock(brief: VideoBrief?, content: String): String {
    brief ?: return ""
    val source = if (content.isBlank()) "(the topic above)" else "(the technical content above)"
    return "\n\nVideo template: ${brief.template.name}, ${brief.format.words}, ${brief.seconds} seconds. Plan the slides by this template. " +
        "The app draws every slide from your JSON and plays them in order without sound, so the template decides " +
        "the story and what each slide shows; keep its content rules and turn its scenes into slides.\n\n" +
        brief.copy(content = source).filled()
}

/**
 * The video pipeline's first step (docs/requirements/14, MW-1 and MW-2; 19, SP-11): the text agent
 * writes technical content on the prompt with no length limit ([systemPrompt] is
 * `prompts/video/video_content.md`), and the deck is planned from that content rather than the bare prompt.
 */
class VideoContentWriter(private val systemPrompt: String) {
    suspend fun write(agent: TextAgent, prompt: String): ApiResult<String> = when (val result = agent.generate(systemPrompt, prompt)) {
        is ApiResult.Failed -> result
        is ApiResult.Ok -> result.value.trim().takeIf { it.isNotEmpty() }?.let { ApiResult.Ok(it) }
            ?: ApiResult.Failed(ApiError.BadResponse("no technical content in the reply"))
    }
}

private fun contentBlock(content: String) =
    if (content.isBlank()) "" else "\n\nTechnical content to represent:\n${content.trim()}"

private fun deckFeedbackBlock(feedback: List<String>) = if (feedback.isEmpty()) {
    ""
} else {
    "\n\nA review rejected slides drawn from your last plan for this topic:\n${feedback.joinToString("\n") { "- $it" }}\nWrite a new plan that fixes these problems."
}

/**
 * Asks [agent], and once more when the reply can't be parsed or [check] finds problems, telling it
 * what was wrong. A definition that still has problems on the second try is used anyway (the one
 * with fewer), since the problems are about quality; only an unreadable reply fails.
 */
private suspend fun <T> askTwice(
    agent: TextAgent,
    system: String,
    user: String,
    parse: (String) -> Result<T>,
    check: (T) -> List<String> = { emptyList() },
): ApiResult<T> {
    var problem = ""
    var message = user
    var best: Pair<T, Int>? = null
    repeat(2) {
        val reply = when (val result = agent.generate(system, message)) {
            is ApiResult.Failed -> return result
            is ApiResult.Ok -> result.value
        }
        val problems = parse(reply).fold(
            onSuccess = { value ->
                val found = check(value)
                if (found.isEmpty()) return ApiResult.Ok(value)
                if (best == null || found.size < best!!.second) best = value to found.size
                found
            },
            onFailure = { listOf(it.message.orEmpty().ifBlank { "no slide deck in the reply" }.also { problem = it }) },
        )
        message = "$user\n\nYour last reply couldn't be used: ${problems.joinToString("; ")}. Reply with the corrected JSON only."
    }
    best?.let { return ApiResult.Ok(it.first) }
    return ApiResult.Failed(ApiError.BadResponse(problem.ifBlank { "no slide deck in the reply" }))
}

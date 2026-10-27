package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.SlideDeck
import com.smassistant.app.infographic.infographicProblems
import com.smassistant.app.infographic.parseInfographic
import com.smassistant.app.infographic.parseSlideDeck
import com.smassistant.app.infographic.slideDeckProblems
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult

/**
 * Layout ideas given to the text agent, one per image, so a Generate Image submit returns
 * different infographics rather than four takes on the same one.
 */
val INFOGRAPHIC_LAYOUT_IDEAS = listOf(
    "a tall portrait cheat sheet that covers the topic end to end",
    "a landscape poster centred on the architecture or flow of how it works",
    "a portrait guide built around a comparison, trade-offs and best practices",
    "a square grid of the key points with a short code sample",
)

/**
 * The "LLM generates content" step of the pipeline: sends the topic to a text agent with
 * [systemPrompt] (`prompts/infographic.md`, its `{{examples}}` filled with the few-shot examples
 * closest to the topic) and reads back the JSON definition. A reply that isn't a usable
 * definition, or that fails [infographicProblems], is asked for once more with the problems
 * named. [feedback] carries what a review found wrong with the last image drawn from it.
 */
class InfographicWriter(
    private val systemPrompt: String,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend fun write(
        agent: TextAgent,
        topic: String,
        layoutIdea: String,
        content: String = "",
        feedback: List<String> = emptyList(),
    ): ApiResult<InfographicSpec> {
        val system = systemPrompt.replace("{{examples}}", formatImageExamples(pickImageExamples(examples, topic)))
        val user = "Topic: $topic${contentBlock(content)}\n\nLayout idea: $layoutIdea${feedbackBlock(feedback)}"
        return askTwice(agent, system, user, ::parseInfographic) { infographicProblems(it, "$topic\n$content") }
    }
}

/**
 * The video's "LLM generates content" step: one call plans every slide ([systemPrompt] is
 * `prompts/video_deck.md`, `{{slides}}` the slide count, `{{seconds}}` how long each slide
 * shows, `{{examples}}` the closest few-shot examples), so the slides tell one story in one palette. Asked once more if the reply isn't a usable deck
 * or fails [slideDeckProblems].
 */
class SlideDeckWriter(
    private val systemPrompt: String,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend fun write(agent: TextAgent, topic: String, slides: Int, secondsPerSlide: Int, content: String = ""): ApiResult<SlideDeck> {
        val system = systemPrompt
            .replace("{{slides}}", slides.toString())
            .replace("{{seconds}}", secondsPerSlide.toString())
            .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, topic)))
        return askTwice(agent, system, "Topic: $topic${contentBlock(content)}", ::parseSlideDeck, ::slideDeckProblems)
    }
}

/**
 * The workflow's first step (docs/requirements/14, MW-1 and MW-2): the text agent writes technical
 * content on the prompt with no length limit ([systemPrompt] is `prompts/media_content.md`), and
 * the infographic or the video's deck is planned from that content rather than the bare prompt.
 */
class MediaContentWriter(private val systemPrompt: String) {
    suspend fun write(agent: TextAgent, prompt: String): ApiResult<String> = when (val result = agent.generate(systemPrompt, prompt)) {
        is ApiResult.Failed -> result
        is ApiResult.Ok -> result.value.trim().takeIf { it.isNotEmpty() }?.let { ApiResult.Ok(it) }
            ?: ApiResult.Failed(ApiError.BadResponse("no technical content in the reply"))
    }
}

private fun contentBlock(content: String) =
    if (content.isBlank()) "" else "\n\nTechnical content to represent:\n${content.trim()}"

private fun feedbackBlock(feedback: List<String>) = if (feedback.isEmpty()) {
    ""
} else {
    "\n\nA review rejected the last image drawn for this topic:\n${feedback.joinToString("\n") { "- $it" }}\nWrite a new definition that fixes these problems."
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
            onFailure = { listOf(it.message.orEmpty().ifBlank { "no infographic in the reply" }.also { problem = it }) },
        )
        message = "$user\n\nYour last reply couldn't be used: ${problems.joinToString("; ")}. Reply with the corrected JSON only."
    }
    best?.let { return ApiResult.Ok(it.first) }
    return ApiResult.Failed(ApiError.BadResponse(problem.ifBlank { "no infographic in the reply" }))
}

package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.SlideDeck
import com.smassistant.app.infographic.parseInfographic
import com.smassistant.app.infographic.parseSlideDeck
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
 * definition is asked for once more before it fails.
 */
class InfographicWriter(
    private val systemPrompt: String,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend fun write(agent: TextAgent, topic: String, layoutIdea: String): ApiResult<InfographicSpec> {
        val system = systemPrompt.replace("{{examples}}", formatImageExamples(pickImageExamples(examples, topic)))
        return askTwice(agent, system, "Topic: $topic\n\nLayout idea: $layoutIdea", ::parseInfographic)
    }
}

/**
 * The video's "LLM generates content" step: one call plans every slide ([systemPrompt] is
 * `prompts/video_deck.md`, `{{slides}}` the slide count, `{{examples}}` the closest few-shot
 * examples), so the slides tell one story in one palette. Asked once more if the reply isn't a usable deck.
 */
class SlideDeckWriter(
    private val systemPrompt: String,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend fun write(agent: TextAgent, topic: String, slides: Int): ApiResult<SlideDeck> {
        val system = systemPrompt
            .replace("{{slides}}", slides.toString())
            .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, topic)))
        return askTwice(agent, system, "Topic: $topic", ::parseSlideDeck)
    }
}

private suspend fun <T> askTwice(agent: TextAgent, system: String, user: String, parse: (String) -> Result<T>): ApiResult<T> {
    var problem = ""
    repeat(2) {
        val reply = when (val result = agent.generate(system, user)) {
            is ApiResult.Failed -> return result
            is ApiResult.Ok -> result.value
        }
        parse(reply).onSuccess { return ApiResult.Ok(it) }.onFailure { problem = it.message.orEmpty() }
    }
    return ApiResult.Failed(ApiError.BadResponse(problem.ifBlank { "no infographic in the reply" }))
}

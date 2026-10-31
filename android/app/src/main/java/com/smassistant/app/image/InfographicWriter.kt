package com.smassistant.app.image

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.image.render.Carousel
import com.smassistant.app.image.render.InfographicSpec
import com.smassistant.app.image.render.MAX_CAROUSEL_PAGES
import com.smassistant.app.image.render.MIN_CAROUSEL_PAGES
import com.smassistant.app.image.render.carouselProblems
import com.smassistant.app.image.render.infographicProblems
import com.smassistant.app.image.render.parseCarousel
import com.smassistant.app.image.render.parseInfographic
import com.smassistant.app.image.render.templateProblems
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
 * [systemPrompt] (`prompts/image/infographic.md`, its `{{examples}}` filled with the few-shot examples
 * of the brief's template and those closest to the topic) and reads back the JSON definition. A reply that isn't a usable
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
        brief: ImageBrief? = null,
    ): ApiResult<InfographicSpec> {
        val picked = pickImageExamples(examples, topic, template = brief?.template?.id)
        val system = systemPrompt.replace("{{examples}}", formatImageExamples(picked))
        val user = "Topic: $topic${contentBlock(content)}${templateBlock(brief, content)}\n\nLayout idea: $layoutIdea${feedbackBlock(feedback)}"
        val result = askTwice(agent, system, user, ::parseInfographic) {
            infographicProblems(it, "$topic\n$content") + templateProblems(it, brief?.template?.id, content)
        }
        // The brief's format is the one the image is made in, whatever the model chose, and the brand is the user's.
        return if (brief != null && result is ApiResult.Ok) {
            ApiResult.Ok(result.value.copy(format = brief.format.infographic, brand = brief.style.brandName.ifBlank { null }))
        } else {
            result
        }
    }
}

/**
 * The image template the infographic follows (docs/design/image-templates.md): its rules, filled
 * with the style inputs. The content isn't repeated; the template points at the content above.
 */
private fun templateBlock(brief: ImageBrief?, content: String): String {
    brief ?: return ""
    val source = if (content.isBlank()) "(the topic above)" else "(the technical content above)"
    val rules = brief.copy(content = source).filled()
    // The style guide's colours become the palette (design §5); the rest of it stays in the template's rules.
    val palette = brief.style.styleGuide.trim().takeIf { it.isNotEmpty() }
        ?.let { " Pick the palette closest to the style guide \"$it\"." }.orEmpty()
    return "\n\nImage template: ${brief.template.name}, ${brief.format.words}. Plan the infographic by this template. " +
        "The app draws the image from your JSON, so the template decides what to show and how to arrange it; " +
        "keep its content rules and turn its design rules into your choice of sections.$palette\n\n$rules"
}

/**
 * Writes a carousel (docs/design/single-image-carousel.md) in one text-agent call ([systemPrompt] is
 * `prompts/image/carousel.md`, `{{pages}}` the most pages, `{{examples}}` the closest few-shot examples),
 * so the pages tell one story in one palette. Asked once more if the reply isn't a usable carousel or
 * fails [carouselProblems], with [fits] saying which pages are too full for their square. A [brief]
 * adds the image template the carousel follows; its format is ignored, since pages are always square.
 * The brand is the user's, on every page.
 */
class CarouselWriter(
    private val systemPrompt: String,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend fun write(
        agent: TextAgent,
        topic: String,
        content: String = "",
        brief: ImageBrief? = null,
        fits: (InfographicSpec) -> Boolean = { true },
        maxPages: Int = MAX_CAROUSEL_PAGES,
    ): ApiResult<Carousel> {
        val system = systemPrompt
            .replace("{{pages}}", maxPages.coerceIn(MIN_CAROUSEL_PAGES, MAX_CAROUSEL_PAGES).toString())
            .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, topic, template = brief?.template?.id)))
        val user = "Topic: $topic${contentBlock(content)}${carouselTemplateBlock(brief, content)}"
        val result = askTwice(agent, system, user, ::parseCarousel) { carouselProblems(it, "$topic\n$content", fits) }
        val brand = brief?.style?.brandName?.ifBlank { null } ?: return result
        return if (result is ApiResult.Ok) ApiResult.Ok(result.value.copy(pages = result.value.pages.map { it.copy(brand = brand) })) else result
    }
}

/** The image template a carousel follows: its content rules, while the carousel rules decide the pages. */
private fun carouselTemplateBlock(brief: ImageBrief?, content: String): String {
    brief ?: return ""
    val source = if (content.isBlank()) "(the topic above)" else "(the technical content above)"
    return "\n\nImage template: ${brief.template.name}. Plan the carousel by this template: it decides what to show; " +
        "the carousel rules decide the pages, which are all square.\n\n" + brief.copy(content = source).filled()
}

/**
 * The image pipeline's first step (docs/requirements/14, MW-1 and MW-2; 19, SP-11): the text agent
 * writes technical content on the prompt with no length limit ([systemPrompt] is
 * `prompts/image/image_content.md`), and the poster is planned from that content rather than the bare prompt.
 */
class ImageContentWriter(private val systemPrompt: String) {
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

package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.agents.appImageAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.AppConfig
import com.smassistant.app.infographic.ImageInspector
import com.smassistant.app.infographic.InfographicPipeline
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.imageProblem
import com.smassistant.app.media.AndroidImageInspector
import com.smassistant.app.media.AndroidInfographicComposer
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.PaintTextMeasurer
import com.smassistant.app.media.reviewJpeg
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Name used when a definition can't be drawn, e.g. "Unexpected response from the infographic renderer: …". */
const val INFOGRAPHIC_RENDERER = "the infographic renderer"

/**
 * The infographic pipeline's parts: the text agent that writes each definition, how it is drawn,
 * the [content] step that first writes the no-limit technical content the definitions represent,
 * and the [reviewer] that looks at each finished image (an image it scores too low is written and
 * drawn again, up to [retries] times).
 */
class Infographics(
    val textAgent: () -> TextAgent?,
    val writer: InfographicWriter,
    val pipeline: InfographicPipeline,
    val content: MediaContentWriter? = null,
    val reviewer: ImageReviewer? = null,
    val retries: Int = 1,
)

/** Smallest side an image the image agent paints whole (without a text agent) may have. */
const val MIN_PAINTED_SIDE = 512

/**
 * Generate Image (docs/tasks IM-I1, IM-I6). With a text agent set up it runs the infographic
 * pipeline (docs/design/image-video-pipeline.md): the text agent first writes technical content
 * on the prompt with no length limit (docs/requirements/14), then [count] definitions of that
 * content are written in parallel, each with its own layout idea, drawn with illustrations from the selected image agent. Without a text
 * agent it falls back to asking the image agent for finished images, with the style from
 * `assets/prompts/image.md` and the few-shot [examples] closest to the topic.
 *
 * Every image is checked before it is finished (docs/design/image-video-pipeline.md, "Checks before
 * an image is finished"): the definition by [InfographicWriter], the pictures and the PNG by the
 * pipeline, and the finished infographic by [Infographics.reviewer]. Without a text agent, painted
 * images that [inspector] finds blank or too small are painted once more and then left out.
 */
class GenerateImagesUseCase(
    private val styleTemplate: String,
    private val agent: () -> ImageAgent?,
    private val count: Int,
    private val examples: List<ImageExample> = emptyList(),
    private val infographics: Infographics? = null,
    private val inspector: ImageInspector? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<List<File>> = generate(ImageRequest(prompt))

    /** Generate Image for a [request]; its template and format aren't used yet (docs/tasks IT-I4). */
    suspend fun generate(request: ImageRequest): Outcome<List<File>> {
        val topic = request.prompt.trim()
        infographics?.let { parts -> parts.textAgent()?.let { writer -> return infographics(parts, writer, topic) } }
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(imagePrompt(topic), count)) {
            is ApiResult.Ok -> painted(selected, topic, result.value)
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }

    /** Keeps the painted images that pass the checks, asking once more for as many as failed. */
    private suspend fun painted(selected: ImageAgent, topic: String, images: List<File>): Outcome<List<File>> {
        val check = inspector ?: return Outcome.Ok(images)
        val good = images.filter { imageProblem(check.facts(it), MIN_PAINTED_SIDE) == null }
        val missing = images.size - good.size
        val more = if (missing == 0) {
            emptyList()
        } else {
            when (val again = selected.generate(imagePrompt(topic), missing)) {
                is ApiResult.Ok -> again.value.filter { imageProblem(check.facts(it), MIN_PAINTED_SIDE) == null }
                is ApiResult.Failed -> emptyList()
            }
        }
        return (good + more).takeIf { it.isNotEmpty() }?.let { Outcome.Ok(it) }
            ?: Outcome.Failed(ApiError.BadResponse("the images came back blank or too small"), selected.service.displayName)
    }

    private suspend fun infographics(parts: Infographics, writer: TextAgent, topic: String): Outcome<List<File>> {
        val content = parts.content?.let { step ->
            when (val result = step.write(writer, topic)) {
                is ApiResult.Failed -> return Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
        }.orEmpty()
        val illustrator = agent()
        val outcomes = coroutineScope {
            List(count) { i ->
                async { infographic(parts, writer, topic, INFOGRAPHIC_LAYOUT_IDEAS[i % INFOGRAPHIC_LAYOUT_IDEAS.size], content, illustrator) }
            }.awaitAll()
        }
        val files = outcomes.filterIsInstance<Outcome.Ok<File>>().map { it.value }
        return if (files.isNotEmpty()) Outcome.Ok(files) else outcomes.first() as Outcome.Failed
    }

    /**
     * One infographic: written, drawn and reviewed. One the review scores too low is written again
     * with the review's problems, up to [Infographics.retries] times, and the best-scored is kept.
     */
    private suspend fun infographic(
        parts: Infographics,
        writer: TextAgent,
        topic: String,
        idea: String,
        content: String,
        illustrator: ImageAgent?,
    ): Outcome<File> {
        val reviewer = parts.reviewer
        var feedback = emptyList<String>()
        var best: Pair<File, Int>? = null
        repeat(parts.retries + 1) {
            val spec = when (val result = parts.writer.write(writer, topic, idea, content, feedback)) {
                is ApiResult.Failed -> return best?.let { Outcome.Ok(it.first) } ?: Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
            val file = parts.pipeline.draw(spec, illustrator)
            if (file == null) {
                feedback = listOf("the image couldn't be drawn or came out blank; use simpler sections")
                return@repeat
            }
            val review = reviewer?.review(writer, topic, spec, file) ?: return Outcome.Ok(file)
            if (reviewer.passes(review)) return Outcome.Ok(file)
            if (best == null || review.score > best!!.second) best = file to review.score
            feedback = review.problems.ifEmpty { listOf("it scored ${review.score}/10; make it clearer and closer to the topic") }
        }
        return best?.let { Outcome.Ok(it.first) }
            ?: Outcome.Failed(ApiError.BadResponse("the infographic couldn't be drawn"), INFOGRAPHIC_RENDERER)
    }

    private fun imagePrompt(prompt: String) = styleTemplate
        .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, prompt)))
        .replace("{{prompt}}", prompt)
}

/**
 * The image generator the screens use: the default text agent writes the definitions, the
 * selected image agent paints the illustrations, and the old style and example assets are the fallback.
 */
fun appGenerateImages(context: Context): GenerateImagesUseCase {
    val cache = MediaCache(context)
    val examples = parseImageExamples(loadPromptAsset(context, "image_examples"))
    return GenerateImagesUseCase(
        styleTemplate = loadPromptAsset(context, "image"),
        agent = appImageAgentSelector(context, cache)::imageAgent,
        count = IMAGES_PER_REQUEST,
        examples = examples,
        infographics = appInfographics(context, cache, examples),
        inspector = AndroidImageInspector,
    )
}

/**
 * The pipeline's real parts: the default text agent, the no-limit content and infographic
 * prompts, Paint-measured layout, 2x composing, and the checks set in app.yml (`image_checks`).
 */
internal fun appInfographics(context: Context, cache: MediaCache, examples: List<ImageExample>): Infographics {
    val checks = AppConfig.imageChecks(context)
    return Infographics(
        textAgent = appAgentSelector(context)::textAgent,
        writer = InfographicWriter(loadPromptAsset(context, "infographic"), examples),
        pipeline = InfographicPipeline(
            renderer = InfographicRenderer(PaintTextMeasurer),
            illustrationTemplate = loadPromptAsset(context, "illustration"),
            composer = AndroidInfographicComposer(cache),
            inspector = AndroidImageInspector,
        ),
        content = appMediaContentWriter(context),
        reviewer = if (checks.visionReview) ImageReviewer(loadPromptAsset(context, "image_review"), ::reviewJpeg, checks.minScore) else null,
        retries = if (checks.visionReview) checks.retries else 0,
    )
}

/** Writes the no-limit technical content an image or video represents (docs/requirements/14). */
internal fun appMediaContentWriter(context: Context) = MediaContentWriter(loadPromptAsset(context, "media_content"))

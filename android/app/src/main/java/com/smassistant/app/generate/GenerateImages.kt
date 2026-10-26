package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.agents.appImageAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.infographic.InfographicPipeline
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.media.AndroidInfographicComposer
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.PaintTextMeasurer
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Name used when a definition can't be drawn, e.g. "Unexpected response from the infographic renderer: …". */
const val INFOGRAPHIC_RENDERER = "the infographic renderer"

/** The infographic pipeline's parts: the text agent that writes each definition, and how it is drawn. */
class Infographics(
    val textAgent: () -> TextAgent?,
    val writer: InfographicWriter,
    val pipeline: InfographicPipeline,
)

/**
 * Generate Image (docs/tasks IM-I1, IM-I6). With a text agent set up it runs the infographic
 * pipeline (docs/design/image-video-pipeline.md): [count] definitions written in parallel, each
 * with its own layout idea, drawn with illustrations from the selected image agent. Without a text
 * agent it falls back to asking the image agent for finished images, with the style from
 * `assets/prompts/image.md` and the few-shot [examples] closest to the topic.
 */
class GenerateImagesUseCase(
    private val styleTemplate: String,
    private val agent: () -> ImageAgent?,
    private val count: Int,
    private val examples: List<ImageExample> = emptyList(),
    private val infographics: Infographics? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<List<File>> {
        val topic = prompt.trim()
        infographics?.let { parts -> parts.textAgent()?.let { writer -> return infographics(parts, writer, topic) } }
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(imagePrompt(topic), count)) {
            is ApiResult.Ok -> Outcome.Ok(result.value)
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }

    private suspend fun infographics(parts: Infographics, writer: TextAgent, topic: String): Outcome<List<File>> {
        val illustrator = agent()
        val outcomes = coroutineScope {
            List(count) { i ->
                async {
                    val idea = INFOGRAPHIC_LAYOUT_IDEAS[i % INFOGRAPHIC_LAYOUT_IDEAS.size]
                    when (val spec = parts.writer.write(writer, topic, idea)) {
                        is ApiResult.Failed -> Outcome.Failed(spec.error, writer.service.displayName)
                        is ApiResult.Ok -> parts.pipeline.draw(spec.value, illustrator)?.let { Outcome.Ok(it) }
                            ?: Outcome.Failed(ApiError.BadResponse("the infographic couldn't be drawn"), INFOGRAPHIC_RENDERER)
                    }
                }
            }.awaitAll()
        }
        val files = outcomes.filterIsInstance<Outcome.Ok<File>>().map { it.value }
        return if (files.isNotEmpty()) Outcome.Ok(files) else outcomes.first() as Outcome.Failed
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
        agent = appImageAgentSelector(context, cache, illustrations = true)::imageAgent,
        count = IMAGES_PER_REQUEST,
        examples = examples,
        infographics = appInfographics(context, cache, examples),
    )
}

/** The pipeline's real parts: the default text agent, the infographic prompt, Paint-measured layout and 2x composing. */
internal fun appInfographics(context: Context, cache: MediaCache, examples: List<ImageExample>) = Infographics(
    textAgent = appAgentSelector(context)::textAgent,
    writer = InfographicWriter(loadPromptAsset(context, "infographic"), examples),
    pipeline = InfographicPipeline(
        renderer = InfographicRenderer(PaintTextMeasurer),
        illustrationTemplate = loadPromptAsset(context, "illustration"),
        composer = AndroidInfographicComposer(cache),
    ),
)

package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.agents.appImageAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.infographic.InfographicPipeline
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.media.AndroidImageInspector
import com.smassistant.app.media.AndroidInfographicComposer
import com.smassistant.app.media.Media3SlideshowMaker
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.PaintTextMeasurer
import com.smassistant.app.media.SlideshowMaker
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.loadPromptAsset
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The fewest slides a Generate Video submit plans, as for the shortest length. */
const val SLIDES_PER_VIDEO = 4

/** Fewer slides than this isn't worth a video, so the submit fails instead. */
const val MIN_SLIDES = 2

/** Each video slide gets at most one AI illustration, its header picture. */
const val ILLUSTRATIONS_PER_SLIDE = 1

/** Name used in video errors, e.g. "Unexpected response from the video maker: …". */
const val VIDEO_MAKER = "the video maker"

/**
 * A generated video: a slideshow of [slides], [seconds] long, or an AI clip from a text-to-video
 * model when [slides] is empty (its length is the model's, so [seconds] is 0).
 */
data class GeneratedVideo(val file: File, val slides: List<File>, val seconds: Int = DEFAULT_VIDEO_SECONDS)

/**
 * The video pipeline's parts: the text agent that plans the deck, how each slide is drawn, and
 * the [content] step that first writes the no-limit technical content the deck represents.
 */
class SlideDecks(
    val textAgent: () -> TextAgent?,
    val writer: SlideDeckWriter,
    val pipeline: InfographicPipeline,
    val content: MediaContentWriter? = null,
)

/**
 * Generate Video (docs/tasks VI-I1, VI-I4, Q-08 = slideshow). With a text agent set up it runs the
 * infographic pipeline (docs/design/image-video-pipeline.md): the text agent first writes
 * technical content on the prompt with no length limit (docs/requirements/14), one call plans
 * all slides of that content as a deck, each slide is drawn with its illustration from the video agent, and [maker] encodes them
 * in order into an MP4. Without a text agent the video agent paints each slide from
 * `assets/prompts/video.md`, as before. Slides that fail are left out.
 *
 * The video is as long as the length picked on the screen ([seconds], read on each submit,
 * Requirement 9): that sets how many slides are planned ([slidesFor]) and the slides that
 * come back share the time equally.
 */
class GenerateVideoUseCase(
    private val slideTemplate: String,
    private val agent: () -> ImageAgent?,
    private val maker: SlideshowMaker,
    private val newVideoFile: () -> File,
    private val seconds: () -> Int = { DEFAULT_VIDEO_SECONDS },
    private val decks: SlideDecks? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<GeneratedVideo> {
        val topic = prompt.trim()
        val length = seconds()
        val slides = slidesFor(length)
        val drawn = decks?.let { parts -> parts.textAgent()?.let { writer -> deckSlides(parts, writer, topic, slides, length / slides) } }
            ?: paintedSlides(topic, slides)
            ?: return Outcome.NoAgent
        val images = when (drawn) {
            is Outcome.Ok -> drawn.value
            is Outcome.Failed -> return drawn
            Outcome.NoAgent -> return Outcome.NoAgent
        }
        return maker.make(images, newVideoFile(), length * 1000L / images.size).fold(
            onSuccess = { Outcome.Ok(GeneratedVideo(it, images, length)) },
            onFailure = { Outcome.Failed(ApiError.BadResponse(it.message ?: "couldn't encode the video"), VIDEO_MAKER) },
        )
    }

    private suspend fun deckSlides(
        parts: SlideDecks,
        writer: TextAgent,
        topic: String,
        slides: Int,
        secondsPerSlide: Int,
    ): Outcome<List<File>> {
        val content = parts.content?.let { step ->
            when (val result = step.write(writer, topic)) {
                is ApiResult.Failed -> return Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
        }.orEmpty()
        val deck = when (val result = parts.writer.write(writer, topic, slides, secondsPerSlide, content)) {
            is ApiResult.Failed -> return Outcome.Failed(result.error, writer.service.displayName)
            is ApiResult.Ok -> result.value
        }
        val illustrator = agent()
        val images = coroutineScope {
            deck.slides.map { slide -> async { parts.pipeline.draw(slide, illustrator, ILLUSTRATIONS_PER_SLIDE) } }.awaitAll()
        }.filterNotNull()
        return if (images.size >= MIN_SLIDES) {
            Outcome.Ok(images)
        } else {
            Outcome.Failed(ApiError.BadResponse("only ${images.size} slide could be drawn"), INFOGRAPHIC_RENDERER)
        }
    }

    /** The fallback without a text agent; null when there's no video agent either. */
    private suspend fun paintedSlides(topic: String, slides: Int): Outcome<List<File>>? {
        val selected = agent() ?: return null
        val results = coroutineScope {
            (1..slides).map { slide -> async { selected.generate(slidePrompt(topic, slide, slides), 1) } }.awaitAll()
        }
        val images = results.filterIsInstance<ApiResult.Ok<List<File>>>().flatMap { it.value }
        if (images.size >= MIN_SLIDES) return Outcome.Ok(images)
        val error = results.filterIsInstance<ApiResult.Failed>().firstOrNull()?.error
            ?: ApiError.BadResponse("only ${images.size} slide came back")
        return Outcome.Failed(error, selected.service.displayName)
    }

    private fun slidePrompt(prompt: String, slide: Int, slides: Int) = slideTemplate
        .replace("{{prompt}}", prompt)
        .replace("{{slide}}", slide.toString())
        .replace("{{slides}}", slides.toString())
}

/**
 * The video generator the screens use: the default text agent writes the content and plans the deck, the selected video
 * agent and model paint the illustrations, slides are drawn at 1080 px (the video's size) and
 * encoded on the device, as long as the length picked on the screen.
 */
fun appGenerateVideo(context: Context): GenerateVideoUseCase {
    val cache = MediaCache(context)
    val selector = appImageAgentSelector(context, cache, MediaPurpose.VIDEO, illustrations = true)
    return GenerateVideoUseCase(
        slideTemplate = loadPromptAsset(context, "video"),
        agent = selector::imageAgent,
        maker = Media3SlideshowMaker(context.applicationContext),
        newVideoFile = { cache.newFile("mp4") },
        seconds = AgentPreferences(context)::videoSeconds,
        decks = SlideDecks(
            textAgent = appAgentSelector(context)::textAgent,
            writer = SlideDeckWriter(
                loadPromptAsset(context, "video_deck"),
                parseImageExamples(loadPromptAsset(context, "image_examples")),
            ),
            pipeline = InfographicPipeline(
                renderer = InfographicRenderer(PaintTextMeasurer),
                illustrationTemplate = loadPromptAsset(context, "illustration"),
                composer = AndroidInfographicComposer(cache, scale = 1f),
                inspector = AndroidImageInspector,
            ),
            content = appMediaContentWriter(context),
        ),
    )
}

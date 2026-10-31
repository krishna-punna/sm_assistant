package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.agents.appImageAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.AppConfig
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.infographic.InfographicPipeline
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.SlideDeck
import com.smassistant.app.infographic.revealSteps
import com.smassistant.app.media.AndroidFrameBlender
import com.smassistant.app.media.AndroidImageInspector
import com.smassistant.app.media.AndroidInfographicComposer
import com.smassistant.app.media.AndroidSizeGuard
import com.smassistant.app.media.FrameBlender
import com.smassistant.app.media.Media3SlideshowMaker
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.PaintTextMeasurer
import com.smassistant.app.media.SizeGuard
import com.smassistant.app.media.SlideshowMaker
import com.smassistant.app.media.nearestImageFormat
import com.smassistant.app.media.reviewJpeg
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
 * model when [slides] is empty (its length is the model's, so [seconds] is 0), in [format].
 */
data class GeneratedVideo(
    val file: File,
    val slides: List<File>,
    val seconds: Int = DEFAULT_VIDEO_SECONDS,
    val format: VideoFormat = DEFAULT_VIDEO_FORMAT,
)

/**
 * A slide ready for the video: its pictures in order ([frames], several for a step-by-step
 * reveal, the last one the whole slide), the seconds its scene asked for (null: an equal share)
 * and the [spec] it was drawn from.
 */
internal data class DrawnSlide(val frames: List<File>, val seconds: Int? = null, val spec: InfographicSpec? = null) {
    constructor(file: File, seconds: Int? = null) : this(listOf(file), seconds)

    /** The whole slide. */
    val file: File get() = frames.last()
}

/** [millis] split into [parts] equal shares, the remainder on the last. */
private fun split(millis: Long, parts: Int): List<Long> = List(parts) { i -> millis / parts + if (i == parts - 1) millis % parts else 0 }

/** The slides of a video, the size they were drawn for and the video template they follow. */
internal data class DrawnVideo(val slides: List<DrawnSlide>, val format: VideoFormat, val templateId: String? = null)

/**
 * The video pipeline's parts: the text agent that plans the deck, how each slide is drawn, the
 * [content] step that first writes the no-limit technical content the deck represents, the
 * video [templates] the chooser picks from for that content (docs/design/video-templates.md), and
 * the [reviewer] that looks at every finished slide: when one scores too low the deck is planned
 * again with the problems, up to [retries] times, and the better-scored video is kept.
 */
class SlideDecks(
    val textAgent: () -> TextAgent?,
    val writer: SlideDeckWriter,
    val pipeline: InfographicPipeline,
    val content: MediaContentWriter? = null,
    val templates: Templates? = null,
    val reviewer: ImageReviewer? = null,
    val retries: Int = 1,
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
 * Requirement 9): that sets how many slides are planned ([slidesFor]), and the slides that
 * come back share the time by the seconds each scene asked for ([sceneMillis], at least
 * [MIN_SCENE_SECONDS] each), or equally when the storyboard gives none. A `reveal` scene is shown
 * in steps, one more section each ([revealSteps]), the steps sharing its time. With video
 * templates the chooser picks one for the technical content (the prompt when there is none), and
 * the deck follows it; a failed choice gives the Universal Master. With a [blender] the scenes
 * fade into each other.
 */
class GenerateVideoUseCase(
    private val slideTemplate: String,
    private val agent: () -> ImageAgent?,
    private val maker: SlideshowMaker,
    private val newVideoFile: () -> File,
    private val seconds: () -> Int = { DEFAULT_VIDEO_SECONDS },
    private val decks: SlideDecks? = null,
    private val blender: FrameBlender? = null,
    private val sizeGuard: SizeGuard? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<GeneratedVideo> {
        val topic = prompt.trim()
        val length = seconds()
        val slides = slidesFor(length)
        val drawn = decks?.let { parts -> parts.textAgent()?.let { writer -> deckSlides(parts, writer, topic, slides, length) } }
            ?: paintedSlides(topic, slides)
            ?: return Outcome.NoAgent
        val made = when (drawn) {
            is Outcome.Ok -> drawn.value
            is Outcome.Failed -> return drawn
            Outcome.NoAgent -> return Outcome.NoAgent
        }
        val video = sizeGuard?.let { guard -> guarded(made, guard) } ?: made
        val images = video.slides.map { it.file }
        val (frames, millis) = timeline(video, sceneMillis(video.slides.map { it.seconds }, length))
        // A video made from a template carries it and its size in the file name, so saved results keep them.
        val output = newVideoFile().let { file -> video.templateId?.let { labelVideo(file, it, video.format) } ?: file }
        return maker.make(frames, output, millis, video.format.width, video.format.height).fold(
            onSuccess = { Outcome.Ok(GeneratedVideo(it, images, length, video.format)) },
            onFailure = { Outcome.Failed(ApiError.BadResponse(it.message ?: "couldn't encode the video"), VIDEO_MAKER) },
        )
    }

    /**
     * Every frame of the video and how long it shows: a revealed scene's steps share its time
     * equally, and with a [blender] each scene fades into the next over [FADE_FRAMES] blended
     * frames taken from the end of its time. A scene too short for that, or a blend that fails,
     * keeps a hard cut.
     */
    private suspend fun timeline(video: DrawnVideo, sceneMillis: List<Long>): Pair<List<File>, List<Long>> {
        val frames = mutableListOf<File>()
        val millis = mutableListOf<Long>()
        video.slides.zip(sceneMillis).forEachIndexed { i, (slide, time) ->
            val next = video.slides.getOrNull(i + 1)
            val fade = FADE_FRAMES * FADE_FRAME_MILLIS
            val blends = if (blender != null && next != null && time >= MIN_FADED_SCENE_MILLIS) {
                fadeAmounts().map { blender.blend(slide.file, next.frames.first(), it, video.format.width, video.format.height) }
                    .takeIf { all -> all.all { it != null } }?.filterNotNull()
            } else {
                null
            }
            val shown = split(if (blends != null) time - fade else time, slide.frames.size)
            frames += slide.frames
            millis += shown
            if (blends != null) {
                frames += blends
                millis += List(blends.size) { FADE_FRAME_MILLIS }
            }
        }
        return frames to millis
    }

    private suspend fun deckSlides(
        parts: SlideDecks,
        writer: TextAgent,
        topic: String,
        slides: Int,
        seconds: Int,
    ): Outcome<DrawnVideo> {
        val content = parts.content?.let { step ->
            when (val result = step.write(writer, topic)) {
                is ApiResult.Failed -> return Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
        }.orEmpty()
        val brief = parts.templates?.let { templates ->
            val choice = templates.chooser.choose(writer, content.ifBlank { topic })
            VideoBrief(choice.primary, content, seconds, style = templates.style())
        }
        val format = brief?.format ?: DEFAULT_VIDEO_FORMAT
        val reviewer = parts.reviewer
        var feedback = emptyList<String>()
        var best: Pair<DrawnVideo, Int>? = null
        var failure: Outcome.Failed? = null
        repeat(if (reviewer == null) 1 else parts.retries + 1) {
            val deck = when (val result = parts.writer.write(writer, topic, slides, seconds / slides, content, brief, feedback)) {
                is ApiResult.Failed -> return best?.let { Outcome.Ok(it.first) } ?: Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
            val video = when (val drawn = drawDeck(parts, deck, format, brief?.template?.id)) {
                is Outcome.Ok -> drawn.value
                is Outcome.Failed -> {
                    failure = drawn
                    feedback = listOf("the slides couldn't be drawn; use simpler sections")
                    return@repeat
                }
                Outcome.NoAgent -> return Outcome.NoAgent
            }
            reviewer ?: return Outcome.Ok(video)
            val reviews = coroutineScope {
                video.slides.map { slide -> async { slide.spec?.let { reviewer.review(writer, topic, it, slide.file, brief?.template) } } }.awaitAll()
            }
            val failed = reviews.withIndex().filter { (_, review) -> review != null && !reviewer.passes(review) }
            if (failed.isEmpty()) return Outcome.Ok(video)
            // The video is as good as its weakest scene.
            val score = reviews.filterNotNull().minOf { it.score }
            if (best == null || score > best!!.second) best = video to score
            feedback = failed.flatMap { (i, review) ->
                review!!.problems.ifEmpty { listOf("it scored ${review.score}/10; make it clearer") }.map { "slide ${i + 1}: $it" }
            }
        }
        return best?.let { Outcome.Ok(it.first) } ?: failure!!
    }

    /** Draws every slide of [deck] at [format], for the template [templateId]; slides that can't be drawn are left out, and too few fail. */
    private suspend fun drawDeck(parts: SlideDecks, deck: SlideDeck, format: VideoFormat, templateId: String?): Outcome<DrawnVideo> {
        val illustrator = agent()
        val images = coroutineScope {
            deck.slides.zip(deck.scenes).map { (slide, scene) ->
                // Every slide is drawn at the video's size, whatever the deck said.
                val sized = slide.copy(format = format.infographic)
                val steps = if (scene.reveal) sized.revealSteps() else listOf(sized)
                async {
                    parts.pipeline.drawSteps(sized, steps, illustrator, ILLUSTRATIONS_PER_SLIDE)
                        .takeIf { it.isNotEmpty() }
                        ?.let { DrawnSlide(it, scene.seconds, sized) }
                }
            }.awaitAll()
        }.filterNotNull()
        return if (images.size >= MIN_SLIDES) {
            Outcome.Ok(DrawnVideo(images, format, templateId))
        } else {
            Outcome.Failed(ApiError.BadResponse("only ${images.size} slide could be drawn"), INFOGRAPHIC_RENDERER)
        }
    }

    /**
     * The last step: every frame has the video's ratio, padded when it is off by more than 2%
     * (docs/requirements/18, SV-9), so the encoder never stretches a picture.
     */
    private suspend fun guarded(video: DrawnVideo, guard: SizeGuard): DrawnVideo {
        val ratio = listOf(video.format.width to video.format.height)
        return video.copy(slides = video.slides.map { slide -> slide.copy(frames = slide.frames.map { guard.fit(it, ratio) }) })
    }

    /**
     * The fallback without a text agent; null when there's no video agent either. Each slide is
     * painted at the image size closest to the video's (2:3 for 9:16) and padded to it by the guard.
     */
    private suspend fun paintedSlides(topic: String, slides: Int): Outcome<DrawnVideo>? {
        val selected = agent() ?: return null
        val format = DEFAULT_VIDEO_FORMAT
        val paintAt = nearestImageFormat(format.width.toFloat(), format.height.toFloat())
        val results = coroutineScope {
            (1..slides).map { slide -> async { selected.generate(slidePrompt(topic, slide, slides, format), 1, paintAt) } }.awaitAll()
        }
        val images = results.filterIsInstance<ApiResult.Ok<List<File>>>().flatMap { it.value }
        if (images.size >= MIN_SLIDES) return Outcome.Ok(DrawnVideo(images.map { DrawnSlide(it) }, format))
        val error = results.filterIsInstance<ApiResult.Failed>().firstOrNull()?.error
            ?: ApiError.BadResponse("only ${images.size} slide came back")
        return Outcome.Failed(error, selected.service.displayName)
    }

    private fun slidePrompt(prompt: String, slide: Int, slides: Int, format: VideoFormat) = slideTemplate
        .replace("{{prompt}}", prompt)
        .replace("{{format}}", format.words)
        .replace("{{slide}}", slide.toString())
        .replace("{{slides}}", slides.toString())
}

/**
 * The video generator the screens and the scheduler use: the default text agent writes the content, picks the video template and plans the deck, the selected video
 * agent and model paint the illustrations, slides are drawn at the video's size (1080 x 1920, 1080 x 1080 or 1920 x 1080) and
 * encoded on the device, as long as the length picked on the screen.
 */
fun appGenerateVideo(context: Context): GenerateVideoUseCase {
    val cache = MediaCache(context)
    val checks = AppConfig.imageChecks(context)
    val selector = appImageAgentSelector(context, cache, MediaPurpose.VIDEO)
    return GenerateVideoUseCase(
        slideTemplate = loadPromptAsset(context, "video"),
        agent = selector::imageAgent,
        maker = Media3SlideshowMaker(context.applicationContext),
        blender = AndroidFrameBlender(cache),
        sizeGuard = AndroidSizeGuard,
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
            templates = appVideoTemplates(context),
            reviewer = if (checks.visionReview) ImageReviewer(loadPromptAsset(context, "image_review"), ::reviewJpeg, checks.minScore) else null,
            retries = if (checks.visionReview) checks.retries else 0,
        ),
    )
}

/** The video templates from `prompts/video_templates.md`, their chooser and the Image Style settings. */
internal fun appVideoTemplates(context: Context): Templates {
    val all = parseImageTemplates(loadPromptAsset(context, "video_templates"))
    return Templates(all, TemplateChooser(loadPromptAsset(context, "choose_template"), all, media = "video"), ImageStyleStore(context)::load)
}

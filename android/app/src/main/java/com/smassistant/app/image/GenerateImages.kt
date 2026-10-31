package com.smassistant.app.image

import android.content.Context
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.agents.appImageAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.AppConfig
import com.smassistant.app.data.ImageLayout
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.image.render.AndroidImageInspector
import com.smassistant.app.image.render.AndroidImageUpscaler
import com.smassistant.app.image.render.AndroidInfographicComposer
import com.smassistant.app.image.render.ImageInspector
import com.smassistant.app.image.render.InfographicPipeline
import com.smassistant.app.image.render.InfographicRenderer
import com.smassistant.app.image.render.PaintTextMeasurer
import com.smassistant.app.image.render.PosterFit
import com.smassistant.app.image.render.imageProblem
import com.smassistant.app.image.render.numbered
import com.smassistant.app.image.render.reviewJpeg
import com.smassistant.app.media.AndroidSizeGuard
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.SizeGuard
import com.smassistant.app.media.ratio
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.loadPromptAsset
import java.io.File
import kotlin.math.roundToInt
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
    val content: ImageContentWriter? = null,
    val reviewer: ImageReviewer? = null,
    val retries: Int = 1,
    val templates: Templates? = null,
    /** Writes the carousel pages when the Layout setting is Carousel (docs/requirements/18, SV-5); null never makes one. */
    val carousel: CarouselWriter? = null,
)

/**
 * The image templates (docs/design/image-templates.md): all of them, the [chooser] that picks
 * one for the content, the user's [style] inputs and the [painter] that writes the prompt of an
 * AI-painted image (without it every image is drawn). Without them each image gets a fixed layout idea.
 */
class Templates(
    val all: List<ImageTemplate>,
    val chooser: TemplateChooser,
    val style: () -> ImageStyle = { ImageStyle() },
    val painter: ImagePainter? = null,
)

/** Smallest side an image the image agent paints whole (without a text agent) may have. */
const val MIN_PAINTED_SIDE = 512

/**
 * The short side a finished painted portrait or landscape image should have (1024 x 1536, 1536 x 1024).
 * Gemini's 2:3 is about 832 x 1248, so smaller images are upscaled ([upscaleFactor]).
 */
const val PAINTED_SHORT_SIDE = 1024

/** The side a finished painted square should have, like the largest square sample (1536 x 1536), so 1024 squares are upscaled. */
const val PAINTED_SQUARE_SIDE = 1536

/** 2 when a [width] x [height] image is smaller than its minimum ([PAINTED_SQUARE_SIDE] for a square, [PAINTED_SHORT_SIDE] on the short side otherwise), else 1 (docs/design/image-templates.md §2). */
fun upscaleFactor(width: Int, height: Int): Int =
    if (minOf(width, height) < if (width == height) PAINTED_SQUARE_SIDE else PAINTED_SHORT_SIDE) 2 else 1

/** Enlarges a painted image by its [upscaleFactor], in place; returns the file. */
fun interface ImageUpscaler {
    suspend fun upscale(file: File): File
}

/**
 * Generate Image (docs/tasks IM-I1, IM-I6). With a text agent set up it runs the infographic
 * pipeline (docs/design/image-video-pipeline.md): the text agent first writes technical content
 * on the prompt with no length limit (docs/requirements/14), then [count] definitions of that
 * content are written in parallel, each with its own layout idea, drawn with illustrations from the selected image agent. Without a text
 * agent it falls back to asking the image agent for finished images, with the style from
 * `assets/prompts/image/image.md` and the few-shot [examples] closest to the topic.
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
    private val upscaler: ImageUpscaler? = null,
    private val sizeGuard: SizeGuard? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<List<File>> = generate(ImageRequest(prompt))

    /**
     * Generate Image for a [request]. With templates set up, the template the user picked (or the
     * one the chooser picks for the content) and the format the user picked shape every infographic.
     */
    suspend fun generate(request: ImageRequest): Outcome<List<File>> {
        val outcome = make(request)
        val guard = sizeGuard ?: return outcome
        // The last step: every image is 2:3, 1:1 or 3:2, padded when it is off by more than 2% (docs/requirements/18, SV-9).
        return if (outcome is Outcome.Ok) Outcome.Ok(outcome.value.map { guard.fit(it, IMAGE_RATIOS) }) else outcome
    }

    private suspend fun make(request: ImageRequest): Outcome<List<File>> {
        val topic = request.prompt.trim()
        infographics?.let { parts -> parts.textAgent()?.let { writer -> return infographics(parts, writer, topic, request) } }
        val selected = agent() ?: return Outcome.NoAgent
        // Auto is resolved before the agent is asked, so the model never picks the shape (docs/requirements/18, SV-8).
        val format = request.format ?: infographics?.templates?.style()?.format ?: ImageSize.PORTRAIT
        return when (val result = selected.generate(imagePrompt(topic), count, format)) {
            is ApiResult.Ok -> painted(selected, topic, result.value, format)
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }

    /**
     * Keeps the painted images that pass the checks, asking once more for as many as failed, and
     * upscales the ones under the painted minimum ([upscaleFactor]).
     */
    private suspend fun painted(selected: ImageAgent, topic: String, images: List<File>, format: ImageSize?): Outcome<List<File>> {
        val check = inspector ?: return Outcome.Ok(upscaled(images))
        val good = images.filter { imageProblem(check.facts(it), MIN_PAINTED_SIDE) == null }
        val missing = images.size - good.size
        val more = if (missing == 0) {
            emptyList()
        } else {
            when (val again = selected.generate(imagePrompt(topic), missing, format)) {
                is ApiResult.Ok -> again.value.filter { imageProblem(check.facts(it), MIN_PAINTED_SIDE) == null }
                is ApiResult.Failed -> emptyList()
            }
        }
        return (good + more).takeIf { it.isNotEmpty() }?.let { Outcome.Ok(upscaled(it)) }
            ?: Outcome.Failed(ApiError.BadResponse("the images came back blank or too small"), selected.service.displayName)
    }

    private suspend fun upscaled(images: List<File>): List<File> = upscaler?.let { up -> images.map { up.upscale(it) } } ?: images

    private suspend fun infographics(parts: Infographics, writer: TextAgent, topic: String, request: ImageRequest): Outcome<List<File>> {
        val content = parts.content?.let { step ->
            when (val result = step.write(writer, topic)) {
                is ApiResult.Failed -> return Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
        }.orEmpty()
        val illustrator = agent()
        val briefs = parts.templates?.let { templates -> briefs(templates, writer, topic, content, request) }
        val layout = parts.templates?.style()?.layout ?: ImageLayout.AUTO
        val outcomes = coroutineScope {
            List(count) { i ->
                val brief = briefs?.get(i)
                val idea = brief?.let { "${it.format.words}: ${layoutOf(it.template).sections}" }
                    ?: INFOGRAPHIC_LAYOUT_IDEAS[i % INFOGRAPHIC_LAYOUT_IDEAS.size]
                async {
                    val painter = parts.templates?.painter
                    // An agent that paints only one shape (Cloudflare: square) is asked for that shape, and the label says so.
                    val paintBrief = brief?.let { b -> illustrator?.onlyFormat?.let { b.copy(format = it) } ?: b }
                    val painted = if (paintBrief != null && paintBrief.painted && painter != null && illustrator != null) {
                        painted(parts, painter, writer, topic, illustrator, paintBrief)
                    } else {
                        null
                    }
                    if (painted != null) {
                        Outcome.Ok(listOf(labelImage(painted, paintBrief!!.template.id, paintBrief.format, painted = true)))
                    } else {
                        // A painted image that couldn't be made is drawn instead. Only the Carousel layout splits
                        // (docs/requirements/18, SV-5); Auto condenses content too long for one poster.
                        if (layout == ImageLayout.CAROUSEL) {
                            carousel(parts, writer, topic, content, illustrator, brief)?.let { return@async Outcome.Ok(it) }
                        }
                        infographic(parts, writer, topic, idea, content, illustrator, brief, condense = layout == ImageLayout.AUTO)
                    }
                }
            }.awaitAll()
        }
        val files = outcomes.filterIsInstance<Outcome.Ok<List<File>>>().flatMap { it.value }
        return if (files.isNotEmpty()) Outcome.Ok(files) else outcomes.first() as Outcome.Failed
    }

    /**
     * The content as a carousel, made only when the Layout setting is Carousel (docs/requirements/18,
     * SV-5): the carousel writer plans the pages, then each is drawn and labelled in order. The
     * pages, or null when no carousel writer is set up or fewer than 2 could be made, so a single
     * image is drawn instead.
     */
    private suspend fun carousel(
        parts: Infographics,
        writer: TextAgent,
        topic: String,
        content: String,
        illustrator: ImageAgent?,
        brief: ImageBrief?,
    ): List<File>? {
        val carousels = parts.carousel ?: return null
        return when (val result = carousels.write(writer, topic, content, brief, parts.pipeline::fits)) {
            is ApiResult.Failed -> null
            is ApiResult.Ok -> {
                val id = System.currentTimeMillis()
                parts.pipeline.drawPages(result.value.numbered(), illustrator)
                    .takeIf { it.size >= 2 }
                    ?.mapIndexed { i, file -> labelCarouselPage(file, id, i + 1) }
            }
        }
    }

    /**
     * One infographic: written, drawn and reviewed. One the review scores too low is written again
     * with the review's problems, up to [Infographics.retries] times, and the best-scored is kept.
     * One made from a [brief] is labelled with its template and format; the format is never switched.
     * With [condense], a definition too long for its format at full text size is written again once,
     * shorter (docs/requirements/18, SV-5), and drawn with smaller text if it still doesn't fit.
     */
    private suspend fun infographic(
        parts: Infographics,
        writer: TextAgent,
        topic: String,
        idea: String,
        content: String,
        illustrator: ImageAgent?,
        brief: ImageBrief? = null,
        condense: Boolean = true,
    ): Outcome<List<File>> {
        val reviewer = parts.reviewer
        var feedback = emptyList<String>()
        var best: Pair<File, Int>? = null
        var condensing = condense
        repeat(parts.retries + 1) {
            var spec = when (val result = parts.writer.write(writer, topic, idea, content, feedback, brief)) {
                is ApiResult.Failed -> return best?.let { Outcome.Ok(listOf(it.first)) } ?: Outcome.Failed(result.error, writer.service.displayName)
                is ApiResult.Ok -> result.value
            }
            // Too dense for one readable poster: the writer gets the measured numbers and decides itself
            // what to merge (AC-4); the denser one is drawn smaller when that fails.
            val fit = if (condensing) parts.pipeline.measure(spec) else null
            if (fit != null && !fit.fits) {
                condensing = false
                val shorter = parts.writer.write(writer, topic, idea, content, feedback + condenseFeedback(fit), brief)
                if (shorter is ApiResult.Ok) spec = shorter.value
            }
            val file = parts.pipeline.draw(spec, illustrator)?.let { drawn -> brief?.let { labelImage(drawn, it.template.id, spec.format.image) } ?: drawn }
            if (file == null) {
                feedback = listOf("the image couldn't be drawn or came out blank; use simpler sections")
                return@repeat
            }
            val review = reviewer?.review(writer, topic, spec, file, brief?.template) ?: return Outcome.Ok(listOf(file))
            if (reviewer.passes(review)) return Outcome.Ok(listOf(file))
            if (best == null || review.score > best!!.second) best = file to review.score
            feedback = review.problems.ifEmpty { listOf("it scored ${review.score}/10; make it clearer and closer to the topic") }
        }
        return best?.let { Outcome.Ok(listOf(it.first)) }
            ?: Outcome.Failed(ApiError.BadResponse("the infographic couldn't be drawn"), INFOGRAPHIC_RENDERER)
    }

    /**
     * One AI-painted image (docs/design/image-templates.md §6): painted from the filled template,
     * checked for blank or tiny pixels, upscaled and reviewed. One the review scores too low is
     * painted again with the review's problems, up to [Infographics.retries] times, and the
     * best-scored is kept. Null when no image passed the pixel checks.
     *
     * In Auto mode (docs/requirements/18, SV-6) only a painted image the review passes is kept, so
     * one with misspelt, missing or invented text is drawn by the app instead. Without a review the
     * text can't be checked, so nothing is painted and the image is drawn.
     */
    private suspend fun painted(
        parts: Infographics,
        painter: ImagePainter,
        writer: TextAgent,
        topic: String,
        illustrator: ImageAgent,
        brief: ImageBrief,
    ): File? {
        val reviewer = parts.reviewer
        val strict = brief.style.mode == ImageMode.AUTO
        if (strict && reviewer == null) return null
        var feedback = emptyList<String>()
        var best: Pair<File, Int>? = null
        val filled = if (brief.content.isBlank()) brief.copy(content = topic) else brief
        repeat(parts.retries + 1) {
            val prompt = painter.prompt(writer, illustrator, filled, feedback)
            val image = when (val result = illustrator.generate(prompt, 1, brief.format)) {
                is ApiResult.Failed -> return best?.first.takeUnless { strict }
                is ApiResult.Ok -> result.value.firstOrNull()
            }?.takeIf { file -> inspector?.let { imageProblem(it.facts(file), MIN_PAINTED_SIDE) == null } ?: true }
            if (image == null) {
                feedback = listOf("the image came out blank or too small")
                return@repeat
            }
            val file = upscaler?.upscale(image) ?: image
            val review = reviewer?.reviewPainted(writer, topic, file, brief.template) ?: return file.takeUnless { strict }
            if (reviewer.passes(review)) return file
            if (best == null || review.score > best!!.second) best = file to review.score
            feedback = review.problems.ifEmpty { listOf("it scored ${review.score}/10; make it clearer and closer to the topic") }
        }
        return best?.first.takeUnless { strict }
    }

    /**
     * One brief per image: the template the user picked, or the chooser's choice for the content
     * (the topic when there is none), planned over the [count] images by [planImages]. The
     * style's mode decides which are AI-painted ([paints]); its series switch wraps every
     * template in the Series template.
     */
    private suspend fun briefs(templates: Templates, writer: TextAgent, topic: String, content: String, request: ImageRequest): List<ImageBrief> {
        val picked = request.template?.let { id -> templates.all.firstOrNull { it.id == id } }
        val choice = picked?.let { TemplateChoice(it) } ?: templates.chooser.choose(writer, content.ifBlank { topic })
        val style = templates.style()
        val universal = templates.all.byId(UNIVERSAL_TEMPLATE)
        val series = templates.all.firstOrNull { it.id == SERIES_TEMPLATE }?.takeIf { style.series && choice.primary.id != SERIES_TEMPLATE }
        val format = request.format ?: style.format
        return planImages(choice, count, picked != null, universal, format)
            .mapIndexed { i, plan -> ImageBrief(plan.template, content, plan.format, style, paints(style.mode, i), series, fixedFormat = format != null) }
    }

    private fun imagePrompt(prompt: String) = styleTemplate
        .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, prompt)))
        .replace("{{prompt}}", prompt)
}

/**
 * What the writer is told when its definition is too dense for one readable poster (AC-4): the
 * measured numbers, not a fixed card count, so the AI decides what to merge. Nothing may be left out.
 */
internal fun condenseFeedback(fit: PosterFit): String =
    "it is too dense for one readable poster: its ${fit.cards} cards need ${fit.neededHeight} units of height and the " +
        "format holds ${fit.availableHeight}, so text would shrink below ${fit.feedTextPx.roundToInt()} px at LinkedIn's feed width; " +
        "keep every point but merge related cards and shorten items (about 6 words each) so it fits"

/** The request for [prompt] with the template and format last picked on the image screens. */
fun pickedImageRequest(context: Context, prompt: String): ImageRequest {
    val preferences = AgentPreferences(context)
    return ImageRequest(prompt, preferences.imageTemplate, preferences.imageFormat)
}

/**
 * The image generator the screens use: the default text agent writes the definitions, the
 * selected image agent paints the illustrations, and the old style and example assets are the fallback.
 */
fun appGenerateImages(context: Context): GenerateImagesUseCase {
    val cache = MediaCache(context, MediaPurpose.IMAGE)
    val examples = parseImageExamples(loadPromptAsset(context, "image/image_examples"))
    return GenerateImagesUseCase(
        styleTemplate = loadPromptAsset(context, "image/image"),
        agent = appImageAgentSelector(context, cache)::imageAgent,
        count = IMAGES_PER_REQUEST,
        examples = examples,
        infographics = appInfographics(context, cache, examples),
        inspector = AndroidImageInspector,
        upscaler = AndroidImageUpscaler,
        sizeGuard = AndroidSizeGuard,
    )
}

/** The ratios an image may have: 2:3, 1:1 and 3:2. */
private val IMAGE_RATIOS = ImageSize.entries.map { it.ratio }

/**
 * The pipeline's real parts: the default text agent, the no-limit content and infographic
 * prompts, Paint-measured layout, 2x composing, and the checks set in app.yml (`image_checks`).
 */
internal fun appInfographics(context: Context, cache: MediaCache, examples: List<ImageExample>): Infographics {
    val checks = AppConfig.imageChecks(context)
    return Infographics(
        textAgent = appAgentSelector(context, MediaPurpose.IMAGE)::textAgent,
        writer = InfographicWriter(loadPromptAsset(context, "image/infographic"), examples),
        pipeline = InfographicPipeline(
            renderer = InfographicRenderer(PaintTextMeasurer),
            illustrationTemplate = loadPromptAsset(context, "image/illustration"),
            composer = AndroidInfographicComposer(cache),
            inspector = AndroidImageInspector,
        ),
        content = ImageContentWriter(loadPromptAsset(context, "image/image_content")),
        reviewer = if (checks.visionReview) ImageReviewer(loadPromptAsset(context, "image/image_review"), ::reviewJpeg, checks.minScore) else null,
        retries = if (checks.visionReview) checks.retries else 0,
        templates = appTemplates(context),
        carousel = CarouselWriter(loadPromptAsset(context, "image/carousel"), examples),
    )
}

/** The image templates from `prompts/image/image_templates.md`, their chooser, the Image Style settings and the painter. */
internal fun appTemplates(context: Context): Templates {
    val all = parseImageTemplates(loadPromptAsset(context, "image/image_templates"))
    return Templates(
        all,
        TemplateChooser(loadPromptAsset(context, "image/choose_image_template"), all),
        ImageStyleStore(context)::load,
        ImagePainter(loadPromptAsset(context, "image/condense_image_prompt")),
    )
}


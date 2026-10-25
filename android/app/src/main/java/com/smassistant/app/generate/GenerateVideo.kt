package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.GeminiImageAgent
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.media.Media3SlideshowMaker
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.SlideshowMaker
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.loadPromptAsset
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** How many slides a Generate Video submit makes. */
const val SLIDES_PER_VIDEO = 4

/** Fewer slides than this isn't worth a video, so the submit fails instead. */
const val MIN_SLIDES = 2

/** Name used in video errors, e.g. "Unexpected response from the video maker: …". */
const val VIDEO_MAKER = "the video maker"

data class GeneratedVideo(val file: File, val slides: List<File>)

/**
 * Generate Video (docs/tasks VI-I1, Q-08 = slideshow): asks Gemini for [slides] diagram
 * slides that explain the prompt step by step (`assets/prompts/video.md`), then encodes
 * them, in order, into an MP4 with [maker]. Slides that fail are left out.
 */
class GenerateVideoUseCase(
    private val slideTemplate: String,
    private val agent: () -> ImageAgent?,
    private val maker: SlideshowMaker,
    private val newVideoFile: () -> File,
    private val slides: Int = SLIDES_PER_VIDEO,
) {
    suspend operator fun invoke(prompt: String): Outcome<GeneratedVideo> {
        val selected = agent() ?: return Outcome.NoAgent
        val results = coroutineScope {
            (1..slides).map { slide -> async { selected.generate(slidePrompt(prompt.trim(), slide), 1) } }.awaitAll()
        }
        val images = results.filterIsInstance<ApiResult.Ok<List<File>>>().flatMap { it.value }
        if (images.size < MIN_SLIDES) {
            val error = results.filterIsInstance<ApiResult.Failed>().firstOrNull()?.error
                ?: ApiError.BadResponse("only ${images.size} slide came back")
            return Outcome.Failed(error, ApiService.GEMINI.displayName)
        }
        return maker.make(images, newVideoFile()).fold(
            onSuccess = { Outcome.Ok(GeneratedVideo(it, images)) },
            onFailure = { Outcome.Failed(ApiError.BadResponse(it.message ?: "couldn't encode the video"), VIDEO_MAKER) },
        )
    }

    private fun slidePrompt(prompt: String, slide: Int) = slideTemplate
        .replace("{{prompt}}", prompt)
        .replace("{{slide}}", slide.toString())
        .replace("{{slides}}", slides.toString())
}

/** The video generator the screens use: saved Gemini settings, the slide asset, on-device encoding. */
fun appGenerateVideo(context: Context): GenerateVideoUseCase {
    val settings = ApiSettingsStore(context)
    val cache = MediaCache(context)
    val agent = GeminiImageAgent({ settings.load(ApiService.GEMINI) }, cache)
    return GenerateVideoUseCase(
        slideTemplate = loadPromptAsset(context, "video"),
        agent = { agent.takeIf { settings.load(ApiService.GEMINI).let { it.key.isNotBlank() && it.imageModel.isNotBlank() } } },
        maker = Media3SlideshowMaker(context.applicationContext),
        newVideoFile = { cache.newFile("mp4") },
    )
}

package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.GeminiImageAgent
import com.smassistant.app.agents.IMAGES_PER_REQUEST
import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.media.MediaCache
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.net.ApiResult
import java.io.File

/**
 * Generate Image (docs/tasks IM-I1): wraps the user's prompt in the infographic style from
 * `assets/prompts/image.md`, with the few-shot [examples] closest to the topic, and asks
 * Gemini for [count] images.
 */
class GenerateImagesUseCase(
    private val styleTemplate: String,
    private val agent: () -> ImageAgent?,
    private val count: Int,
    private val examples: List<ImageExample> = emptyList(),
) {
    suspend operator fun invoke(prompt: String): Outcome<List<File>> {
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(imagePrompt(prompt.trim()), count)) {
            is ApiResult.Ok -> Outcome.Ok(result.value)
            is ApiResult.Failed -> Outcome.Failed(result.error, ApiService.GEMINI.displayName)
        }
    }

    private fun imagePrompt(prompt: String) = styleTemplate
        .replace("{{examples}}", formatImageExamples(pickImageExamples(examples, prompt)))
        .replace("{{prompt}}", prompt)
}

/** The image generator the screens use: saved Gemini settings, the style and example assets, the media cache. */
fun appGenerateImages(context: Context): GenerateImagesUseCase {
    val settings = ApiSettingsStore(context)
    val agent = GeminiImageAgent({ settings.load(ApiService.GEMINI) }, MediaCache(context))
    return GenerateImagesUseCase(
        styleTemplate = loadPromptAsset(context, "image"),
        agent = { agent.takeIf { settings.load(ApiService.GEMINI).let { it.key.isNotBlank() && it.imageModel.isNotBlank() } } },
        count = IMAGES_PER_REQUEST,
        examples = parseImageExamples(loadPromptAsset(context, "image_examples")),
    )
}

package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiResult
import java.io.File

/**
 * Generate Image (docs/tasks IM-I1): wraps the user's prompt in the diagram style from
 * `assets/prompts/image.md` and asks Gemini for [count] images.
 */
class GenerateImagesUseCase(
    private val styleTemplate: String,
    private val agent: () -> ImageAgent?,
    private val count: Int,
) {
    suspend operator fun invoke(prompt: String): Outcome<List<File>> {
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(styleTemplate.replace("{{prompt}}", prompt.trim()), count)) {
            is ApiResult.Ok -> Outcome.Ok(result.value)
            is ApiResult.Failed -> Outcome.Failed(result.error, ApiService.GEMINI.displayName)
        }
    }
}

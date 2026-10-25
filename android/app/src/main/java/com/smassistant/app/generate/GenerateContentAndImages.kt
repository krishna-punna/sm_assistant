package com.smassistant.app.generate

import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.userMessage
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A Content & Image result: each half is either there or has an error with its own Retry. */
data class ContentAndImages(
    val markdown: String? = null,
    val textError: String? = null,
    val images: List<File>? = null,
    val imageError: String? = null,
    val retryingText: Boolean = false,
    val retryingImages: Boolean = false,
)

/**
 * Generate Content & Image (docs/tasks CI-I1): the technical post and the images from the
 * same prompt, in parallel. One half failing still shows the other.
 */
class GenerateContentAndImagesUseCase(
    private val text: suspend (String) -> Outcome<String>,
    private val images: suspend (String) -> Outcome<List<File>>,
) {
    suspend operator fun invoke(prompt: String): Outcome<ContentAndImages> = coroutineScope {
        val textOutcome = async { text(prompt) }
        val imageOutcome = async { images(prompt) }
        combine(textOutcome.await(), imageOutcome.await())
    }

    suspend fun retryText(prompt: String, current: ContentAndImages): ContentAndImages = when (val outcome = text(prompt)) {
        is Outcome.Ok -> current.copy(markdown = outcome.value, textError = null, retryingText = false)
        else -> current.copy(textError = message(outcome, TEXT_NOT_SET_UP), retryingText = false)
    }

    suspend fun retryImages(prompt: String, current: ContentAndImages): ContentAndImages = when (val outcome = images(prompt)) {
        is Outcome.Ok -> current.copy(images = outcome.value, imageError = null, retryingImages = false)
        else -> current.copy(imageError = message(outcome, IMAGES_NOT_SET_UP), retryingImages = false)
    }

    private fun combine(text: Outcome<String>, images: Outcome<List<File>>): Outcome<ContentAndImages> = when {
        text is Outcome.NoAgent && images is Outcome.NoAgent -> Outcome.NoAgent
        text !is Outcome.Ok && images !is Outcome.Ok -> (text as? Outcome.Failed) ?: (images as Outcome.Failed)
        else -> Outcome.Ok(
            ContentAndImages(
                markdown = (text as? Outcome.Ok)?.value,
                textError = message(text, TEXT_NOT_SET_UP),
                images = (images as? Outcome.Ok)?.value,
                imageError = message(images, IMAGES_NOT_SET_UP),
            ),
        )
    }

    private fun message(outcome: Outcome<*>, notSetUp: String): String? = when (outcome) {
        is Outcome.Ok -> null
        Outcome.NoAgent -> notSetUp
        is Outcome.Failed -> outcome.error.userMessage(outcome.serviceName)
    }

    companion object {
        const val TEXT_NOT_SET_UP = "No text agent is set up. Add a Claude, Gemini or Groq key in AI Agent settings."
        const val IMAGES_NOT_SET_UP = "Images are made with Gemini. Add a Gemini API key in AI Agent settings."
    }
}

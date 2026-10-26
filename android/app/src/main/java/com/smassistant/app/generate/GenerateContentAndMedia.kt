package com.smassistant.app.generate

import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.userMessage
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A Content & Image or Content & Video result: each half is either there or has an error with its own Retry. */
data class ContentAndMedia<M>(
    val markdown: String? = null,
    val textError: String? = null,
    val media: M? = null,
    val mediaError: String? = null,
    val retryingText: Boolean = false,
    val retryingMedia: Boolean = false,
)

typealias ContentAndImages = ContentAndMedia<List<File>>
typealias ContentAndVideo = ContentAndMedia<GeneratedVideo>

/**
 * Generate Content & Image / Content & Video (docs/tasks CI-I1, CV-I1): the technical post and
 * the images or video from the same prompt, in parallel. One half failing still shows the other.
 * [mediaNotSetUp] is the message when the media half has no agent.
 */
class GenerateContentAndMediaUseCase<M>(
    private val text: suspend (String) -> Outcome<String>,
    private val media: suspend (String) -> Outcome<M>,
    private val mediaNotSetUp: String,
) {
    suspend operator fun invoke(prompt: String): Outcome<ContentAndMedia<M>> = coroutineScope {
        val textOutcome = async { text(prompt) }
        val mediaOutcome = async { media(prompt) }
        combine(textOutcome.await(), mediaOutcome.await())
    }

    suspend fun retryText(prompt: String, current: ContentAndMedia<M>): ContentAndMedia<M> = when (val outcome = text(prompt)) {
        is Outcome.Ok -> current.copy(markdown = outcome.value, textError = null, retryingText = false)
        else -> current.copy(textError = message(outcome, TEXT_NOT_SET_UP), retryingText = false)
    }

    suspend fun retryMedia(prompt: String, current: ContentAndMedia<M>): ContentAndMedia<M> = when (val outcome = media(prompt)) {
        is Outcome.Ok -> current.copy(media = outcome.value, mediaError = null, retryingMedia = false)
        else -> current.copy(mediaError = message(outcome, mediaNotSetUp), retryingMedia = false)
    }

    private fun combine(text: Outcome<String>, media: Outcome<M>): Outcome<ContentAndMedia<M>> = when {
        text is Outcome.NoAgent && media is Outcome.NoAgent -> Outcome.NoAgent
        text !is Outcome.Ok && media !is Outcome.Ok -> (text as? Outcome.Failed) ?: (media as Outcome.Failed)
        else -> Outcome.Ok(
            ContentAndMedia(
                markdown = (text as? Outcome.Ok)?.value,
                textError = message(text, TEXT_NOT_SET_UP),
                media = (media as? Outcome.Ok)?.value,
                mediaError = message(media, mediaNotSetUp),
            ),
        )
    }

    private fun message(outcome: Outcome<*>, notSetUp: String): String? = when (outcome) {
        is Outcome.Ok -> null
        Outcome.NoAgent -> notSetUp
        is Outcome.Failed -> outcome.error.userMessage(outcome.serviceName)
    }

    companion object {
        const val TEXT_NOT_SET_UP = "No text agent is set up. Add an API key for one of the agents in AI Agent settings."
        const val IMAGES_NOT_SET_UP = "No AI agent is set up for images. Add an API key for one of the agents in AI Agent settings."
        const val VIDEO_NOT_SET_UP = "Videos are made from image slides. Add a Gemini, Claude, Hugging Face, Cloudflare or Pollinations.AI key in AI Agent settings."
    }
}

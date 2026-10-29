package com.smassistant.app.scheduler

import android.content.Context
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.generate.GenerateContentAndMediaUseCase.Companion.IMAGES_NOT_SET_UP
import com.smassistant.app.generate.GenerateContentAndMediaUseCase.Companion.TEXT_NOT_SET_UP
import com.smassistant.app.generate.GenerateContentAndMediaUseCase.Companion.VIDEO_NOT_SET_UP
import com.smassistant.app.generate.GeneratedVideo
import com.smassistant.app.generate.appGenerateImages
import com.smassistant.app.generate.appGenerateTechnicalContent
import com.smassistant.app.generate.appGenerateVideo
import com.smassistant.app.linkedin.PostToLinkedInUseCase
import com.smassistant.app.linkedin.SignInExpiry
import com.smassistant.app.linkedin.appSignInExpiry
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.linkedin.linkedInErrorMessage
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import com.smassistant.app.prompt.PreparePromptsUseCase
import com.smassistant.app.prompt.PromptSet
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.prompt.loadPromptAsset
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * How a run went. [title] is the prepared prompt's title once there was one; [retry] is true when
 * it failed on something that may pass in a few minutes (network, rate limit), so the worker
 * tries again before recording it (SC-NFR3).
 */
data class RunOutcome(
    val record: RunRecord,
    val title: String? = null,
    val retry: Boolean = false,
    /** For a "review before posting" scheduler: the post made and held for approval. */
    val held: PendingPost? = null,
    /** True when the run did nothing (an earlier post still waits for review), so the topic doesn't move on. */
    val skipped: Boolean = false,
)

/**
 * One scheduled run (docs/requirements/13-scheduled-posts.md, SC-1…3b): the scheduler's next topic
 * → Prepare Prompt for the post type → Generate Technical Content and/or the images or video →
 * the length check with default hashtags → Post in LinkedIn. Nothing is posted unless every part
 * the post type needs came back and the text fits.
 */
class AutoPostRun(
    private val preparePrompt: suspend (String, PromptType) -> Outcome<PromptSet>,
    private val generateText: suspend (String) -> Outcome<String>,
    private val generateImages: suspend (String) -> Outcome<List<File>>,
    private val generateVideo: suspend (String) -> Outcome<GeneratedVideo>,
    private val linkedIn: PostToLinkedInUseCase,
    private val now: () -> Long = System::currentTimeMillis,
    /** The LinkedIn sign-in's expiry; an expired one fails the run before anything is generated. */
    private val signInExpiry: () -> SignInExpiry? = { null },
) {
    suspend operator fun invoke(scheduler: AutoPostScheduler): RunOutcome {
        val topic = scheduler.currentTopic() ?: return failed("Choose at least one technical content type.")
        scheduler.pending?.let { waiting ->
            return RunOutcome(
                RunRecord(now(), ok = false, message = "Skipped: “${waiting.title}” is still waiting for your review.", awaitingReview = true),
                skipped = true,
            )
        }
        if (!linkedIn.isConfigured()) return failed(linkedInErrorMessage(ApiError.NotConfigured))
        val expiry = signInExpiry()
        if (expiry is SignInExpiry.Expired) return failed(expiry.message().orEmpty())

        val type = scheduler.postType.promptType
        val prompts = when (val outcome = preparePrompt(topicRequest(topic, scheduler.recentTitles), type)) {
            is Outcome.Ok -> outcome.value
            else -> return failed(outcome, "prepare the prompt", TEXT_NOT_SET_UP)
        }
        // The post prompt for the text, the "no limit" content prompt for the images or video
        // (docs/requirements/14); each falls back to the other if the agent left one out.
        val postPrompt = prompts.post ?: prompts.content
        val contentPrompt = prompts.content ?: prompts.post
        if (postPrompt == null || contentPrompt == null) return failed("The agent didn't write a prompt, so nothing was posted.")
        val title = postPrompt.title

        val (text, media) = coroutineScope {
            // Image and Video posts have no written post: the title is the caption, before the hashtags.
            val writing = async { if (type.post) generateText(postPrompt.prompt) else Outcome.Ok(postPrompt.title) }
            val making = async { media(type.media, contentPrompt.prompt) }
            writing.await() to making.await()
        }
        val markdown = when (text) {
            is Outcome.Ok -> text.value
            else -> return failed(text, "write the post", TEXT_NOT_SET_UP, title)
        }
        val files = when (media) {
            is Outcome.Ok -> media.value
            else -> return if (type.media == MediaPurpose.VIDEO) {
                failed(media, "make the video", VIDEO_NOT_SET_UP, title)
            } else {
                failed(media, "make the images", IMAGES_NOT_SET_UP, title)
            }
        }

        val post = linkedIn.prepare(markdown)
        if (post.text.isBlank()) return failed("The agent wrote an empty post, so nothing was posted.", title)
        if (!post.fitsLimit) {
            return failed("The post was ${post.overLimitBy} characters over LinkedIn's ${linkedIn.limit}-character limit with the hashtags, so it wasn't posted.", title)
        }

        if (scheduler.reviewFirst) {
            return RunOutcome(
                RunRecord(now(), ok = true, message = "Ready for your review: “$title”", awaitingReview = true),
                title,
                held = PendingPost(title, post.text, files.map { it.path }, now()),
            )
        }

        return when (val result = linkedIn.post(post.text, files)) {
            is ApiResult.Ok -> RunOutcome(RunRecord(now(), ok = true, message = "Posted “$title”", postUrl = result.value.url), title)
            is ApiResult.Failed -> RunOutcome(
                RunRecord(now(), ok = false, message = linkedInErrorMessage(result.error)),
                title,
                retry = isTransient(result.error),
            )
        }
    }

    private suspend fun media(purpose: MediaPurpose?, prompt: String): Outcome<List<File>> = when (purpose) {
        null -> Outcome.Ok(emptyList())
        MediaPurpose.IMAGE -> generateImages(prompt)
        MediaPurpose.VIDEO -> when (val video = generateVideo(prompt)) {
            is Outcome.Ok -> Outcome.Ok(listOf(video.value.file))
            is Outcome.Failed -> video
            Outcome.NoAgent -> Outcome.NoAgent
        }
    }

    private fun failed(message: String, title: String? = null) = RunOutcome(RunRecord(now(), ok = false, message = message), title)

    /** A step that didn't come back: the agent's error, or [notSetUp] when there's no agent for it. */
    private fun failed(outcome: Outcome<*>, step: String, notSetUp: String, title: String? = null): RunOutcome = when (outcome) {
        is Outcome.Failed -> RunOutcome(
            RunRecord(now(), ok = false, message = "Couldn't $step. " + outcome.error.userMessage(outcome.serviceName)),
            title,
            retry = isTransient(outcome.error),
        )
        else -> failed(notSetUp, title)
    }

    companion object {
        /**
         * The topic given to Prepare Prompt (SC-1): the content type, asking for one specific angle
         * and steering away from the scheduler's recent posts.
         */
        fun topicRequest(topic: String, recentTitles: List<String>): String {
            val avoid = recentTitles.take(AutoPostScheduler.MAX_RECENT_TITLES)
            return buildString {
                append("$topic: choose one specific, practical subject within $topic for a technical LinkedIn post, ")
                append("such as a feature, pattern, pitfall or design problem an experienced developer would want explained.")
                if (avoid.isNotEmpty()) {
                    append(" Pick something different from these recent posts: ")
                    append(avoid.joinToString("; "))
                    append(".")
                }
            }
        }

        /** Errors worth a retry in a few minutes. */
        fun isTransient(error: ApiError) = error is ApiError.Unreachable || error == ApiError.RateLimited ||
            (error is ApiError.Server && error.httpCode >= 500)
    }
}

/** The run with the app's saved agents, prompts, hashtags and LinkedIn sign-in, as the screens use them. */
fun appAutoPostRun(context: Context): AutoPostRun {
    val textAgent = appAgentSelector(context)::textAgent
    val postPrompt = loadPromptAsset(context, "prepare_prompt")
    val contentPrompt = loadPromptAsset(context, "prepare_content_prompt")
    return AutoPostRun(
        preparePrompt = { topic, type -> PreparePromptsUseCase(postPrompt, contentPrompt, textAgent) { type }(topic) },
        generateText = appGenerateTechnicalContent(context)::invoke,
        generateImages = appGenerateImages(context)::invoke,
        generateVideo = appGenerateVideo(context)::invoke,
        linkedIn = appPostToLinkedIn(context),
        signInExpiry = appSignInExpiry(context),
    )
}

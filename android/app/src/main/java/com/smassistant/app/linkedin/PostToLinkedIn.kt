package com.smassistant.app.linkedin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.AppConfig
import com.smassistant.app.generate.PostCut
import com.smassistant.app.generate.SmartTrimUseCase
import com.smassistant.app.hashtags.HashtagSettingsStore
import com.smassistant.app.hashtags.Hashtags
import com.smassistant.app.hashtags.LINKEDIN_POST_LIMIT
import com.smassistant.app.hashtags.PostText
import com.smassistant.app.hashtags.postLength
import com.smassistant.app.markdown.MarkdownToLinkedIn
import com.smassistant.app.media.isVideo
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The single entry point for every Post in LinkedIn button (docs/tasks LP-1-I2, HT-I5):
 * Markdown → plain text → default hashtags, then publish. Prepare Prompt never uses it (HT-16).
 */
class PostToLinkedInUseCase(
    private val tags: () -> List<String>,
    private val tagsEnabled: () -> Boolean,
    private val configured: () -> Boolean,
    private val publish: suspend (String, List<File>) -> ApiResult<LinkedInPost>,
    val limit: Int = LINKEDIN_POST_LIMIT,
) {
    /** The text that would be posted for [markdown], with the default hashtags when they're on. */
    fun prepare(markdown: String): PostText =
        Hashtags.appendTo(MarkdownToLinkedIn.convert(markdown), tags(), tagsEnabled(), limit)

    fun isConfigured() = configured()

    /** Posts [text] with [images] (or one video) attached. A post needs text or at least one image (HT-12: media-only text is the hashtags). */
    suspend fun post(text: String, images: List<File> = emptyList()): ApiResult<LinkedInPost> = when {
        !isConfigured() -> ApiResult.Failed(ApiError.NotConfigured)
        text.isBlank() && images.isEmpty() -> ApiResult.Failed(ApiError.BadResponse("nothing to post"))
        postLength(text) > limit -> ApiResult.Failed(ApiError.BadResponse("post text is over $limit characters"))
        images.size > MAX_IMAGES -> ApiResult.Failed(ApiError.BadResponse("LinkedIn allows up to $MAX_IMAGES images"))
        images.any(::isVideo) && images.size > 1 -> ApiResult.Failed(ApiError.BadResponse("LinkedIn posts take one video and no images"))
        else -> publish(text, images)
    }

    companion object {
        /** LinkedIn's multi-image limit. */
        const val MAX_IMAGES = 20
    }
}

/** The posting flow behind one screen's Post in LinkedIn buttons. */
sealed interface PostState {
    data object Idle : PostState

    /**
     * Confirmation dialog with the final text and attached [images] (LP-6, HT-15).
     * [addedTags] are the defaults that were appended; [limit] is LinkedIn's character limit.
     * [trimming] is true while the AI agent condenses the text (PL-10); [trimMessage] says why it couldn't.
     */
    data class Confirm(
        val text: String,
        val addedTags: List<String>,
        val images: List<File> = emptyList(),
        val limit: Int = LINKEDIN_POST_LIMIT,
        val trimming: Boolean = false,
        val trimMessage: String? = null,
    ) : PostState {
        val length get() = postLength(text)
        val overLimitBy get() = (length - limit).coerceAtLeast(0)
        val canPost get() = (text.isNotBlank() || images.isNotEmpty()) && overLimitBy == 0 && !trimming
    }

    data object Posting : PostState
    data class Posted(val post: LinkedInPost) : PostState

    /** [openSettings] offers a way to the LinkedIn settings screen (LP-3). */
    data class Failed(val message: String, val openSettings: Boolean) : PostState
}

/** [smartTrim] condenses the text with the selected text agent for Trim post (PL-10); null hides that option. */
class PostViewModel(
    private val useCase: PostToLinkedInUseCase,
    private val smartTrim: SmartTrimUseCase? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<PostState>(PostState.Idle)
    val state: StateFlow<PostState> = _state.asStateFlow()

    /** Post in LinkedIn tapped under a result: its text (Markdown, may be empty) and selected images. */
    fun start(markdown: String, images: List<File> = emptyList()) {
        if (_state.value == PostState.Posting) return
        _state.value = if (useCase.isConfigured()) {
            val prepared = useCase.prepare(markdown)
            PostState.Confirm(prepared.text, prepared.addedTags, images, useCase.limit)
        } else {
            failure(ApiError.NotConfigured)
        }
    }

    /** The text [markdown] would post as, with the default hashtags, for the length note under a result (PL-7). */
    fun measure(markdown: String): PostText = useCase.prepare(markdown)

    val limit get() = useCase.limit

    /** Edits in the confirmation dialog (interim for Q-12), e.g. to get under the limit. */
    fun onTextChange(text: String) {
        val confirm = _state.value as? PostState.Confirm ?: return
        if (confirm.trimming) return
        _state.value = confirm.copy(text = text, trimMessage = null)
    }

    val canSmartTrim get() = smartTrim != null

    /** Cut to fit (PL-12): drops the least important lines, then words, until the text fits, keeping the hashtags. */
    fun cutToFit() {
        val confirm = _state.value as? PostState.Confirm ?: return
        if (confirm.trimming) return
        _state.value = confirm.copy(text = PostCut.toFit(confirm.text) { postLength(it) <= confirm.limit }, trimMessage = null)
    }

    /**
     * Trim with AI (PL-10, PL-11): the agent condenses the body, keeping the key points and the
     * hashtags, to fit the limit, or to about a fifth shorter when it already fits.
     */
    fun trimWithAi() {
        val confirm = _state.value as? PostState.Confirm ?: return
        val trim = smartTrim ?: return
        if (confirm.trimming || confirm.text.isBlank()) return
        // The last line of hashtags (defaults merged with any the agent suggested, HT-22) stays as it is.
        val (rest, trailing) = Hashtags.splitTrailingTags(confirm.text)
        val tail = Hashtags.format(trailing).takeIf { it.isNotEmpty() }
        val body = if (tail != null) rest.trimEnd() else confirm.text
        if (body.isBlank()) return
        fun join(text: String) = if (tail == null) text else text.trimEnd() + "\n\n" + tail
        val budget = confirm.limit - (tail?.let { postLength(it) + 2 } ?: 0)
        val maxChars = if (confirm.overLimitBy > 0) budget else minOf(budget, postLength(body) * 4 / 5)
        _state.value = confirm.copy(trimming = true, trimMessage = null)
        viewModelScope.launch {
            val outcome = trim(body, maxChars) { postLength(join(it)) <= confirm.limit }
            // Dismissed, or a new dialog opened, while the agent was working.
            val current = (_state.value as? PostState.Confirm)?.takeIf { it.trimming } ?: return@launch
            _state.value = when (outcome) {
                is Outcome.Ok -> current.copy(text = join(outcome.value), trimming = false)
                is Outcome.Failed -> current.copy(trimming = false, trimMessage = outcome.error.userMessage(outcome.serviceName))
                Outcome.NoAgent -> current.copy(trimming = false, trimMessage = "Set up a text agent to trim with AI, or use Cut to fit.")
            }
        }
    }

    fun confirm() {
        val confirm = _state.value as? PostState.Confirm ?: return
        if (!confirm.canPost) return
        _state.value = PostState.Posting
        viewModelScope.launch {
            _state.value = when (val result = useCase.post(confirm.text, confirm.images)) {
                is ApiResult.Ok -> PostState.Posted(result.value)
                is ApiResult.Failed -> failure(result.error)
            }
        }
    }

    fun dismiss() {
        if (_state.value != PostState.Posting) _state.value = PostState.Idle
    }

    private fun failure(error: ApiError) = PostState.Failed(linkedInErrorMessage(error), openSettings = needsLinkedInSettings(error))
}

/** What to tell the user when posting failed with [error]; also used for scheduled posts (SC-7). */
fun linkedInErrorMessage(error: ApiError): String = when (error) {
    ApiError.NotConfigured -> "Set up LinkedIn first: sign in or paste an access token in LinkedIn settings."
    ApiError.InvalidKey -> "LinkedIn token expired or invalid. Sign in again in LinkedIn settings."
    ApiError.NoAccess -> "This LinkedIn token can't post. It needs the w_member_social permission."
    else -> error.userMessage(ApiService.LINKEDIN.displayName)
}

/** True when the fix for [error] is on the LinkedIn settings screen (LP-3). */
fun needsLinkedInSettings(error: ApiError) =
    error == ApiError.NotConfigured || error == ApiError.InvalidKey || error == ApiError.NoAccess

/** The use case the screens use: saved hashtags, saved LinkedIn settings, the real client. */
fun appPostToLinkedIn(context: Context): PostToLinkedInUseCase {
    val hashtags = HashtagSettingsStore(context)
    val settings = ApiSettingsStore(context)
    val client = LinkedInClient({ settings.load(ApiService.LINKEDIN) })
    return PostToLinkedInUseCase(
        tags = { hashtags.tags },
        tagsEnabled = { hashtags.enabled },
        configured = { settings.load(ApiService.LINKEDIN).let { it.api.isNotBlank() && it.key.isNotBlank() } },
        publish = client::createPost,
        limit = AppConfig.postRules(context).maxCharacters,
    )
}

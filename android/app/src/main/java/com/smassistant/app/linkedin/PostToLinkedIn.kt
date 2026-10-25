package com.smassistant.app.linkedin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.hashtags.HashtagSettingsStore
import com.smassistant.app.hashtags.Hashtags
import com.smassistant.app.hashtags.LINKEDIN_POST_LIMIT
import com.smassistant.app.hashtags.PostText
import com.smassistant.app.markdown.MarkdownToLinkedIn
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single entry point for every Post in LinkedIn button (docs/tasks LP-1-I2, HT-I5):
 * Markdown → plain text → default hashtags, then publish. Prepare Prompt never uses it (HT-16).
 */
class PostToLinkedInUseCase(
    private val tags: () -> List<String>,
    private val tagsEnabled: () -> Boolean,
    private val configured: () -> Boolean,
    private val publish: suspend (String) -> ApiResult<LinkedInPost>,
) {
    /** The text that would be posted for [markdown], with the default hashtags when they're on. */
    fun prepare(markdown: String): PostText =
        Hashtags.appendTo(MarkdownToLinkedIn.convert(markdown), tags(), tagsEnabled())

    fun isConfigured() = configured()

    suspend fun post(text: String): ApiResult<LinkedInPost> = when {
        !isConfigured() -> ApiResult.Failed(ApiError.NotConfigured)
        text.isBlank() || text.length > LINKEDIN_POST_LIMIT -> ApiResult.Failed(ApiError.BadResponse("post text must be 1 to $LINKEDIN_POST_LIMIT characters"))
        else -> publish(text)
    }
}

/** The posting flow behind one screen's Post in LinkedIn buttons. */
sealed interface PostState {
    data object Idle : PostState

    /** Confirmation dialog with the final text (LP-6, HT-15). [addedTags] are the defaults that were appended. */
    data class Confirm(val text: String, val addedTags: List<String>) : PostState {
        val overLimitBy get() = (text.length - LINKEDIN_POST_LIMIT).coerceAtLeast(0)
        val canPost get() = text.isNotBlank() && overLimitBy == 0
    }

    data object Posting : PostState
    data class Posted(val post: LinkedInPost) : PostState

    /** [openSettings] offers a way to the LinkedIn settings screen (LP-3). */
    data class Failed(val message: String, val openSettings: Boolean) : PostState
}

class PostViewModel(private val useCase: PostToLinkedInUseCase) : ViewModel() {
    private val _state = MutableStateFlow<PostState>(PostState.Idle)
    val state: StateFlow<PostState> = _state.asStateFlow()

    /** Post in LinkedIn tapped under a result. */
    fun start(markdown: String) {
        if (_state.value == PostState.Posting) return
        _state.value = if (useCase.isConfigured()) {
            val prepared = useCase.prepare(markdown)
            PostState.Confirm(prepared.text, prepared.addedTags)
        } else {
            failure(ApiError.NotConfigured)
        }
    }

    /** Edits in the confirmation dialog (interim for Q-12), e.g. to get under the limit. */
    fun onTextChange(text: String) {
        val confirm = _state.value as? PostState.Confirm ?: return
        _state.value = confirm.copy(text = text)
    }

    fun confirm() {
        val confirm = _state.value as? PostState.Confirm ?: return
        if (!confirm.canPost) return
        _state.value = PostState.Posting
        viewModelScope.launch {
            _state.value = when (val result = useCase.post(confirm.text)) {
                is ApiResult.Ok -> PostState.Posted(result.value)
                is ApiResult.Failed -> failure(result.error)
            }
        }
    }

    fun dismiss() {
        if (_state.value != PostState.Posting) _state.value = PostState.Idle
    }

    private fun failure(error: ApiError) = when (error) {
        ApiError.NotConfigured -> PostState.Failed("Set up LinkedIn first: paste your access token in LinkedIn settings.", openSettings = true)
        ApiError.InvalidKey -> PostState.Failed("LinkedIn token expired or invalid. Paste a new access token in LinkedIn settings.", openSettings = true)
        ApiError.NoAccess -> PostState.Failed("This LinkedIn token can't post. It needs the w_member_social permission.", openSettings = true)
        else -> PostState.Failed(error.userMessage(ApiService.LINKEDIN.displayName), openSettings = false)
    }
}

/** The use case the screens use: saved hashtags, saved LinkedIn settings, the real client. */
fun appPostToLinkedIn(context: Context): PostToLinkedInUseCase {
    val hashtags = HashtagSettingsStore(context)
    val settings = ApiSettingsStore(context)
    val client = LinkedInClient({ settings.load(ApiService.LINKEDIN) })
    return PostToLinkedInUseCase(
        tags = { hashtags.tags },
        tagsEnabled = { hashtags.enabled },
        configured = { settings.load(ApiService.LINKEDIN).let { it.api.isNotBlank() && it.key.isNotBlank() } },
        publish = client::createTextPost,
    )
}

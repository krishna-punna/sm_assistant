package com.smassistant.app.linkedin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.AppConfig
import com.smassistant.app.drafts.Draft
import com.smassistant.app.drafts.DraftSaver
import com.smassistant.app.drafts.DraftStore
import com.smassistant.app.generate.PostCut
import com.smassistant.app.generate.SmartTrimUseCase
import com.smassistant.app.hashtags.HashtagSettingsStore
import com.smassistant.app.hashtags.Hashtags
import com.smassistant.app.hashtags.LINKEDIN_POST_LIMIT
import com.smassistant.app.hashtags.PostText
import com.smassistant.app.hashtags.postLength
import com.smassistant.app.history.PostHistoryStore
import com.smassistant.app.history.PostSource
import com.smassistant.app.history.PublishedPost
import com.smassistant.app.image.isCarousel
import com.smassistant.app.markdown.MarkdownToLinkedIn
import com.smassistant.app.media.AndroidCarouselPdfMaker
import com.smassistant.app.media.CarouselPdfMaker
import com.smassistant.app.media.isVideo
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import java.io.File
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
    private val publish: suspend (String, List<File>) -> ApiResult<LinkedInPost>,
    val limit: Int = LINKEDIN_POST_LIMIT,
    /** Called with each post that went out, for the post history (improvement #7). */
    private val onPublished: (PublishedPost) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    /** Where Save draft in the confirm dialog keeps posts for later (improvement #8); null hides it. */
    val drafts: DraftSaver? = null,
    /** Makes a carousel's PDF, so it is posted as a swipeable document (SC-I6); null posts its pages as images. */
    private val carouselPdf: CarouselPdfMaker? = null,
) {
    /** The text that would be posted for [markdown], with the default hashtags when they're on. */
    fun prepare(markdown: String): PostText =
        Hashtags.appendTo(MarkdownToLinkedIn.convert(markdown), tags(), tagsEnabled(), limit)

    fun isConfigured() = configured()

    /**
     * Posts [text] with [images] (or one video) attached. A post needs text or at least one image (HT-12: media-only text is the hashtags).
     * A published post is added to the history as made by [source], titled [title] (the text's first line when null).
     */
    suspend fun post(
        text: String,
        images: List<File> = emptyList(),
        source: PostSource = PostSource.MANUAL,
        title: String? = null,
    ): ApiResult<LinkedInPost> = when {
        !isConfigured() -> ApiResult.Failed(ApiError.NotConfigured)
        text.isBlank() && images.isEmpty() -> ApiResult.Failed(ApiError.BadResponse("nothing to post"))
        postLength(text) > limit -> ApiResult.Failed(ApiError.BadResponse("post text is over $limit characters"))
        images.size > MAX_IMAGES -> ApiResult.Failed(ApiError.BadResponse("LinkedIn allows up to $MAX_IMAGES images"))
        images.any(::isVideo) && images.size > 1 -> ApiResult.Failed(ApiError.BadResponse("LinkedIn posts take one video and no images"))
        else -> publishMedia(text, images).also { result ->
            if (result is ApiResult.Ok) {
                runCatching { onPublished(PublishedPost.of(text, images, result.value.url, source, now(), title)) }
            }
        }
    }

    /**
     * A carousel's pages go out as one PDF document titled from the post's first line; when the PDF
     * can't be made or LinkedIn won't take the document, as a multi-image post of the pages instead.
     * A sign-in problem isn't retried, since the images would fail the same way.
     */
    private suspend fun publishMedia(text: String, images: List<File>): ApiResult<LinkedInPost> {
        val maker = carouselPdf
        if (maker == null || !isCarousel(images)) return publish(text, images)
        val posted = maker.make(images, documentTitle(text))?.let { publish(text, listOf(it)) }
        return when {
            posted is ApiResult.Ok -> posted
            posted is ApiResult.Failed && needsLinkedInSettings(posted.error) -> posted
            else -> publish(text, images)
        }
    }

    companion object {
        /** LinkedIn's multi-image limit. */
        const val MAX_IMAGES = 20
    }
}

/** A carousel document's title: the post's first line without hashtags, "Carousel" when that leaves nothing. */
fun documentTitle(text: String): String =
    text.lineSequence().map { line -> line.split(' ').filterNot { it.startsWith("#") }.joinToString(" ").trim() }
        .firstOrNull { it.isNotEmpty() }?.take(80)?.trim() ?: "Carousel"

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
        /** Who made the post and its title, for the post history. */
        val source: PostSource = PostSource.MANUAL,
        val title: String? = null,
        /** The draft this post was opened from: Save draft updates it, and posting removes it. */
        val draftId: String? = null,
    ) : PostState {
        val length get() = postLength(text)
        val overLimitBy get() = (length - limit).coerceAtLeast(0)
        val canPost get() = (text.isNotBlank() || images.isNotEmpty()) && overLimitBy == 0 && !trimming
    }

    data object Posting : PostState

    /** Save draft kept the post for later. */
    data object DraftSaved : PostState
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

    /** The result the open post came from (its input's id), so its pipeline's Post stage follows the post (docs/requirements/20, PS-5). */
    var key: Long? = null
        private set

    /** Post in LinkedIn tapped under a result: its text (Markdown, may be empty) and selected images. */
    fun start(markdown: String, images: List<File> = emptyList(), key: Long? = null) {
        if (_state.value == PostState.Posting) return
        this.key = key
        _state.value = if (useCase.isConfigured()) {
            val prepared = useCase.prepare(markdown)
            PostState.Confirm(prepared.text, prepared.addedTags, images, useCase.limit)
        } else {
            failure(ApiError.NotConfigured)
        }
    }

    /**
     * Review a post that's already final (a scheduler's post waiting for approval): the same
     * confirmation, edits and trimming, without converting it or adding hashtags again.
     */
    fun review(text: String, images: List<File> = emptyList(), title: String? = null) {
        if (_state.value == PostState.Posting) return
        key = null
        _state.value = if (useCase.isConfigured()) {
            PostState.Confirm(text, emptyList(), images, useCase.limit, source = PostSource.SCHEDULED, title = title)
        } else {
            failure(ApiError.NotConfigured)
        }
    }

    /** Opens a saved draft in the confirm dialog to edit, post, or save again. */
    fun openDraft(draft: Draft) {
        if (_state.value == PostState.Posting) return
        key = null
        _state.value = PostState.Confirm(draft.text, emptyList(), draft.files, useCase.limit, title = draft.title, draftId = draft.id)
    }

    val canSaveDraft get() = useCase.drafts != null

    /** Save draft (improvement #8): keeps the dialog's text and media to finish later, over the draft it came from. */
    fun saveDraft() {
        val confirm = _state.value as? PostState.Confirm ?: return
        val drafts = useCase.drafts ?: return
        if (confirm.trimming || (confirm.text.isBlank() && confirm.images.isEmpty())) return
        // Scheduler titles are kept; a manual post's draft is titled by its text.
        val title = confirm.title.takeIf { confirm.source == PostSource.SCHEDULED || confirm.draftId != null }
        _state.value = runCatching { drafts.save(confirm.text, confirm.images, confirm.draftId, title) }
            .fold({ PostState.DraftSaved }, { PostState.Failed("Couldn't save the draft: ${it.message}", openSettings = false) })
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
            _state.value = when (val result = useCase.post(confirm.text, confirm.images, confirm.source, confirm.title)) {
                is ApiResult.Ok -> {
                    confirm.draftId?.let { id -> runCatching { useCase.drafts?.delete(id) } }
                    PostState.Posted(result.value)
                }
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
        onPublished = PostHistoryStore(context)::add,
        drafts = DraftStore(context),
        carouselPdf = AndroidCarouselPdfMaker(context),
    )
}

package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.ChatItem
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.chat.ContentAndMediaSaver
import com.smassistant.app.chat.VideoSaver
import com.smassistant.app.generate.ContentAndVideo
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.appGenerateTechnicalContent
import com.smassistant.app.generate.appGenerateVideo
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.ErrorCard
import com.smassistant.app.ui.components.GeneratingRow
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import java.io.File

/** S13 Generate Content & Video (docs/requirements/06 §6.5, slide 10): text, then the video, then Post in LinkedIn. */
@Composable
fun ContentAndVideoScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val useCase = remember(appContext) {
        GenerateContentAndMediaUseCase(
            appGenerateTechnicalContent(appContext)::invoke,
            appGenerateVideo(appContext)::invoke,
            GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP,
        )
    }
    val viewModel = viewModel { ChatViewModel(createSavedStateHandle(), ContentAndMediaSaver(VideoSaver)) { prompt -> useCase(prompt) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.CONTENT_AND_VIDEO.title,
        state = state,
        placeholder = "Paste your prompt",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { result ->
            // As in Content & Image: find the result's item so a half can be retried in place.
            val id = state.items.filterIsInstance<ChatItem.Result<ContentAndVideo>>().firstOrNull { it.value === result }?.id
            ContentAndVideoResult(
                result = result,
                posting = postState == PostState.Posting,
                onRetryText = {
                    id?.let { viewModel.updateResult(it, { r -> r.copy(retryingText = true) }, useCase::retryText) }
                },
                onRetryVideo = {
                    id?.let { viewModel.updateResult(it, { r -> r.copy(retryingMedia = true) }, useCase::retryMedia) }
                },
                onPost = { video -> postViewModel.start(result.markdown.orEmpty(), listOfNotNull(video)) },
            )
        },
    )
    PostDialogs(
        state = postState,
        onTextChange = postViewModel::onTextChange,
        onConfirm = postViewModel::confirm,
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSetUpLinkedIn,
    )
}

/**
 * The text (or its error with Retry), then the video (or its error), then Post in LinkedIn with
 * the text as the body and the video attached (CV-1, CV-2). Without a video the text posts alone.
 */
@Composable
fun ContentAndVideoResult(
    result: ContentAndVideo,
    posting: Boolean,
    onRetryText: () -> Unit,
    onRetryVideo: () -> Unit,
    onPost: (File?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            result.retryingText -> GeneratingRow()
            result.markdown != null -> MarkdownText(result.markdown)
            result.textError != null -> ErrorCard(result.textError, "Retry text", onRetryText)
        }
        when {
            result.retryingMedia -> GeneratingRow()
            result.media != null -> VideoResult(result.media, posting = posting, onPost = { onPost(result.media.file) })
            else -> {
                result.mediaError?.let { ErrorCard(it, "Retry video", onRetryVideo) }
                if (result.markdown != null) {
                    PostInLinkedInButton(onClick = { onPost(null) }, busy = posting)
                }
            }
        }
    }
}

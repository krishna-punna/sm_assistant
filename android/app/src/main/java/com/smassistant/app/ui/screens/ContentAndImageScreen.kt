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
import com.smassistant.app.chat.ImagesSaver
import com.smassistant.app.generate.ContentAndImages
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.appGenerateImages
import com.smassistant.app.generate.appGenerateTechnicalContent
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.TextAgentDropdown
import com.smassistant.app.ui.components.ImageAgentDropdown
import com.smassistant.app.ui.components.ErrorCard
import com.smassistant.app.ui.components.GeneratingRow
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import java.io.File

/** S12 Generate Content & Image (docs/requirements/06 §6.4, slide 11): text, then images, then Post in LinkedIn. */
@Composable
fun ContentAndImageScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val useCase = remember(appContext) {
        GenerateContentAndMediaUseCase(
            appGenerateTechnicalContent(appContext)::invoke,
            appGenerateImages(appContext)::invoke,
            GenerateContentAndMediaUseCase.IMAGES_NOT_SET_UP,
        )
    }
    val viewModel = viewModel { ChatViewModel(createSavedStateHandle(), ContentAndMediaSaver(ImagesSaver)) { prompt -> useCase(prompt) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.CONTENT_AND_IMAGE.title,
        state = state,
        placeholder = "Paste your prompt",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        header = {
            TextAgentDropdown()
            ImageAgentDropdown()
        },
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { result ->
            // ChatScreen hands over the value; find its item (same instance) so a half can be retried in place.
            val id = state.items.filterIsInstance<ChatItem.Result<ContentAndImages>>().firstOrNull { it.value === result }?.id
            ContentAndImagesResult(
                result = result,
                posting = postState == PostState.Posting,
                onRetryText = {
                    id?.let { viewModel.updateResult(it, { r -> r.copy(retryingText = true) }, useCase::retryText) }
                },
                onRetryImages = {
                    id?.let { viewModel.updateResult(it, { r -> r.copy(retryingMedia = true) }, useCase::retryMedia) }
                },
                onPost = { images -> postViewModel.start(result.markdown.orEmpty(), images) },
                onBackToHome = onBackToHome,
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

/** The text (or its error with Retry), then the image strip (or its error), then Post in LinkedIn (CI-1, CI-2). */
@Composable
fun ContentAndImagesResult(
    result: ContentAndImages,
    posting: Boolean,
    onRetryText: () -> Unit,
    onRetryImages: () -> Unit,
    onPost: (List<File>) -> Unit,
    onBackToHome: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            result.retryingText -> GeneratingRow()
            result.markdown != null -> MarkdownText(result.markdown)
            result.textError != null -> ErrorCard(result.textError, "Retry text", onRetryText)
        }
        when {
            result.retryingMedia -> GeneratingRow()
            result.media != null -> ImageResult(
                images = result.media,
                posting = posting,
                onPost = onPost,
                onBackToHome = onBackToHome,
                canPostWithoutImages = result.markdown != null,
            )
            else -> {
                result.mediaError?.let { ErrorCard(it, "Retry images", onRetryImages) }
                if (result.markdown != null) {
                    PostInLinkedInButton(onClick = { onPost(emptyList()) }, busy = posting)
                }
            }
        }
    }
}

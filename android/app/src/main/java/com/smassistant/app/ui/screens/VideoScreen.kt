package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.VideoSaver
import com.smassistant.app.chat.sessionChatViewModel
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.media.sizeText
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.MediaActions
import com.smassistant.app.ui.components.MediaAgentDropdown
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.TextAgentDropdown
import com.smassistant.app.ui.components.VideoLengthDropdown
import com.smassistant.app.ui.components.VideoPlayer
import com.smassistant.app.video.GeneratedVideo
import com.smassistant.app.video.appGenerateVideo
import com.smassistant.app.video.name
import com.smassistant.app.video.parseVideoTemplates
import com.smassistant.app.video.videoLabel
import com.smassistant.app.video.videoLengthLabel

/** S11 Generate Video (docs/requirements/06 §6.3, Q-08 = slideshow): prompt card, then the video. */
@Composable
fun VideoScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val useCase = appGenerateVideo(appContext)
        sessionChatViewModel(appContext, Feature.VIDEO.name, createSavedStateHandle(), VideoSaver) { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext), appSmartTrim(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.VIDEO.title,
        state = state,
        placeholder = "Paste your prompt",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        header = {
            TextAgentDropdown(showModel = true, purpose = MediaPurpose.VIDEO)
            MediaAgentDropdown(MediaPurpose.VIDEO)
            VideoLengthDropdown()
        },
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { video ->
            VideoResult(video, posting = postState == PostState.Posting, onPost = { postViewModel.start("", listOf(video.file)) })
        },
        noAgentMessage = GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP,
    )
    PostDialogs(
        state = postState,
        onTextChange = postViewModel::onTextChange,
        onTrimWithAi = postViewModel::trimWithAi,
        onCutToFit = postViewModel::cutToFit,
        onConfirm = postViewModel::confirm,
        onSaveDraft = if (postViewModel.canSaveDraft) postViewModel::saveDraft else null,
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSetUpLinkedIn,
    )
}

/** One generated video: the inline player, what it's made of, Post in LinkedIn (VI-3), then Share and Save to Gallery. */
@Composable
fun VideoResult(video: GeneratedVideo, posting: Boolean, onPost: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoPlayer(video.file, aspectRatio = video.format.width.toFloat() / video.format.height)
        val appContext = LocalContext.current.applicationContext
        // "Workflow or Lifecycle · 9:16 · 1080×1920" for a video made from a template (docs/design/video-templates.md):
        // the size is the one the MP4 was encoded at (docs/requirements/18, SV-10).
        val label = remember(video.file) {
            videoLabel(video.file)?.name(runCatching { parseVideoTemplates(loadPromptAsset(appContext, "video/video_templates")) }.getOrDefault(emptyList()))
        }
        Text(
            listOfNotNull(label, sizeText(video.format.width, video.format.height), "${video.slides.size} slides, ${videoLengthLabel(video.seconds)}")
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PostInLinkedInButton(onClick = onPost, busy = posting)
        MediaActions(files = { listOf(video.file) })
    }
}

package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.sessionChatViewModel
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.chat.VideoSaver
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.GeneratedVideo
import com.smassistant.app.generate.appGenerateVideo
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.generate.videoLengthLabel
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.MediaAgentDropdown
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.TextAgentDropdown
import com.smassistant.app.ui.components.VideoLengthDropdown
import com.smassistant.app.ui.components.VideoPlayer

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
            TextAgentDropdown(label = "Text agent", showModel = true)
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
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSetUpLinkedIn,
    )
}

/** One generated video: the inline player, what it's made of, and Post in LinkedIn (VI-3). */
@Composable
fun VideoResult(video: GeneratedVideo, posting: Boolean, onPost: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoPlayer(video.file, aspectRatio = 1f)
        Text(
            "${video.slides.size} slides, ${videoLengthLabel(video.seconds)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PostInLinkedInButton(onClick = onPost, busy = posting)
    }
}

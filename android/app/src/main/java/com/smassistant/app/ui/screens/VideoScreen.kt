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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.generate.GeneratedVideo
import com.smassistant.app.generate.appGenerateVideo
import com.smassistant.app.media.SECONDS_PER_SLIDE
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.VideoPlayer

/** Shown when Gemini (the slide maker) isn't set up. */
const val VIDEO_NOT_SET_UP = "Generate Video uses Gemini to draw the slides. Add a Gemini key to continue."

/** S11 Generate Video (docs/requirements/06 §6.3, Q-08 = slideshow): prompt card, then the video. */
@Composable
fun VideoScreen(onSetUpAgent: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val useCase = appGenerateVideo(appContext)
        ChatViewModel<GeneratedVideo> { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { VideoResult(it) },
        noAgentMessage = VIDEO_NOT_SET_UP,
    )
}

/** One generated video: the inline player and what it's made of. */
@Composable
fun VideoResult(video: GeneratedVideo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VideoPlayer(video.file, aspectRatio = 1f)
        Text(
            "${video.slides.size} slides, ${video.slides.size * SECONDS_PER_SLIDE} seconds",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

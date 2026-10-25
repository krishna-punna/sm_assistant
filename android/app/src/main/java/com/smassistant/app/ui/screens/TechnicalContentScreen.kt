package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.generate.appGenerateTechnicalContent
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard

/** S9 Generate Technical Content (docs/requirements/06 §6.1): the prompt card, the rendered post, Post in LinkedIn. */
@Composable
fun TechnicalContentScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val useCase = appGenerateTechnicalContent(appContext)
        ChatViewModel<String> { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.TECHNICAL_CONTENT.title,
        state = state,
        placeholder = "Paste your prompt",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { markdown ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownText(markdown)
                PostInLinkedInButton(onClick = { postViewModel.start(markdown) }, busy = postState == PostState.Posting)
            }
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

package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.chat.TextSaver
import com.smassistant.app.chat.sessionChatViewModel
import com.smassistant.app.generate.appGenerateTechnicalContent
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.navigation.Feature
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PostDialogs
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PostLengthNote
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.TextAgentDropdown

/** S9 Generate Technical Content (docs/requirements/06 §6.1): the prompt card, the rendered post, Post in LinkedIn. */
@Composable
fun TechnicalContentScreen(onSetUpAgent: () -> Unit, onSetUpLinkedIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val useCase = appGenerateTechnicalContent(appContext)
        sessionChatViewModel(appContext, Feature.TECHNICAL_CONTENT.name, createSavedStateHandle(), TextSaver) { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext), appSmartTrim(appContext)) }
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
        header = { TextAgentDropdown() },
        inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
        resultContent = { markdown ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownText(markdown)
                PostLengthNote(postViewModel.measure(markdown), postViewModel.limit)
                PostInLinkedInButton(onClick = { postViewModel.start(markdown) }, busy = postState == PostState.Posting)
            }
        },
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

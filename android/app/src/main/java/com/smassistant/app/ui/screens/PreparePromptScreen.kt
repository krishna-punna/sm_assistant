package com.smassistant.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.chat.PreparedPromptSaver
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.PreparePromptUseCase
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.UserBubble

/** S8 Prepare LinkedIn Post Prompt (docs/requirements/05). No Post in LinkedIn button here (07). */
@Composable
fun PreparePromptScreen(onSetUpAgent: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val selector = appAgentSelector(appContext)
        val useCase = PreparePromptUseCase(loadPromptAsset(appContext, "prepare_prompt"), selector::textAgent)
        ChatViewModel(createSavedStateHandle(), PreparedPromptSaver) { topic -> useCase(topic) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        title = Feature.PREPARE_PROMPT.title,
        state = state,
        placeholder = "Enter a topic, e.g. Design a system for Short URL",
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onRetry = viewModel::retry,
        onCancel = viewModel::cancel,
        onSetUpAgent = onSetUpAgent,
        onBackToHome = onBackToHome,
        inputContent = { UserBubble(it) },
        resultContent = { PromptCard(it.title, it.prompt) },
    )
}

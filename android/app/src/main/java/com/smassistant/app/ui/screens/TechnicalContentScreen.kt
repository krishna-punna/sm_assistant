package com.smassistant.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.generate.GenerateTextUseCase
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PromptCard

/** S9 Generate Technical Content (docs/requirements/06 §6.1): the prompt card, then the rendered post. */
@Composable
fun TechnicalContentScreen(onSetUpAgent: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val selector = appAgentSelector(appContext)
        val useCase = GenerateTextUseCase(loadPromptAsset(appContext, "technical_content"), selector::textAgent)
        ChatViewModel<String> { prompt -> useCase(prompt) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

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
        resultContent = { MarkdownText(it) },
    )
}

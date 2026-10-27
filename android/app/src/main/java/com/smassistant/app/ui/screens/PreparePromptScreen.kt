package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.ChatViewModel
import com.smassistant.app.chat.PromptSetSaver
import com.smassistant.app.navigation.Feature
import com.smassistant.app.prompt.CONTENT_PROMPT_LABEL
import com.smassistant.app.prompt.POST_PROMPT_LABEL
import com.smassistant.app.prompt.PreparePromptsUseCase
import com.smassistant.app.prompt.PreparedPrompt
import com.smassistant.app.prompt.PromptSet
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.prompt.loadPromptAsset
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.PromptTypeDropdown
import com.smassistant.app.ui.components.TextAgentDropdown
import com.smassistant.app.ui.components.UserBubble

/**
 * S8 Prepare LinkedIn Post Prompt (docs/requirements/05). The Prompt Type picks what the prompt
 * is for (docs/requirements/14). No Post in LinkedIn button here (07).
 */
@Composable
fun PreparePromptScreen(onSetUpAgent: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel {
        val selector = appAgentSelector(appContext)
        val useCase = PreparePromptsUseCase(
            loadPromptAsset(appContext, "prepare_prompt"),
            loadPromptAsset(appContext, "prepare_content_prompt"),
            selector::textAgent,
            AgentPreferences(appContext)::promptType,
        )
        ChatViewModel(createSavedStateHandle(), PromptSetSaver) { topic -> useCase(topic) }
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
        header = {
            TextAgentDropdown()
            PromptTypeDropdown()
        },
        inputContent = { UserBubble(it) },
        resultContent = { PromptSetCards(it) },
    )
}

/**
 * One prepared result: a Text prompt is the card alone, as before; other types label each
 * card, the post prompt first, so it's clear which prompt goes where.
 */
@Composable
fun PromptSetCards(set: PromptSet) {
    if (set.type == PromptType.TEXT && set.post != null) {
        PromptCard(set.post.title, set.post.prompt)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        set.post?.let { LabelledPromptCard(POST_PROMPT_LABEL, it) }
        set.content?.let { LabelledPromptCard(CONTENT_PROMPT_LABEL, it) }
    }
}

@Composable
private fun LabelledPromptCard(label: String, prompt: PreparedPrompt) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        PromptCard(prompt.title, prompt.prompt)
    }
}

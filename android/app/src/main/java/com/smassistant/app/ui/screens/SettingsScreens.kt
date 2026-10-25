package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.IMAGE_AGENTS
import com.smassistant.app.agents.TEXT_AGENTS
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ModelCatalog
import com.smassistant.app.navigation.AiAgent
import com.smassistant.app.settings.ApiSettingsUiState
import com.smassistant.app.settings.ApiSettingsViewModel
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SelectDropdown
import com.smassistant.app.ui.components.SmScreen

/** S2 LinkedIn settings. */
@Composable
fun LinkedInSettingsScreen(onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "LinkedIn", onBackToHome = onBackToHome) {
        ApiSettingsForm(ApiService.LINKEDIN)
    }
}

/** S3 AI Agent list. */
@Composable
fun AiAgentListScreen(onAgentSelected: (AiAgent) -> Unit, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "AI Agent", onBackToHome = onBackToHome) {
        AiAgent.entries.forEach { agent ->
            MenuButton("${agent.displayName} AI Agent") { onAgentSelected(agent) }
        }
        DefaultTextAgentPicker()
        DefaultImageAgentPicker()
    }
}

/** Which agent text features use first (AGC-4, interim answer to Q-05). */
@Composable
private fun DefaultTextAgentPicker() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    var selected by remember { mutableStateOf(preferences.defaultTextAgent ?: TEXT_AGENTS.first()) }
    DefaultTextAgentChips(selected) {
        preferences.defaultTextAgent = it
        selected = it
    }
}

@Composable
fun DefaultTextAgentChips(selected: ApiService, onSelect: (ApiService) -> Unit) =
    DefaultAgentChips("Default agent for text", TEXT_AGENTS, selected, onSelect)

/** Which agent makes images and video slides: Gemini paints them, Claude draws them as SVG. */
@Composable
private fun DefaultImageAgentPicker() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    var selected by remember { mutableStateOf(preferences.defaultImageAgent ?: IMAGE_AGENTS.first()) }
    DefaultAgentChips("Default agent for images", IMAGE_AGENTS, selected) {
        preferences.defaultImageAgent = it
        selected = it
    }
}

@Composable
private fun DefaultAgentChips(title: String, agents: List<ApiService>, selected: ApiService, onSelect: (ApiService) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        agents.forEach { service ->
            FilterChip(
                selected = service == selected,
                onClick = { onSelect(service) },
                label = { Text(service.displayName) },
            )
        }
    }
    Text(
        "If it isn't set up, the next set-up agent is used.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/** S4–S6 settings for one AI agent. */
@Composable
fun AgentSettingsScreen(agent: AiAgent, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "${agent.displayName} AI Agent", onBackToHome = onBackToHome) {
        ApiSettingsForm(agent.service)
    }
}

/** API + API key form shared by the settings screens. */
@Composable
private fun ColumnScope.ApiSettingsForm(service: ApiService) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel(key = service.name) {
        ApiSettingsViewModel(service, ApiSettingsStore(appContext), catalog = if (service in TEXT_AGENTS) ModelCatalog() else null)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    ApiSettingsFields(
        serviceName = service.displayName,
        state = state,
        onApiChange = viewModel::onApiChange,
        onKeyChange = viewModel::onKeyChange,
        onSave = viewModel::onSave,
        onModelChange = viewModel::onModelChange,
        onImageModelChange = viewModel::onImageModelChange,
        onRefreshModels = viewModel::refreshModels,
    )
}

/** Stateless fields + SAVE, so the form can be UI-tested with any state. */
@Composable
fun ColumnScope.ApiSettingsFields(
    serviceName: String,
    state: ApiSettingsUiState,
    onApiChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onModelChange: (String) -> Unit = {},
    onImageModelChange: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
) {
    val apiError = state.apiError
    val keyError = state.keyError
    OutlinedTextField(
        value = state.api,
        onValueChange = onApiChange,
        label = { Text("$serviceName APIs") },
        singleLine = true,
        isError = apiError != null,
        supportingText = if (apiError != null) {
            { Text(apiError) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apiField"),
    )
    OutlinedTextField(
        value = state.key,
        onValueChange = onKeyChange,
        label = { Text("$serviceName API Key") },
        placeholder = { Text("Enter your $serviceName API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        isError = keyError != null,
        supportingText = if (keyError != null) {
            { Text(keyError) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keyField"),
    )
    if (state.showModels) {
        ModelPickers(state, onModelChange, onImageModelChange, onRefreshModels)
    }
    PrimaryButton("SAVE", onClick = onSave)
}

/** Model dropdowns (Q-04) with the list's status and a Refresh that fetches it again. */
@Composable
private fun ModelPickers(
    state: ApiSettingsUiState,
    onModelChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    val labelOf = { options: List<AiModel> -> { id: String -> options.firstOrNull { it.id == id }?.label ?: id.ifBlank { "Choose a model" } } }
    val text = state.textOptions
    SelectDropdown("Model", state.model, text.map { it.id }, labelOf(text), onModelChange, "modelPicker")
    if (state.showImageModel) {
        val image = state.imageOptions
        SelectDropdown("Image model", state.imageModel, image.map { it.id }, labelOf(image), onImageModelChange, "imageModelPicker")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        val status = when {
            state.loadingModels -> "Updating the model list…"
            state.modelsError != null -> state.modelsError
            state.models.text.isEmpty() -> "Save your API key to load the models."
            else -> "${state.models.text.size} models available"
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.modelsError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .testTag("modelStatus"),
        )
        TextButton(onClick = onRefreshModels, enabled = !state.loadingModels && state.key.isNotBlank()) {
            Text("Refresh models")
        }
    }
}

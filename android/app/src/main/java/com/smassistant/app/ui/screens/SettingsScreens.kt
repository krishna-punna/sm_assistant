package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.IMAGE_AGENTS
import com.smassistant.app.agents.TEXT_AGENTS
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiConnectionCheck
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.data.ModelCatalog
import com.smassistant.app.linkedin.LinkedInAuthStore
import com.smassistant.app.navigation.AiAgent
import com.smassistant.app.settings.ApiSettingsUiState
import com.smassistant.app.settings.ApiSettingsViewModel
import com.smassistant.app.settings.LinkedInAuthUiState
import com.smassistant.app.settings.LinkedInAuthViewModel
import com.smassistant.app.ui.components.LinkedInSignInDialog
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SelectDropdown
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.components.clearLinkedInBrowserLogin
import java.text.DateFormat
import java.util.Date

/** S2 LinkedIn settings: Sign in with LinkedIn (Q-03), or paste an access token as a fallback. */
@Composable
fun LinkedInSettingsScreen(onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val auth = viewModel { LinkedInAuthViewModel(LinkedInAuthStore(appContext), ApiSettingsStore(appContext)) }
    val pasteForm = apiSettingsViewModel(ApiService.LINKEDIN)
    val state by auth.state.collectAsStateWithLifecycle()
    val pasted by pasteForm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_LONG).show()
            auth.messageShown()
        }
    }
    // Sign-in or sign-out replaced the token: show it in the paste form.
    LaunchedEffect(state.tokenVersion) {
        if (state.tokenVersion > 0) pasteForm.reload()
    }
    // A pasted token replaces the signed-in one: drop the "Signed in as" line.
    LaunchedEffect(pasted.message) { auth.refreshSession() }

    SmScreen(sectionTitle = "LinkedIn", onBackToHome = onBackToHome) {
        LinkedInSignInFields(
            state = state,
            now = System.currentTimeMillis(),
            onClientIdChange = auth::onClientIdChange,
            onClientSecretChange = auth::onClientSecretChange,
            onRedirectUriChange = auth::onRedirectUriChange,
            onSignIn = auth::signIn,
            onSignOut = {
                clearLinkedInBrowserLogin()
                auth.signOut()
            },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("Or paste an access token", style = MaterialTheme.typography.titleSmall)
        Text(
            "Use this if you already have a token with the w_member_social permission, e.g. from LinkedIn's OAuth token tool.",
            style = MaterialTheme.typography.bodySmall,
        )
        ApiSettingsForm(ApiService.LINKEDIN, pasteForm)
    }
    state.authorizationUrl?.let { url ->
        LinkedInSignInDialog(url, onNavigate = auth::onBrowserNavigate, onCancel = auth::cancelSignIn)
    }
}

/** Stateless sign-in section, so it can be UI-tested with any state. */
@Composable
fun ColumnScope.LinkedInSignInFields(
    state: LinkedInAuthUiState,
    now: Long,
    onClientIdChange: (String) -> Unit,
    onClientSecretChange: (String) -> Unit,
    onRedirectUriChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val session = state.session
    if (session != null) {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(session.expiresAt))
        val expired = state.isExpired(now)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Signed in as ${session.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("linkedInSession"))
                Text(
                    if (expired) "Token expired on $date. Sign in again." else "Token expires on $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    } else {
        Text("Sign in with your LinkedIn developer app", style = MaterialTheme.typography.titleSmall)
    }
    SettingsTextField(state.clientId, onClientIdChange, "Client ID", state.clientIdError, "clientIdField")
    SettingsTextField(state.clientSecret, onClientSecretChange, "Client Secret", state.clientSecretError, "clientSecretField", secret = true)
    SettingsTextField(
        state.redirectUri,
        onRedirectUriChange,
        "Redirect URL",
        state.redirectUriError ?: "Add this exact URL under Authorized redirect URLs in your LinkedIn app.",
        "redirectUriField",
        isError = state.redirectUriError != null,
    )
    Text(
        "The Client Secret is stored encrypted on this phone only. Use a LinkedIn app made just for SM Assistant.",
        style = MaterialTheme.typography.bodySmall,
    )
    PrimaryButton(
        when {
            state.finishing -> "Signing in…"
            session != null -> "Sign in again"
            else -> "Sign in with LinkedIn"
        },
        onClick = onSignIn,
        enabled = !state.finishing,
    )
}

@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String?,
    tag: String,
    secret: Boolean = false,
    isError: Boolean = supporting != null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        isError = isError,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    )
}

/** S3 AI Agent list. */
@Composable
fun AiAgentListScreen(onAgentSelected: (AiAgent) -> Unit, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "AI Agent", onBackToHome = onBackToHome) {
        AiAgent.entries.forEach { agent ->
            MenuButton("${agent.displayName} AI Agent") { onAgentSelected(agent) }
        }
        DefaultTextAgentPicker()
        DefaultImageAgentPicker(MediaPurpose.IMAGE)
        DefaultImageAgentPicker(MediaPurpose.VIDEO)
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

/** Which agent makes images, or video slides ([purpose]): Gemini and the diffusion agents paint them. */
@Composable
private fun DefaultImageAgentPicker(purpose: MediaPurpose) {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    var selected by remember { mutableStateOf(preferences.defaultMediaAgent(purpose) ?: IMAGE_AGENTS.first()) }
    val title = if (purpose == MediaPurpose.VIDEO) "Default agent for video" else "Default agent for images"
    DefaultAgentChips(title, IMAGE_AGENTS, selected) {
        preferences.setDefaultMediaAgent(purpose, it)
        selected = it
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultAgentChips(title: String, agents: List<ApiService>, selected: ApiService, onSelect: (ApiService) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun apiSettingsViewModel(service: ApiService): ApiSettingsViewModel {
    val appContext = LocalContext.current.applicationContext
    return viewModel(key = service.name) {
        ApiSettingsViewModel(
            service,
            ApiSettingsStore(appContext),
            catalog = if (service in TEXT_AGENTS) ModelCatalog() else null,
            check = ApiConnectionCheck()::check,
        )
    }
}

/** API + API key form shared by the settings screens. */
@Composable
private fun ColumnScope.ApiSettingsForm(service: ApiService, viewModel: ApiSettingsViewModel = apiSettingsViewModel(service)) {
    val appContext = LocalContext.current.applicationContext
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
        onAccountIdChange = viewModel::onAccountIdChange,
        onSave = { viewModel.onSave() },
        onTest = viewModel::test,
        onModelChange = viewModel::onModelChange,
        onImageModelChange = viewModel::onImageModelChange,
        onVideoModelChange = viewModel::onVideoModelChange,
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
    onAccountIdChange: (String) -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onImageModelChange: (String) -> Unit = {},
    onVideoModelChange: (String) -> Unit = {},
    onRefreshModels: () -> Unit = {},
    onTest: () -> Unit = {},
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
    if (state.showAccountId) {
        SettingsTextField(
            state.accountId,
            onAccountIdChange,
            "$serviceName Account ID",
            state.accountIdError ?: "On the Cloudflare dashboard: Workers AI, then Use REST API.",
            "accountIdField",
            isError = state.accountIdError != null,
        )
    }
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
        ModelPickers(state, onModelChange, onImageModelChange, onVideoModelChange, onRefreshModels)
    }
    PrimaryButton("SAVE", onClick = onSave)
    if (state.canTest) TestRow(state.testStatus, onTest)
}

/** Test (AS-7): checks the key with the API Status request and says what it found, next to SAVE. */
@Composable
private fun TestRow(status: ConnectionStatus?, onTest: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (text, ok) = when (status) {
            null -> "Check the key works with a quick read-only request." to null
            ConnectionStatus.Checking -> "Testing…" to null
            ConnectionStatus.Connected -> "✓ Connected: the key works" to true
            ConnectionStatus.NotConfigured -> "Save the API and key first" to false
            ConnectionStatus.InvalidKey -> "✗ Invalid key: the API rejected it" to false
            ConnectionStatus.NoPermission -> "⚠ No access: the key works but lacks permission for this check" to false
            is ConnectionStatus.Unreachable -> "✗ Unreachable: ${status.reason}" to false
            is ConnectionStatus.Error -> "✗ Error: unexpected response (HTTP ${status.httpCode})" to false
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = when (ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .testTag("testStatus"),
        )
        TextButton(
            onClick = onTest,
            enabled = status != ConnectionStatus.Checking,
            modifier = Modifier.testTag("testButton"),
        ) { Text("Test") }
    }
}

/** Model dropdowns (Q-04) with the list's status and a Refresh that fetches it again. */
@Composable
private fun ModelPickers(
    state: ApiSettingsUiState,
    onModelChange: (String) -> Unit,
    onImageModelChange: (String) -> Unit,
    onVideoModelChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    // A blank id is the "Same as …" fallback (see ApiConfig.forMedia); otherwise the picker asks for a choice.
    val labelOf = { options: List<AiModel>, blank: String ->
        { id: String -> options.firstOrNull { it.id == id }?.label ?: id.ifBlank { blank } }
    }
    val text = state.textOptions
    SelectDropdown("Model", state.model, text.map { it.id }, labelOf(text, "Choose a model"), onModelChange, "modelPicker")
    if (state.showImageModel) {
        val image = state.imageOptions
        SelectDropdown("Image model", state.imageModel, image.map { it.id }, labelOf(image, "Choose a model"), onImageModelChange, "imageModelPicker")
        val video = state.videoOptions
        val videoIds = listOf("") + video.map { it.id }
        SelectDropdown("Video model", state.videoModel, videoIds, labelOf(video, "Same as image model"), onVideoModelChange, "videoModelPicker")
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

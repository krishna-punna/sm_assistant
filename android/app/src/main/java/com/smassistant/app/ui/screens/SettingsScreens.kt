package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.smassistant.app.navigation.AiAgent
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SmScreen

/** S2 LinkedIn settings. */
@Composable
fun LinkedInSettingsScreen(onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "LinkedIn", onBackToHome = onBackToHome) {
        ApiSettingsForm(apiLabel = "LinkedIn APIs", keyLabel = "LinkedIn API Key")
    }
}

/** S3 AI Agent list. */
@Composable
fun AiAgentListScreen(onAgentSelected: (AiAgent) -> Unit, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "AI Agent", onBackToHome = onBackToHome) {
        AiAgent.entries.forEach { agent ->
            MenuButton("${agent.displayName} AI Agent") { onAgentSelected(agent) }
        }
    }
}

/** S4–S6 settings for one AI agent. */
@Composable
fun AgentSettingsScreen(agent: AiAgent, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "${agent.displayName} AI Agent", onBackToHome = onBackToHome) {
        ApiSettingsForm(
            apiLabel = "${agent.displayName} APIs",
            keyLabel = "${agent.displayName} API Key",
        )
    }
}

/** API + API key form shared by the settings screens. Saving is not wired up yet. */
@Composable
private fun ColumnScope.ApiSettingsForm(apiLabel: String, keyLabel: String) {
    var api by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }

    OutlinedTextField(
        value = api,
        onValueChange = { api = it },
        label = { Text(apiLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(keyLabel) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("SAVE", onClick = { /* TODO: persist settings */ })
}

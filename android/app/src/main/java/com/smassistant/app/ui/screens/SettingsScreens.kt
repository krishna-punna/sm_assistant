package com.smassistant.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.navigation.AiAgent
import com.smassistant.app.ui.components.MenuButton
import com.smassistant.app.ui.components.PrimaryButton
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
    }
}

/** S4–S6 settings for one AI agent. */
@Composable
fun AgentSettingsScreen(agent: AiAgent, onBackToHome: () -> Unit) {
    SmScreen(sectionTitle = "${agent.displayName} AI Agent", onBackToHome = onBackToHome) {
        ApiSettingsForm(agent.service)
    }
}

/** API + API key form shared by the settings screens, pre-filled from [ApiSettingsStore]. */
@Composable
private fun ColumnScope.ApiSettingsForm(service: ApiService) {
    val context = LocalContext.current
    val store = remember { ApiSettingsStore(context) }
    val saved = remember(service) { store.load(service) }
    var api by rememberSaveable(service) { mutableStateOf(saved.api) }
    var key by rememberSaveable(service) { mutableStateOf(saved.key) }

    OutlinedTextField(
        value = api,
        onValueChange = { api = it },
        label = { Text("${service.displayName} APIs") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text("${service.displayName} API Key") },
        placeholder = { Text("Enter your ${service.displayName} API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("SAVE", onClick = {
        store.save(service, api, key)
        Toast.makeText(context, "${service.displayName} settings saved", Toast.LENGTH_SHORT).show()
    })
}

package com.smassistant.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.TEXT_AGENTS
import com.smassistant.app.agents.isUsableAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore

/**
 * "Agent: Claude ▾" above a text feature's conversation: picks which set-up agent writes the
 * next result. The choice is the same default as on the AI Agent screen, so it sticks.
 */
@Composable
fun TextAgentDropdown() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    val agents = remember {
        val store = ApiSettingsStore(appContext)
        TEXT_AGENTS.filter { store.load(it).isUsableAgent() }
    }
    if (agents.isEmpty()) return
    // Same rule as AgentSelector: the default if it's set up, otherwise the first set-up agent.
    var selected by remember { mutableStateOf(preferences.defaultTextAgent?.takeIf { it in agents } ?: agents.first()) }
    AgentDropdown(agents, selected) {
        preferences.defaultTextAgent = it
        selected = it
    }
}

@Composable
fun AgentDropdown(agents: List<ApiService>, selected: ApiService, onSelect: (ApiService) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Agent", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("agentDropdown")) {
                Text("${selected.displayName} ▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                agents.forEach { agent ->
                    DropdownMenuItem(
                        text = { Text(if (agent == selected) "✓ ${agent.displayName}" else agent.displayName) },
                        onClick = {
                            open = false
                            onSelect(agent)
                        },
                    )
                }
            }
        }
    }
}

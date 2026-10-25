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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.agents.IMAGE_AGENTS
import com.smassistant.app.agents.TEXT_AGENTS
import com.smassistant.app.agents.isUsableAgent
import com.smassistant.app.agents.isUsableImageAgent
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

/**
 * "Image agent: Gemini ▾" on the image and video features: Gemini paints the images, Claude
 * draws them as SVG. Lists only set-up agents and is hidden unless there's a choice to make.
 */
@Composable
fun ImageAgentDropdown() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    val agents = remember {
        val store = ApiSettingsStore(appContext)
        IMAGE_AGENTS.filter { store.load(it).isUsableImageAgent(it) }
    }
    if (agents.size < 2) return
    // Same rule as ImageAgentSelector: the default if it's set up, otherwise the first set-up agent.
    var selected by remember { mutableStateOf(preferences.defaultImageAgent?.takeIf { it in agents } ?: agents.first()) }
    AgentDropdown(agents, selected, label = "Image agent", testTag = "imageAgentDropdown") {
        preferences.defaultImageAgent = it
        selected = it
    }
}

@Composable
fun AgentDropdown(
    agents: List<ApiService>,
    selected: ApiService,
    label: String = "Agent",
    testTag: String = "agentDropdown",
    onSelect: (ApiService) -> Unit,
) = SelectDropdown(label, selected, agents, { it.displayName }, onSelect, testTag)

/** "[label]  [current ▾]": a button that opens a menu of [options], the current one ticked. */
@Composable
fun <T> SelectDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag(testTag)) {
                Text("${optionLabel(selected)} ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (option == selected) "✓ ${optionLabel(option)}" else optionLabel(option)) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

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
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.data.forMedia
import com.smassistant.app.generate.VIDEO_LENGTHS
import com.smassistant.app.generate.videoLengthLabel
import com.smassistant.app.prompt.PromptType

/**
 * "Agent: Gemini ▾" above a text feature's conversation: picks which set-up agent writes the
 * next result. The choice is the same default as on the AI Agent screen, so it sticks.
 *
 * The image and video screens show it as "Text agent" with a "Text model" picker ([showModel]),
 * because there the text agent and model write the content that the separate image or video
 * agent and model then draw (docs/requirements/14).
 */
@Composable
fun TextAgentDropdown(label: String = "Agent", showModel: Boolean = false) {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    val store = remember { ApiSettingsStore(appContext) }
    val agents = remember { TEXT_AGENTS.filter { store.load(it).isUsableAgent() } }
    if (agents.isEmpty()) return
    // Same rule as AgentSelector: the default if it's set up, otherwise the first set-up agent.
    var selected by remember { mutableStateOf(preferences.defaultTextAgent?.takeIf { it in agents } ?: agents.first()) }
    AgentDropdown(agents, selected, label = label) {
        preferences.defaultTextAgent = it
        selected = it
    }
    if (showModel) TextModelDropdown(store, selected)
}

/** "Text model: … ▾": the models last listed on the agent's settings screen; hidden until there are some. */
@Composable
private fun TextModelDropdown(store: ApiSettingsStore, agent: ApiService) {
    var model by remember(agent) { mutableStateOf(store.load(agent).model) }
    val listed = remember(agent) { store.cachedModels(agent)?.lists?.text.orEmpty() }
    if (listed.isEmpty()) return
    val options = if (listed.any { it.id == model }) listed else listOf(AiModel(model)) + listed
    SelectDropdown(
        label = "Text model",
        selected = model,
        options = options.map { it.id },
        optionLabel = { id -> options.firstOrNull { it.id == id }?.label ?: id },
        onSelect = {
            store.saveTextModel(agent, it)
            model = it
        },
        testTag = "textModelDropdown",
    )
}

/**
 * "Image agent: Gemini ▾" and "Image model: … ▾" on the image features, or "Video agent" and
 * "Video model" on the video features ([purpose]): images and video slides each keep their own
 * agent and model. The agent picker lists only
 * set-up agents and is hidden unless there's a choice; the model picker shows the models last
 * listed on the agent's settings screen and is hidden until there are some.
 */
@Composable
fun MediaAgentDropdown(purpose: MediaPurpose = MediaPurpose.IMAGE) {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    val store = remember { ApiSettingsStore(appContext) }
    val agents = remember { IMAGE_AGENTS.filter { store.load(it).forMedia(purpose).isUsableImageAgent(it) } }
    if (agents.isEmpty()) return
    val name = if (purpose == MediaPurpose.VIDEO) "Video" else "Image"
    // Same rule as ImageAgentSelector: the default if it's set up, otherwise the first set-up agent.
    var selected by remember { mutableStateOf(preferences.defaultMediaAgent(purpose)?.takeIf { it in agents } ?: agents.first()) }
    if (agents.size > 1) {
        AgentDropdown(agents, selected, label = "$name agent", testTag = "${name.lowercase()}AgentDropdown") {
            preferences.setDefaultMediaAgent(purpose, it)
            selected = it
        }
    }
    MediaModelDropdown(store, selected, purpose, name)
}

/**
 * "Video length: 30 sec ▾" next to the video agent and model (Requirement 9): 0:30 to 5:00 in
 * half minutes. The choice is saved, and the next video is made that long.
 */
@Composable
fun VideoLengthDropdown() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    var seconds by remember { mutableStateOf(preferences.videoSeconds) }
    SelectDropdown(
        label = "Video length",
        selected = seconds,
        options = VIDEO_LENGTHS,
        optionLabel = ::videoLengthLabel,
        onSelect = {
            preferences.videoSeconds = it
            seconds = it
        },
        testTag = "videoLengthDropdown",
    )
}

/**
 * "Prompt Type: Text ▾" on Prepare Prompt (docs/requirements/14): Text, Image, Video, Text & Image or
 * Text & Video. The choice is saved, and the next prompt is prepared for it.
 */
@Composable
fun PromptTypeDropdown() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    var type by remember { mutableStateOf(preferences.promptType) }
    SelectDropdown(
        label = "Prompt Type",
        selected = type,
        options = PromptType.entries,
        optionLabel = { it.label },
        onSelect = {
            preferences.promptType = it
            type = it
        },
        testTag = "promptTypeDropdown",
    )
}

@Composable
private fun MediaModelDropdown(store: ApiSettingsStore, agent: ApiService, purpose: MediaPurpose, name: String) {
    var model by remember(agent) { mutableStateOf(store.load(agent).forMedia(purpose).imageModel) }
    val listed = remember(agent) {
        store.cachedModels(agent)?.lists?.image.orEmpty()
    }
    if (listed.isEmpty()) return
    val options = if (listed.any { it.id == model }) listed else listOf(AiModel(model)) + listed
    SelectDropdown(
        label = "$name model",
        selected = model,
        options = options.map { it.id },
        optionLabel = { id -> options.firstOrNull { it.id == id }?.label ?: id },
        onSelect = {
            store.saveMediaModel(agent, purpose, it)
            model = it
        },
        testTag = "${name.lowercase()}ModelDropdown",
    )
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

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
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.data.ImageFormat
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.data.forMedia
import com.smassistant.app.generate.ImageTemplate
import com.smassistant.app.generate.VIDEO_LENGTHS
import com.smassistant.app.generate.parseImageTemplates
import com.smassistant.app.generate.videoLengthLabel
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.prompt.loadPromptAsset

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
    AgentStatusHint(selected, remember(selected) { store.lastStatus(selected) })
    if (showModel) TextModelDropdown(store, selected)
}

/**
 * What the last API Status check (or settings Test) found wrong with [service] (GEN-6, Q-23): a
 * warning only, since the check may be old; null when it passed or there's no result.
 */
fun agentStatusWarning(service: ApiService, status: ConnectionStatus?): String? {
    val name = service.displayName
    return when (status) {
        null, ConnectionStatus.Checking, ConnectionStatus.Connected, ConnectionStatus.NotConfigured -> null
        ConnectionStatus.InvalidKey -> "$name rejected its key in the last check. Fix it in AI Agent settings, or generating will fail."
        ConnectionStatus.NoPermission -> "$name's key lacked permission in the last check, so generating may fail."
        is ConnectionStatus.Unreachable -> "$name couldn't be reached in the last check (${status.reason})."
        is ConnectionStatus.Error -> "$name answered with an error (HTTP ${status.httpCode}) in the last check."
    }
}

/** The [agentStatusWarning] under an agent picker, in the error colour; nothing when there's none. */
@Composable
fun AgentStatusHint(service: ApiService, status: ConnectionStatus?) {
    val warning = agentStatusWarning(service, status) ?: return
    Text(
        "⚠ $warning",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("agentWarning-${service.name}"),
    )
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
    // The text agent's picker already warns about it; only a different media agent needs its own warning.
    val textAgent = remember {
        TEXT_AGENTS.filter { store.load(it).isUsableAgent() }.let { text -> preferences.defaultTextAgent?.takeIf { it in text } ?: text.firstOrNull() }
    }
    if (selected != textAgent) AgentStatusHint(selected, remember(selected) { store.lastStatus(selected) })
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

/**
 * "Template: Auto ▾" and "Format: Auto ▾" on the image screens (docs/design/image-templates.md):
 * Auto lets the AI agent pick the template for the content and the app pick the formats; a picked
 * template or format is used for every image. Both choices are saved for the next submit.
 */
@Composable
fun ImageTemplatePickers() {
    val appContext = LocalContext.current.applicationContext
    val preferences = remember { AgentPreferences(appContext) }
    val templates = remember { parseImageTemplates(loadPromptAsset(appContext, "image_templates")) }
    var template by remember { mutableStateOf(preferences.imageTemplate?.takeIf { id -> templates.any { it.id == id } }) }
    var format by remember { mutableStateOf(preferences.imageFormat) }
    TemplateFormatPickers(
        templates = templates,
        template = template,
        format = format,
        onTemplate = {
            preferences.imageTemplate = it
            template = it
        },
        onFormat = {
            preferences.imageFormat = it
            format = it
        },
    )
}

/**
 * The Template (Auto + [templates]) and Format (Auto + 2:3, 1:1, 3:2) pickers, showing [template]
 * and [format] (null is Auto); the image screens and the scheduler editor keep the choice.
 */
@Composable
fun TemplateFormatPickers(
    templates: List<ImageTemplate>,
    template: String?,
    format: ImageFormat?,
    onTemplate: (String?) -> Unit,
    onFormat: (ImageFormat?) -> Unit,
    testTagPrefix: String = "image",
) {
    SelectDropdown(
        label = "Template",
        selected = template?.takeIf { id -> templates.any { it.id == id } },
        options = listOf<String?>(null) + templates.map { it.id },
        optionLabel = { id -> if (id == null) "Auto (AI picks)" else templates.firstOrNull { it.id == id }?.name ?: id },
        onSelect = onTemplate,
        testTag = "${testTagPrefix}TemplateDropdown",
    )
    SelectDropdown(
        label = "Format",
        selected = format,
        options = listOf<ImageFormat?>(null) + ImageFormat.entries,
        optionLabel = { it?.words?.replaceFirstChar { c -> c.uppercase() } ?: "Auto" },
        onSelect = onFormat,
        testTag = "${testTagPrefix}FormatDropdown",
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

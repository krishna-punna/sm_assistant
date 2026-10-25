package com.smassistant.app.agents

import android.content.Context
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore

/** Agents that can generate text, in fallback order. */
val TEXT_AGENTS = listOf(ApiService.CLAUDE, ApiService.GEMINI, ApiService.GROQ)

/** The user's choice of default text agent (interim answer to Q-05). */
class AgentPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    var defaultTextAgent: ApiService?
        get() = prefs.getString(KEY_DEFAULT_TEXT, null)?.let { name -> TEXT_AGENTS.firstOrNull { it.name == name } }
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_TEXT, value?.name).apply()
        }

    private companion object {
        const val KEY_DEFAULT_TEXT = "default_text_agent"
    }
}

/**
 * Picks the agent for text features (AG-5, GEN-1): the user's default if it's set up,
 * otherwise the first set-up agent in [TEXT_AGENTS] order. Null when none is set up.
 */
class AgentSelector(
    private val configOf: (ApiService) -> ApiConfig,
    private val preferred: () -> ApiService?,
    private val agentFor: (ApiService) -> TextAgent,
) {
    fun textAgent(): TextAgent? =
        (listOfNotNull(preferred()) + TEXT_AGENTS).firstOrNull { configOf(it).isUsableAgent() }?.let(agentFor)
}

/** The selector the screens use: saved settings, the user's default, and the real HTTP clients. */
fun appAgentSelector(context: Context): AgentSelector {
    val store = ApiSettingsStore(context)
    val preferences = AgentPreferences(context)
    return AgentSelector(
        configOf = store::load,
        preferred = { preferences.defaultTextAgent },
        agentFor = { service -> textAgentFor(service, { store.load(service) }) },
    )
}

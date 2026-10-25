package com.smassistant.app.navigation

/** AI agents the user can configure (docs/requirements/04-ai-agent-settings.md). */
enum class AiAgent(val displayName: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    GROQ("Groq"),
}

/** Feature screens launched from the Social Media Assistant hub. */
enum class Feature(val title: String) {
    PREPARE_PROMPT("Prepare LinkedIn Post Prompt"),
    TECHNICAL_CONTENT("Generate Technical Content"),
    IMAGE("Generate Image"),
    VIDEO("Generate Video"),
    CONTENT_AND_IMAGE("Generate Content & Image"),
    CONTENT_AND_VIDEO("Generate Content & Video"),
}

object Routes {
    const val HOME = "home"
    const val LINKEDIN_SETTINGS = "settings/linkedin"
    const val AI_AGENTS = "settings/agents"
    const val AGENT_SETTINGS = "settings/agents/{agent}"
    const val HUB = "hub"
    const val FEATURE = "hub/{feature}"
    const val DEFAULT_HASHTAGS = "settings/hashtags"

    fun agentSettings(agent: AiAgent) = "settings/agents/${agent.name}"
    fun feature(feature: Feature) = "hub/${feature.name}"
}

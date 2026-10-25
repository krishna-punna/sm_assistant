package com.smassistant.app.prompt

import android.content.Context
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A prompt for a LinkedIn post, ready to paste into a generator (PP-3). */
data class PreparedPrompt(val title: String, val prompt: String)

/** Reads a system prompt from `assets/prompts/<name>.md`. */
fun loadPromptAsset(context: Context, name: String): String =
    context.assets.open("prompts/$name.md").bufferedReader().use { it.readText() }

/**
 * Prepare LinkedIn Post Prompt (docs/tasks PP-I2): sends the topic with the recipe in
 * `prepare_prompt.md` to the selected text agent and reads back `{title, prompt}`.
 */
class PreparePromptUseCase(
    private val systemPrompt: String,
    private val agent: () -> TextAgent?,
) {
    suspend operator fun invoke(topic: String): Outcome<PreparedPrompt> {
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(systemPrompt, topic)) {
            is ApiResult.Ok -> Outcome.Ok(parsePreparedPrompt(result.value, topic))
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }
}

/**
 * Reads the agent's `{title, prompt}` reply, allowing Markdown fences or text around it.
 * Anything else is taken as the prompt itself, titled from the topic (PP-T2).
 */
fun parsePreparedPrompt(reply: String, topic: String): PreparedPrompt {
    val json = reply.substringAfter('{', "").substringBeforeLast('}', "")
    val parsed = runCatching { Json.parseToJsonElement("{$json}") as? JsonObject }.getOrNull()
    val prompt = parsed?.path("prompt")?.textOrNull?.trim()
    if (prompt.isNullOrEmpty()) return PreparedPrompt(titleFromTopic(topic), reply.trim())
    val title = parsed?.path("title")?.textOrNull?.trim()
    return PreparedPrompt(title?.takeIf { it.isNotEmpty() } ?: titleFromTopic(topic), prompt)
}

/** "design a system for short URL" → "Design A System For Short URL", cut to 60 characters. */
fun titleFromTopic(topic: String): String {
    val title = topic.trim().split(Regex("\\s+")).joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    return if (title.length <= 60) title else title.take(59).trimEnd() + "…"
}

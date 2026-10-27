package com.smassistant.app.prompt

import android.content.Context
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        return ask(selected, systemPrompt, topic)
    }
}

/**
 * What one Prepare Prompt submit made for its [type]: the LinkedIn [post] prompt, the "no limit"
 * [content] prompt for an image or video, or both (docs/requirements/14).
 */
data class PromptSet(val type: PromptType, val post: PreparedPrompt? = null, val content: PreparedPrompt? = null)

/**
 * Prepare Prompt with a Prompt Type (docs/requirements/14). Text gives the post prompt as before
 * ([postPrompt] is `prepare_prompt.md`); Image and Video give a prompt for in-depth content
 * with no length limit ([contentTemplate] is `prepare_content_prompt.md`); Text & Image and
 * Text & Video give both, written in parallel. [type] is read on each submit.
 */
class PreparePromptsUseCase(
    private val postPrompt: String,
    private val contentTemplate: String,
    private val agent: () -> TextAgent?,
    private val type: () -> PromptType,
) {
    suspend operator fun invoke(topic: String): Outcome<PromptSet> {
        val type = type()
        val selected = agent() ?: return Outcome.NoAgent
        return coroutineScope<Outcome<PromptSet>> {
            val post = if (type.post) async { ask(selected, postPrompt, topic) } else null
            val content = type.media?.let { media -> async { ask(selected, contentPrompt(media), topic) } }
            val postOutcome = post?.await()
            val contentOutcome = content?.await()
            (postOutcome as? Outcome.Failed) ?: (contentOutcome as? Outcome.Failed)
                ?: Outcome.Ok(PromptSet(type, (postOutcome as? Outcome.Ok)?.value, (contentOutcome as? Outcome.Ok)?.value))
        }
    }

    private fun contentPrompt(media: MediaPurpose) = contentTemplate
        .replace("{{medium}}", if (media == MediaPurpose.VIDEO) VIDEO_MEDIUM else IMAGE_MEDIUM)
        .replace("{{structure}}", if (media == MediaPurpose.VIDEO) VIDEO_STRUCTURE else IMAGE_STRUCTURE)

    private companion object {
        const val IMAGE_MEDIUM = "one dense infographic cheat-sheet image for LinkedIn, made of numbered panels with headings, " +
            "short bullet points, tables, flow diagrams, code snippets and a key-takeaways box"
        const val VIDEO_MEDIUM = "a slideshow video for LinkedIn, shown one slide at a time, each slide a small infographic " +
            "with a heading, short points, a diagram, table or code snippet"
        const val IMAGE_STRUCTURE = "Ask for 8 to 12 sections, each with a short heading and points of at most a line each, so each section fits one panel."
        const val VIDEO_STRUCTURE = "Ask for the sections to tell one story from the problem, through how it works, to the takeaway, " +
            "each section self-contained so it can be one slide."
    }
}

private suspend fun ask(agent: TextAgent, system: String, topic: String): Outcome<PreparedPrompt> =
    when (val result = agent.generate(system, topic)) {
        is ApiResult.Ok -> Outcome.Ok(parsePreparedPrompt(result.value, topic))
        is ApiResult.Failed -> Outcome.Failed(result.error, agent.service.displayName)
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

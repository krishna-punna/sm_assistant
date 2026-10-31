package com.smassistant.app.image

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImagePromptBudgets
import com.smassistant.app.net.ApiResult
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Most posters a painted series has (AC-6): a guard against runaway replies; the AI uses as few as show everything. */
const val MAX_SERIES_PARTS = 6

/**
 * Whether image [index] of a submit is AI-painted in [mode]: Both paints images 2 and 4, Auto tries
 * to paint every one (and draws it when the painted one fails its review).
 */
fun paints(mode: ImageMode, index: Int): Boolean = when (mode) {
    ImageMode.DRAWN -> false
    ImageMode.PAINTED, ImageMode.AUTO -> true
    ImageMode.BOTH -> index % 2 == 1
}

/**
 * Writes the prompts an AI-painted image is made from (docs/design/image-templates.md §6; AC-6): the
 * filled template in full for an agent with no prompt budget (Gemini, which reads long prompts).
 * For an agent with a budget ([budgets]: Cloudflare and Pollinations, from app.yml) the text agent
 * ([splitTemplate], `prompts/image/split_image_prompt.md`) writes one prompt within the budget, or
 * splits the content over a numbered poster series of up to [MAX_SERIES_PARTS] prompts, so nothing
 * is cut. Each prompt is one poster, in order.
 */
class ImagePainter(
    private val splitTemplate: String,
    private val budgets: (ApiService) -> Int? = { ImagePromptBudgets().of(it) },
) {
    suspend fun prompts(writer: TextAgent, agent: ImageAgent, brief: ImageBrief, feedback: List<String> = emptyList()): List<String> {
        val full = buildString {
            append(brief.filled())
            if (feedback.isNotEmpty()) {
                append("\n\nA review rejected the last image painted from this prompt. Avoid these problems:\n")
                feedback.forEach { append("- ").append(it).append('\n') }
            }
        }.trimEnd()
        val budget = budgets(agent.service) ?: return listOf(full)
        if (full.length <= budget) return listOf(full)
        val system = splitTemplate.replace("{{budget}}", "%,d".format(Locale.US, budget)).replace("{{parts}}", MAX_SERIES_PARTS.toString())
        val parts = when (val result = writer.generate(system, full)) {
            is ApiResult.Ok -> splitPrompts(result.value)
            is ApiResult.Failed -> emptyList()
        }
        // Without a usable split the template goes as one prompt, cut at a sentence: better a poster than none.
        return parts.take(MAX_SERIES_PARTS).map { shortened(it, budget) }.ifEmpty { listOf(shortened(full, budget)) }
    }
}

/** The prompts in a reply to `split_image_prompt.md`: `{"prompts": [...]}`, or the whole reply as one prompt when it isn't JSON. */
internal fun splitPrompts(reply: String): List<String> {
    val text = unfenced(reply)
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start >= 0 && end > start) {
        val parsed = runCatching { Json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
        val prompts = (parsed?.get("prompts") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } }
        if (prompts != null) return prompts
    }
    return listOfNotNull(text.ifBlank { null })
}

/** The reply without a code fence or quotes around it. */
private fun unfenced(reply: String): String =
    reply.trim().removePrefix("```json").removePrefix("```text").removePrefix("```").removeSuffix("```").trim().removeSurrounding("\"").trim()

/** [prompt] cut to [limit] characters, at the end of a sentence or line where it can be. */
internal fun shortened(prompt: String, limit: Int): String {
    if (prompt.length <= limit) return prompt
    val cut = prompt.substring(0, limit)
    val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf('\n'))
    return (if (end >= limit / 2) cut.substring(0, end + 1) else cut).trimEnd()
}

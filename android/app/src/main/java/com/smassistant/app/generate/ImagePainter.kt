package com.smassistant.app.generate

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageMode
import com.smassistant.app.net.ApiResult

/**
 * Longest prompt the FLUX image agents (Cloudflare, Pollinations) are sent: Cloudflare caps prompts
 * at 2,048 characters and FLUX reads only a few hundred tokens (docs/design/image-templates.md §6).
 */
const val SHORT_IMAGE_PROMPT = 1500

/** Whether image [index] of a submit is AI-painted in [mode]: Both paints images 2 and 4. */
fun paints(mode: ImageMode, index: Int): Boolean = when (mode) {
    ImageMode.DRAWN -> false
    ImageMode.PAINTED -> true
    ImageMode.BOTH -> index % 2 == 1
}

/**
 * Writes the prompt an AI-painted image is made from (docs/design/image-templates.md §6): the
 * filled template in full for Gemini, which reads long prompts, and for the FLUX agents the text
 * agent's condensed version of it ([condenseTemplate], `prompts/condense_image_prompt.md`), at most
 * [SHORT_IMAGE_PROMPT] characters.
 */
class ImagePainter(private val condenseTemplate: String) {
    suspend fun prompt(writer: TextAgent, agent: ImageAgent, brief: ImageBrief, feedback: List<String> = emptyList()): String {
        val full = buildString {
            append(brief.filled())
            if (feedback.isNotEmpty()) {
                append("\n\nA review rejected the last image painted from this prompt. Avoid these problems:\n")
                feedback.forEach { append("- ").append(it).append('\n') }
            }
        }.trimEnd()
        if (agent.service == ApiService.GEMINI) return full
        val condensed = when (val result = writer.generate(condenseTemplate, full)) {
            is ApiResult.Ok -> unfenced(result.value)
            is ApiResult.Failed -> ""
        }
        return shortened(condensed.ifBlank { full })
    }
}

/** The reply without a code fence or quotes around it. */
private fun unfenced(reply: String): String =
    reply.trim().removePrefix("```text").removePrefix("```").removeSuffix("```").trim().removeSurrounding("\"").trim()

/** [prompt] cut to [SHORT_IMAGE_PROMPT] characters, at the end of a sentence or line where it can be. */
internal fun shortened(prompt: String, limit: Int = SHORT_IMAGE_PROMPT): String {
    if (prompt.length <= limit) return prompt
    val cut = prompt.substring(0, limit)
    val end = maxOf(cut.lastIndexOf(". "), cut.lastIndexOf('\n'))
    return (if (end >= limit / 2) cut.substring(0, end + 1) else cut).trimEnd()
}

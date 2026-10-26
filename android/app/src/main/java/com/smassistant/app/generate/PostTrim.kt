package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.markdown.MarkdownToLinkedIn
import com.smassistant.app.net.ApiResult

/**
 * Cuts a post down without an AI agent (docs/requirements/12-post-length.md PL-9, PL-12), so a
 * post can always be made to fit. Works on Markdown or plain text, as both keep blocks apart with
 * a blank line.
 */
object PostCut {
    private val blankLine = Regex("\\n[ \\t]*\\n")
    private val hashtagsOnly = Regex("^#[\\p{L}\\p{N}_]+(\\s+#[\\p{L}\\p{N}_]+)*$")

    /**
     * Shortens [text] until [fits] says yes, cutting the least important parts first:
     * 1. lines from the end of the middle sections, keeping the opening hook, the "📌 In short:"
     *    summary and a trailing hashtag line. A heading left on its own goes too; a long paragraph
     *    is cut at a word break instead when that's enough;
     * 2. then the end of what's left, at a word break, ending with "…", still keeping the hashtags.
     * Returns [text] unchanged when it already fits.
     */
    fun toFit(text: String, fits: (String) -> Boolean): String {
        if (fits(text)) return text
        val blocks = text.trim().split(blankLine).map { Block(it.lines().toMutableList()) }.toMutableList()
        val tags = blocks.lastOrNull()?.takeIf { blocks.size > 1 && hashtagsOnly.matches(it.text.trim()) }
        if (tags != null) blocks.removeAt(blocks.lastIndex)
        fun join() = (blocks + listOfNotNull(tags)).joinToString("\n\n") { it.text }
        // Blocks before this index are the hook and the middle sections; from it on, the summary.
        fun middleEnd() = blocks.indexOfLast { it.text.trimStart().startsWith("📌") }.takeIf { it > 0 } ?: blocks.size

        // 1. Work back through the middle sections until it fits or only the hook and summary are left.
        while (middleEnd() > 1) {
            val at = middleEnd() - 1
            val block = blocks[at]
            val line = block.lines.last()
            if (!block.isAtomic && line.length >= LONG_LINE) {
                val cut = longestCut(line, fits) { kept -> block.lines[block.lines.lastIndex] = kept; join() }
                block.lines[block.lines.lastIndex] = line
                if (cut != null) return cut
            }
            if (block.isAtomic || block.lines.size == 1) {
                blocks.removeAt(at)
            } else {
                block.lines.removeAt(block.lines.lastIndex)
                if (block.isLeftover) blocks.removeAt(at)
            }
            val joined = join()
            if (fits(joined)) return joined
        }

        // 2. Cut the rest at the last whole word that fits, keeping the hashtags.
        val body = blocks.joinToString("\n\n") { it.text }
        fun withTags(kept: String) = listOf(kept, tags?.text.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
        return longestCut(body, fits, minKeep = 1, build = ::withTags) ?: withTags("")
    }

    /**
     * The longest start of [source], cut at a word break and ended with "…", for which [build]
     * gives text that [fits]; null if that keeps fewer than [minKeep] characters.
     */
    private fun longestCut(source: String, fits: (String) -> Boolean, minKeep: Int = MIN_KEPT, build: (String) -> String): String? {
        var low = 0
        var high = source.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fits(build(cutAt(source, mid)))) low = mid else high = mid - 1
        }
        val midWord = low in 1 until source.length && !source[low].isWhitespace()
        val end = if (midWord) (low - 1 downTo 1).firstOrNull { source[it].isWhitespace() } ?: low else low
        return if (end < minKeep) null else build(cutAt(source, end))
    }

    private fun cutAt(source: String, length: Int): String {
        if (length <= 0) return ""
        if (length >= source.length) return source
        // Don't split an emoji's surrogate pair.
        val end = if (Character.isLowSurrogate(source[length])) length - 1 else length
        return source.substring(0, end).trimEnd().trimEnd(',', ';', ':', '-', '—') + "…"
    }

    /** A line at least this long is a paragraph worth cutting part-way rather than dropping. */
    private const val LONG_LINE = 200

    /** Cutting a paragraph part-way must keep at least this much of it, or it goes as a whole. */
    private const val MIN_KEPT = 80

    private class Block(val lines: MutableList<String>) {
        private val original = lines.size
        val text get() = lines.joinToString("\n")

        /** A code block or table can't lose lines without breaking, so it goes as a whole. */
        val isAtomic get() = lines.first().trimStart().startsWith("```") || lines.all { it.trimStart().startsWith("|") }

        /** A section cut down to its heading alone. */
        val isLeftover get() = original > 1 && lines.size == 1
    }
}

/**
 * Trim post (PL-10, PL-11): asks the selected text agent to condense a post to [maxChars] while
 * keeping its key points, and cuts whatever is still over with [PostCut], so the result always fits
 * when the agent answers.
 */
class SmartTrimUseCase(private val agent: () -> TextAgent?) {
    suspend operator fun invoke(text: String, maxChars: Int, fits: (String) -> Boolean): Outcome<String> {
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(SYSTEM, request(text, maxChars))) {
            is ApiResult.Ok -> Outcome.Ok(PostCut.toFit(MarkdownToLinkedIn.convert(result.value), fits))
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }

    private fun request(text: String, maxChars: Int) = listOf(
        "Condense this LinkedIn post to at most $maxChars characters (aim for about ${maxChars * 9 / 10}), counting every letter, space, emoji and line break.",
        "Keep every key point, number and the conclusion; merge related points, drop filler and repetition, and tighten the wording.",
        "Keep the same structure (hook line, emoji section headings, \"📌 In short:\" summary) and plain text: no Markdown, no hashtags.",
        "Reply with the condensed post only.",
        "",
        "Post:",
        text,
    ).joinToString("\n")

    private companion object {
        const val SYSTEM = "You edit LinkedIn posts to fit a character limit without losing their substance."
    }
}

/** The Trim post agent the screens use: the selected text agent, as for generating. */
fun appSmartTrim(context: Context) = SmartTrimUseCase(appAgentSelector(context)::textAgent)

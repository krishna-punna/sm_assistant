package com.smassistant.app.hashtags

/** LinkedIn's limit on post commentary, in characters. The app reads it from app.yml (`linkedin_post.max_characters`); this is the fallback. */
const val LINKEDIN_POST_LIMIT = 3_000

/**
 * Length of [text] as LinkedIn counts it (PL-1): every character, space, line break and emoji.
 * Counted in Unicode code points, so an emoji such as 🔧 is one character, not two UTF-16 units.
 */
fun postLength(text: String): Int = text.codePointCount(0, text.length)

/** LinkedIn suggests 3–5 hashtags per post; above this the app warns but doesn't block. */
const val RECOMMENDED_MAX_HASHTAGS = 5

/** Result of reading the hashtag text box: valid [tags] in the user's order, and entries that were rejected. */
data class ParsedHashtags(val tags: List<String>, val invalid: List<String>) {
    val isValid get() = invalid.isEmpty()
}

/** Post text ready to publish. [overLimitBy] > 0 means the body must be shortened (HT-14). */
data class PostText(val text: String, val addedTags: List<String>, val overLimitBy: Int) {
    val fitsLimit get() = overLimitBy == 0
}

/** Default hashtag rules from docs/requirements/10-default-hashtags.md. */
object Hashtags {
    private val separators = Regex("[\\s,]+")
    private val validTag = Regex("^#[\\p{L}\\p{N}_]+$")
    private val notTagChar = Regex("[^\\p{L}\\p{N}_]")

    /**
     * Splits on spaces, commas and new lines (HT-2), adds a missing `#`, rejects characters
     * LinkedIn doesn't allow, and drops case-insensitive duplicates keeping the first spelling
     * and the user's order (HT-4).
     */
    fun parse(input: String): ParsedHashtags {
        val tags = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        input.split(separators).filter { it.isNotEmpty() }.forEach { raw ->
            val tag = if (raw.startsWith("#")) raw else "#$raw"
            when {
                !validTag.matches(tag) -> invalid += raw
                seen.add(tag.lowercase()) -> tags += tag
            }
        }
        return ParsedHashtags(tags, invalid)
    }

    /** How a saved list is shown in the text box. */
    fun format(tags: List<String>): String = tags.joinToString(" ")

    /**
     * Builds the text to post (HT-10 to HT-14, HT-22): when [enabled], adds the tags that aren't
     * already in [body] after one blank line, separated by spaces. If the body already ends with a
     * line of hashtags (the AI agent's, HT-21), the default tags go first on that same line and
     * duplicates are dropped case-insensitively, so the post has one hashtag line. A blank body
     * (image- or video-only post) gets just the tags. Tags are kept even when that pushes the text
     * over [limit]; [PostText.overLimitBy] says by how much.
     */
    fun appendTo(body: String, tags: List<String>, enabled: Boolean, limit: Int = LINKEDIN_POST_LIMIT): PostText {
        val (rest, trailing) = splitTrailingTags(body)
        val defaults = if (enabled) tags.filterNot { containsTag(rest, it) } else emptyList()
        val toAdd = defaults.filterNot { tag -> trailing.any { it.equals(tag, ignoreCase = true) } }
        val line = dedupe(defaults + trailing)
        val text = when {
            line.isEmpty() -> body
            rest.isBlank() -> format(line)
            else -> rest.trimEnd() + "\n\n" + format(line)
        }
        return PostText(text, toAdd, overLimitBy = (postLength(text) - limit).coerceAtLeast(0))
    }

    /**
     * Splits off the last line of [body] when every word on it is a hashtag, e.g. the AI agent's
     * `#SpringBoot #Java`. Characters LinkedIn doesn't allow are dropped (`#Spring-Boot` →
     * `#SpringBoot`). Returns the body without that line and its tags, or the body and no tags.
     */
    fun splitTrailingTags(body: String): Pair<String, List<String>> {
        val trimmed = body.trimEnd()
        val start = trimmed.lastIndexOf('\n') + 1
        val words = trimmed.substring(start).split(separators).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.any { !it.startsWith("#") }) return body to emptyList()
        val tags = words.map { "#" + it.replace(notTagChar, "") }.filter { it.length > 1 }
        if (tags.isEmpty()) return body to emptyList()
        return trimmed.substring(0, start) to dedupe(tags)
    }

    private fun dedupe(tags: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return tags.filter { seen.add(it.lowercase()) }
    }

    /**
     * Characters the hashtags can take at the end of a post (PL-3): the tags plus the blank line
     * before them, or 0 when they're off or empty. Counts every tag, as the body isn't known yet.
     */
    fun reservedLength(tags: List<String>, enabled: Boolean): Int =
        if (enabled && tags.isNotEmpty()) postLength(format(tags)) + 2 else 0

    /** Whole-tag, case-insensitive match: `#ai` matches `#AI` but not `#AIEngineer`. */
    private fun containsTag(text: String, tag: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}_#])" + Regex.escape(tag) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}

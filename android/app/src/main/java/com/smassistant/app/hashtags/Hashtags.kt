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
     * Builds the text to post (HT-10 to HT-14): when [enabled], appends the tags that aren't
     * already in [body] after one blank line, separated by spaces. A blank body (image- or
     * video-only post) gets just the tags. Tags are kept even when that pushes the text over
     * [limit]; [PostText.overLimitBy] says by how much.
     */
    fun appendTo(body: String, tags: List<String>, enabled: Boolean, limit: Int = LINKEDIN_POST_LIMIT): PostText {
        val toAdd = if (enabled) tags.filterNot { containsTag(body, it) } else emptyList()
        val text = when {
            toAdd.isEmpty() -> body
            body.isBlank() -> format(toAdd)
            else -> body.trimEnd() + "\n\n" + format(toAdd)
        }
        return PostText(text, toAdd, overLimitBy = (postLength(text) - limit).coerceAtLeast(0))
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

package com.smassistant.app.generate

import com.smassistant.app.hashtags.Hashtags
import com.smassistant.app.hashtags.PostText
import com.smassistant.app.markdown.MarkdownToLinkedIn

/**
 * Keeps a generated LinkedIn post within the character limit once the default hashtags are
 * added (docs/requirements/12-post-length.md). Settings are read at call time, so a changed
 * hashtag list applies to the next generation.
 */
class PostLengthGuard(
    private val limit: () -> Int,
    private val tags: () -> List<String>,
    private val tagsEnabled: () -> Boolean,
    val shortenAttempts: Int,
    /** How many hashtags the agent should add for the post's topic; 0 when AI-suggested hashtags are off (HT-21). */
    private val aiHashtags: () -> Int = { 0 },
) {
    /** Characters left for the post body: the limit minus the hashtags and the blank line before them (PL-3). */
    fun budget(): Int = (limit() - Hashtags.reservedLength(tags(), tagsEnabled())).coerceAtLeast(0)

    /**
     * What the agent should aim for: models overshoot, so a little under the budget (PL-4), and
     * further under on each shorten [attempt] after the first generation (PL-6): 90%, 80%, 70%, then 60%.
     */
    fun target(attempt: Int = 0): Int = budget() * (90 - 10 * attempt.coerceAtMost(3)) / 100

    /** [markdown] as it would be posted: plain text with the hashtags that would be added (PL-5). */
    fun measure(markdown: String): PostText =
        Hashtags.appendTo(MarkdownToLinkedIn.convert(markdown), tags(), tagsEnabled(), limit())

    /** Added to the system prompt so the agent writes to the budget from the start (PL-4). */
    fun instructions(): String {
        val reserved = Hashtags.reservedLength(tags(), tagsEnabled())
        val hashtags = if (reserved > 0) {
            " The app adds the user's default hashtags after the post, which take $reserved of LinkedIn's ${limit()} characters, so they are already taken out of this budget."
        } else {
            ""
        }
        return "Length budget: the post must be at most ${budget()} characters, counting every letter, space, " +
            "emoji and line break. Aim for about ${target()} characters.$hashtags" + aiHashtagInstructions()
    }

    /** Asks for a last line of topic hashtags (HT-21); the app merges the defaults into it when posting (HT-22). */
    private fun aiHashtagInstructions(): String {
        val count = aiHashtags()
        if (count <= 0) return ""
        val defaults = if (tagsEnabled() && tags().isNotEmpty()) {
            " The app puts the user's default hashtags (${Hashtags.format(tags())}) on that line too, so don't repeat them."
        } else {
            ""
        }
        return "\n\nHashtags: end the post with one last line of up to $count hashtags that fit this post's topic, " +
            "separated by spaces, for example #SpringBoot #Java. Use only letters, numbers and _ after the #. " +
            "That line counts towards the length budget.$defaults"
    }

    /** True when [markdown] can be posted as it is, hashtags included (PL-5). */
    fun fits(markdown: String): Boolean = measure(markdown).fitsLimit

    /** The follow-up message asking the agent to cut [markdown] down to the budget, stricter on each [attempt] (PL-6). */
    fun shortenRequest(markdown: String, measured: PostText, attempt: Int = 1): String = listOf(
        "This LinkedIn post is too long. As posted, with any default hashtags, it is ${measured.overLimitBy} characters over LinkedIn's ${limit()}-character limit.",
        "Rewrite it to at most ${budget()} characters (aim for about ${target(attempt)}), counting every letter, space, emoji and line break. It must be well under the limit, so cut hard.",
        "Keep the same format: the hook line, the emoji section headings and the \"📌 In short:\" summary. Cut the least important points and tighten the wording rather than dropping the summary.",
        if (aiHashtags() > 0) {
            "Keep the last line of hashtags as it is and add no others. Reply with the shortened post only."
        } else {
            "Do not add hashtags. Reply with the shortened post only."
        },
        "",
        "Post:",
        markdown,
    ).joinToString("\n")
}

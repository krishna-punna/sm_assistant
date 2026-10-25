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
) {
    /** Characters left for the post body: the limit minus the hashtags and the blank line before them (PL-3). */
    fun budget(): Int = (limit() - Hashtags.reservedLength(tags(), tagsEnabled())).coerceAtLeast(0)

    /** What the agent should aim for: models overshoot, so a little under the budget (PL-4). */
    fun target(): Int = budget() * 9 / 10

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
            "emoji and line break. Aim for about ${target()} characters.$hashtags"
    }

    /** The follow-up message asking the agent to cut [markdown] down to the budget (PL-6). */
    fun shortenRequest(markdown: String, measured: PostText): String = listOf(
        "This LinkedIn post is too long. As posted, with any default hashtags, it is ${measured.overLimitBy} characters over LinkedIn's ${limit()}-character limit.",
        "Rewrite it to at most ${budget()} characters (aim for about ${target()}), counting every letter, space, emoji and line break.",
        "Keep the same format: the hook line, the emoji section headings and the \"📌 In short:\" summary. Cut the least important points and tighten the wording rather than dropping the summary.",
        "Do not add hashtags. Reply with the shortened post only.",
        "",
        "Post:",
        markdown,
    ).joinToString("\n")
}

package com.smassistant.app.linkedin

/** About how much of a post LinkedIn's feed shows before "…see more": three lines or ~210 characters. */
const val FEED_FOLD_LINES = 3
const val FEED_FOLD_CHARS = 210

/** A post split where the feed folds it: [shown] above "…see more", [rest] below (empty when it all shows). */
data class FeedFold(val shown: String, val rest: String)

/** Splits [text] at the first of [FEED_FOLD_LINES] lines or [FEED_FOLD_CHARS] characters (counted as LinkedIn does). */
fun feedFold(text: String): FeedFold {
    var lines = 1
    var chars = 0
    var index = 0
    while (index < text.length) {
        if (text[index] == '\n') {
            if (lines == FEED_FOLD_LINES) break
            lines++
        }
        if (chars == FEED_FOLD_CHARS) break
        index += Character.charCount(text.codePointAt(index))
        chars++
    }
    return FeedFold(text.substring(0, index), text.substring(index))
}

private val HASHTAG = Regex("(?<![\\p{L}\\p{N}_#])#[\\p{L}\\p{N}_]+")

/** Where the hashtags are in [text], so the preview can show them as LinkedIn links them. */
fun hashtagRanges(text: String): List<IntRange> = HASHTAG.findAll(text).map { it.range }.toList()

/** The share of the limit after which the count turns amber, so trimming isn't a surprise at the last character. */
const val NEAR_LIMIT_SHARE = 0.9

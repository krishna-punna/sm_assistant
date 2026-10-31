package com.smassistant.app.linkedin

import org.junit.Assert.assertEquals
import org.junit.Test

class PostPreviewTest {
    @Test
    fun `the feed folds after three lines`() {
        assertEquals(FeedFold("a\nb\nc", "\nd\ne"), feedFold("a\nb\nc\nd\ne"))
    }

    @Test
    fun `the feed folds a long first line at 210 characters, counting emoji as one`() {
        val fold = feedFold("🔧" + "x".repeat(300))
        assertEquals(FEED_FOLD_CHARS, fold.shown.codePointCount(0, fold.shown.length))
        assertEquals("🔧" + "x".repeat(300), fold.shown + fold.rest)
    }

    @Test
    fun `a short post shows whole`() {
        assertEquals(FeedFold("Short post", ""), feedFold("Short post"))
    }

    @Test
    fun `hashtags are found but not inside words or urls fragments`() {
        val text = "Kafka #java, C# and #system_design\nsee page#anchor #ünïcode"
        assertEquals(listOf("#java", "#system_design", "#ünïcode"), hashtagRanges(text).map { text.substring(it) })
    }
}

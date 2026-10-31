package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smassistant.app.history.PostMedia
import com.smassistant.app.history.PostSource
import com.smassistant.app.history.PublishedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Improvement #7: the history list, its empty state, Remove and Clear. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PostHistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val posts = listOf(
        PublishedPost("a", 1L, "Kafka Partitions", "Kafka text", PostMedia.IMAGES, 2, "https://x/a", PostSource.SCHEDULED),
        PublishedPost("b", 0L, "Hello", "Hello text", url = "https://x/b"),
    )

    @Test
    fun emptyHistorySaysSo() {
        compose.setContent { PostHistoryContent(emptyList(), {}, {}, {}) }
        compose.onNodeWithTag("noHistory").assertExists()
    }

    @Test
    fun showsEachPostWithHowItWasMade() {
        compose.setContent { PostHistoryContent(posts, {}, {}, {}) }
        compose.onNodeWithText("2 posts on this phone, newest first.").assertExists()
        compose.onNodeWithText("Kafka Partitions").assertExists()
        compose.onNodeWithText("Scheduled · 2 images", substring = true).assertExists()
        compose.onNodeWithText("Manual · Text", substring = true).assertExists()
    }

    @Test
    fun removeAndClear() {
        val removed = mutableListOf<String>()
        var cleared = false
        compose.setContent { PostHistoryContent(posts, { removed += it }, { cleared = true }, {}) }
        compose.onNodeWithTag("removeHistory-b").performScrollTo().performClick()
        assertEquals(listOf("b"), removed)
        compose.onNodeWithTag("clearHistory").performScrollTo().performClick()
        compose.onNodeWithTag("confirmClear").performClick()
        assertTrue(cleared)
    }
}

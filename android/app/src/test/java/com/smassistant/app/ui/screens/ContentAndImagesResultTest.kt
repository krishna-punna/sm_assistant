package com.smassistant.app.ui.screens

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.generate.ContentAndImages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ContentAndImagesResultTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val images = listOf(File("missing-a.png"), File("missing-b.png"))

    private fun show(result: ContentAndImages) = compose.setContent {
        ContentAndImagesResult(
            result = result,
            posting = false,
            onRetryText = { events += "retryText" },
            onRetryImages = { events += "retryImages" },
            onPost = { events += "post:${it.size}" },
            onBackToHome = {},
        )
    }

    @Test
    fun textThenImagesThenPost() {
        show(ContentAndImages(markdown = "System Design: Short URL", media = images))

        val text = compose.onNodeWithText("System Design: Short URL").getUnclippedBoundsInRoot()
        val strip = compose.onNodeWithTag("imageStrip").getUnclippedBoundsInRoot()
        val post = compose.onNodeWithText("Post in LinkedIn").getUnclippedBoundsInRoot()
        assertTrue(text.top < strip.top && strip.top < post.top)

        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf("post:2"), events)
    }

    @Test
    fun failedHalvesOfferTheirOwnRetry() {
        show(ContentAndImages(markdown = "Body", mediaError = "Gemini is rate limiting requests."))
        compose.onNodeWithText("Retry images").performClick()
        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf("retryImages", "post:0"), events)
    }

    @Test
    fun textErrorStillShowsImages() {
        show(ContentAndImages(textError = "No text agent is set up.", media = images))
        compose.onNodeWithText("Retry text").performClick()
        compose.onNodeWithTag("imageStrip").assertExists()
        assertEquals(listOf("retryText"), events)
    }
}

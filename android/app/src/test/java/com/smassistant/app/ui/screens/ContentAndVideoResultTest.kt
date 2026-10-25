package com.smassistant.app.ui.screens

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.generate.ContentAndVideo
import com.smassistant.app.generate.GeneratedVideo
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
class ContentAndVideoResultTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val video = GeneratedVideo(File("video.mp4"), (1..4).map { File("s$it.png") })

    private fun show(result: ContentAndVideo) = compose.setContent {
        ContentAndVideoResult(
            result = result,
            posting = false,
            onRetryText = { events += "retryText" },
            onRetryVideo = { events += "retryVideo" },
            onPost = { events += "post:${it?.name}" },
        )
    }

    @Test
    fun textThenVideoThenPost() {
        show(ContentAndVideo(markdown = "System Design: Short URL", media = video))

        val text = compose.onNodeWithText("System Design: Short URL").getUnclippedBoundsInRoot()
        val player = compose.onNodeWithTag("videoPlayer").getUnclippedBoundsInRoot()
        val post = compose.onNodeWithText("Post in LinkedIn").getUnclippedBoundsInRoot()
        assertTrue(text.top < player.top && player.top < post.top)

        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf("post:video.mp4"), events)
    }

    @Test
    fun failedVideoOffersRetryAndTheTextStillPosts() {
        show(ContentAndVideo(markdown = "Body", mediaError = "Gemini is rate limiting requests."))
        compose.onNodeWithText("Retry video").performClick()
        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf("retryVideo", "post:null"), events)
    }

    @Test
    fun textErrorStillShowsTheVideo() {
        show(ContentAndVideo(textError = "No text agent is set up.", media = video))
        compose.onNodeWithText("Retry text").performClick()
        compose.onNodeWithTag("videoPlayer").assertExists()
        assertEquals(listOf("retryText"), events)
    }
}

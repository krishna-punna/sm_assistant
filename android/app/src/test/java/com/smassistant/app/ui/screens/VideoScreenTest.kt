package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.video.GeneratedVideo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class VideoScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resultShowsThePlayerAndLengthAndPosts() {
        var posts = 0
        val slides = (1..4).map { File("s$it.png") }
        compose.setContent { VideoResult(GeneratedVideo(File("video.mp4"), slides, seconds = 90), posting = false, onPost = { posts++ }) }
        compose.onNodeWithTag("videoPlayer").assertExists()
        compose.onNodeWithText("9:16 · 1080×1920 · 4 slides, 1 min 30 sec").assertExists()
        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(1, posts)
    }

    @Test
    fun resultShowsTheTemplateAndSizeOfALabelledVideo() {
        val slides = (1..4).map { File("s$it.png") }
        compose.setContent { VideoResult(GeneratedVideo(File("vtpl-workflow-vertical-video.mp4"), slides, seconds = 90), posting = false, onPost = {}) }
        compose.onNodeWithText("Workflow or Lifecycle · 9:16 · 1080×1920 · 4 slides, 1 min 30 sec").assertExists()
    }

    @Test
    fun withoutGeminiSetUpOpensAgentSettings() {
        var setUp = 0
        compose.setContent { VideoScreen(onSetUpAgent = { setUp++ }, onSetUpLinkedIn = {}, onBackToHome = {}) }

        // The bundled app.yml has no keys, so Gemini isn't set up.
        compose.onNodeWithTag("chatInput").performTextInput("How a URL shortener works")
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP).assertExists()
        compose.onNodeWithText("Set up an AI agent").performClick()
        assertEquals(1, setUp)
    }
}

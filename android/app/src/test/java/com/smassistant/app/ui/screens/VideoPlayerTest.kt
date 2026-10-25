package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.smassistant.app.ui.components.VideoPlayer
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class VideoPlayerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsThePlayerForAFile() {
        compose.setContent { VideoPlayer(File("sample.mp4")) }
        compose.onNodeWithTag("videoPlayer").assertExists()
    }
}

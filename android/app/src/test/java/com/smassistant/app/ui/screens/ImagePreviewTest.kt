package com.smassistant.app.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.smassistant.app.ui.components.ImagePreview
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** IM-T3: the preview swipes between images and its actions act on the image shown. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ImagePreviewTest {
    @get:Rule val compose = createComposeRule()

    private val images = List(3) { File("missing-$it.png") }

    @Test
    fun swipingMovesToTheNextImageAndActionsFollow() {
        val posted = mutableListOf<Int>()
        compose.setContent {
            ImagePreview(images, startIndex = 0, onDismiss = {}) { current ->
                Button(onClick = { posted += current }) { Text("Post in LinkedIn") }
            }
        }
        compose.onNodeWithText("Image 1 of 3").assertExists()

        compose.onNodeWithTag("imagePager").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Image 2 of 3").assertExists()
        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf(1), posted)
    }

    @Test
    fun opensOnTheTappedImage() {
        compose.setContent { ImagePreview(images, startIndex = 2, onDismiss = {}) {} }
        compose.onNodeWithText("Image 3 of 3").assertExists()
    }
}

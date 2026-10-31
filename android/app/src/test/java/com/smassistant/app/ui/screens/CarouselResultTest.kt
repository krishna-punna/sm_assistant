package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SC-I5: a carousel shows one page at a time and Post in LinkedIn posts every page, in order. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CarouselResultTest {
    @get:Rule val compose = createComposeRule()

    private val pages = (1..3).map { File("carousel-7-p0$it-page.png") }

    @Test
    fun swipesThroughThePagesAndPostsThemAll() {
        val posted = mutableListOf<List<File>>()
        compose.setContent { ImageResult(pages, posting = false, onPost = { posted += it }, onBackToHome = {}) }

        compose.onNodeWithText("Carousel · page 1 of 3").assertExists()
        compose.onNodeWithTag("imageStrip").assertDoesNotExist()
        compose.onNodeWithTag("carouselPager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Carousel · page 2 of 3").assertExists()

        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf(pages), posted)
    }

    @Test
    fun separateImagesKeepTheStrip() {
        compose.setContent { ImageResult(listOf(File("a.png")), posting = false, onPost = {}, onBackToHome = {}) }
        compose.onNodeWithTag("imageStrip").assertExists()
        compose.onNodeWithTag("carouselPager").assertDoesNotExist()
    }
}

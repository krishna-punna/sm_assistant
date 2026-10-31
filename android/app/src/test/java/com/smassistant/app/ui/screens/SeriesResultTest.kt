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

/** AC-7: a poster series shows one poster at a time and Post in LinkedIn posts every part, in order. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SeriesResultTest {
    @get:Rule val compose = createComposeRule()

    private val pages = (1..3).map { File("series-7-p0$it-poster.png") }

    @Test
    fun swipesThroughThePostersAndPostsThemAll() {
        val posted = mutableListOf<List<File>>()
        compose.setContent { ImageResult(pages, posting = false, onPost = { posted += it }, onBackToHome = {}) }

        compose.onNodeWithText("Series · part 1 of 3").assertExists()
        compose.onNodeWithTag("imageStrip").assertDoesNotExist()
        compose.onNodeWithTag("seriesPager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Series · part 2 of 3").assertExists()

        compose.onNodeWithText("Post in LinkedIn").performClick()
        assertEquals(listOf(pages), posted)
    }

    @Test
    fun separateImagesKeepTheStrip() {
        compose.setContent { ImageResult(listOf(File("a.png")), posting = false, onPost = {}, onBackToHome = {}) }
        compose.onNodeWithTag("imageStrip").assertExists()
        compose.onNodeWithTag("seriesPager").assertDoesNotExist()
    }
}

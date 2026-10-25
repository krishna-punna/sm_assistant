package com.smassistant.app.ui.screens

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.smassistant.app.ui.components.SmScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SmScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsTitleChipAndBackToHome() {
        var back = 0
        compose.setContent { SmScreen(sectionTitle = "LinkedIn", onBackToHome = { back++ }) { Text("Body") } }
        compose.onNodeWithText("SM Assistant").assertExists()
        compose.onNodeWithText("LinkedIn").assertExists()
        compose.onNodeWithText("Back to Home").performClick()
        assertEquals(1, back)
    }

    @Test
    fun homeHasNoFooter() {
        compose.setContent { SmScreen(sectionTitle = null, onBackToHome = null) { Text("Body") } }
        compose.onAllNodesWithText("Back to Home").assertCountEquals(0)
    }

    @Test
    fun longBodyScrolls() {
        compose.setContent {
            SmScreen(sectionTitle = "List", onBackToHome = {}) { repeat(60) { Text("Row $it") } }
        }
        compose.onNodeWithTag("screenBody").performScrollToNode(hasText("Row 59"))
        compose.onNodeWithText("Row 59").assertIsDisplayed()
    }
}

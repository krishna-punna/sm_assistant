package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smassistant.app.MainActivity
import com.smassistant.app.navigation.Feature
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NAV-T2: rotating a feature screen keeps the screen, the conversation and what was being typed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class RotationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()

    @Test
    fun rotationKeepsTheConversationAndTypedInput() {
        click("SM Assistant")
        click(Feature.TECHNICAL_CONTENT.title)
        // No agent has a key in tests, so the submit ends in the "set up an agent" card.
        compose.onNodeWithTag("chatInput").performTextInput("Design a URL shortener")
        click("Submit")
        compose.onNodeWithText("Set up an AI agent").assertExists()
        compose.onNodeWithTag("chatInput").performTextInput("Half typed")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText(Feature.TECHNICAL_CONTENT.title).assertExists()
        compose.onNodeWithText("Design a URL shortener").assertExists()
        compose.onNodeWithText("Set up an AI agent").assertExists()
        compose.onNodeWithTag("chatInput").assertTextEquals("Half typed", includeEditableText = true)
    }
}

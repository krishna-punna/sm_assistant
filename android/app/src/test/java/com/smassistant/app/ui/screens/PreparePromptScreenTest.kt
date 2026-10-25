package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PreparePromptScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun withoutAnAgentTheTopicStaysAndSetUpOpensAgentSettings() {
        var setUp = 0
        compose.setContent { PreparePromptScreen(onSetUpAgent = { setUp++ }, onBackToHome = {}) }

        // The bundled app.yml has no keys, so no agent is set up.
        compose.onNodeWithTag("chatInput").performTextInput("Design a system for Short URL")
        compose.onNodeWithText("Submit").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Design a system for Short URL").assertExists()
        compose.onNodeWithText("Set up an AI agent").performClick()
        assertEquals(1, setUp)
        compose.onAllNodesWithText("Post in LinkedIn").assertCountEquals(0)
    }
}

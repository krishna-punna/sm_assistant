package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smassistant.app.navigation.AiAgent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AiAgentListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun eachButtonOpensItsOwnAgent() {
        val opened = mutableListOf<AiAgent>()
        compose.setContent { AiAgentListScreen(onAgentSelected = { opened += it }, onBackToHome = {}) }

        AiAgent.entries.forEach { compose.onNodeWithText("${it.displayName} AI Agent").performScrollTo().performClick() }

        assertEquals(AiAgent.entries.toList(), opened)
        assertEquals(listOf("Gemini", "Groq", "Cloudflare", "Pollinations.AI"), opened.map { it.displayName })
    }

    @Test
    fun settingsScreenTitleNamesTheAgent() {
        compose.setContent { AgentSettingsScreen(agent = AiAgent.GEMINI, onBackToHome = {}) }

        compose.onNodeWithText("Gemini AI Agent").assertExists()
    }
}

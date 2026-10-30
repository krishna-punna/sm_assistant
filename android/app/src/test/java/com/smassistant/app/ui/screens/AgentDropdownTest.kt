package com.smassistant.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.data.ApiService
import com.smassistant.app.ui.components.AgentDropdown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AgentDropdownTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun listsTheSetUpAgentsAndSwitchesToThePickedOne() {
        var picked: ApiService? = null
        compose.setContent {
            var selected by remember { mutableStateOf(ApiService.GEMINI) }
            AgentDropdown(listOf(ApiService.GEMINI, ApiService.GROQ), selected) { picked = it; selected = it }
        }
        compose.onNodeWithTag("agentDropdown").assertTextEquals("Gemini ▾").performClick()
        compose.onNodeWithText("✓ Gemini").assertExists()
        compose.onNodeWithText("Cloudflare").assertDoesNotExist()
        compose.onNodeWithText("Groq").performClick()

        assertEquals(ApiService.GROQ, picked)
        compose.onNodeWithTag("agentDropdown").assertTextEquals("Groq ▾")
        compose.onNodeWithText("✓ Gemini").assertDoesNotExist()
    }
}

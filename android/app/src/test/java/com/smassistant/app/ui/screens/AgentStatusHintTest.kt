package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.ui.components.AgentStatusHint
import com.smassistant.app.ui.components.agentStatusWarning
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** GEN-6 (improvement #9): a warning by the agent picker when its last check failed. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class AgentStatusHintTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun warnsOnlyWhenTheLastCheckFailed() {
        assertNull(agentStatusWarning(ApiService.GEMINI, null))
        assertNull(agentStatusWarning(ApiService.GEMINI, ConnectionStatus.Connected))
        assertTrue(agentStatusWarning(ApiService.GEMINI, ConnectionStatus.InvalidKey)!!.startsWith("Gemini rejected its key"))
        assertTrue(agentStatusWarning(ApiService.GROQ, ConnectionStatus.Error(500))!!.contains("HTTP 500"))
        assertTrue(agentStatusWarning(ApiService.GROQ, ConnectionStatus.Unreachable("timeout"))!!.contains("timeout"))
    }

    @Test
    fun showsTheWarningUnderThePicker() {
        compose.setContent { AgentStatusHint(ApiService.GROQ, ConnectionStatus.InvalidKey) }
        compose.onNodeWithTag("agentWarning-GROQ").assertExists()
    }

    @Test
    fun showsNothingWhenConnected() {
        compose.setContent { AgentStatusHint(ApiService.GROQ, ConnectionStatus.Connected) }
        compose.onNodeWithTag("agentWarning-GROQ").assertDoesNotExist()
    }
}

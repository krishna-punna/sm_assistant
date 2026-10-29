package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ConnectionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ApiStatusScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsEachStatusAndEnablesTestAgainWhenDone() {
        compose.setContent {
            ApiStatusContent(
                statuses = mapOf(
                    ApiService.LINKEDIN to ConnectionStatus.NoPermission,
                    ApiService.CLOUDFLARE to ConnectionStatus.Connected,
                    ApiService.GEMINI to ConnectionStatus.NotConfigured,
                    ApiService.GROQ to ConnectionStatus.Error(503),
                ),
                onTestAgain = {},
                onBackToHome = {},
            )
        }

        compose.onNodeWithText("No access").assertExists()
        compose.onNodeWithText("Connected").assertExists()
        compose.onNodeWithText("Not set up").assertExists()
        compose.onNodeWithText("Unexpected response (HTTP 503)").assertExists()
        compose.onNodeWithText("Test again").assertIsEnabled()
    }

    @Test
    fun disablesTestAgainWhileChecking() {
        compose.setContent {
            ApiStatusContent(
                statuses = ApiService.entries.associateWith { ConnectionStatus.Checking },
                onTestAgain = {},
                onBackToHome = {},
            )
        }
        compose.onNodeWithText("Test again").assertIsNotEnabled()
    }
}

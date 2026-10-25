package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.settings.ApiSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ApiSettingsFieldsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsValuesAndErrors() {
        var saves = 0
        compose.setContent {
            Column {
                ApiSettingsFields(
                    serviceName = "Groq",
                    state = ApiSettingsUiState(
                        api = "https://api.groq.com/openai/v1/chat/completions",
                        key = "gsk-secret",
                        keyError = "Enter your Groq API key",
                    ),
                    onApiChange = {},
                    onKeyChange = {},
                    onSave = { saves++ },
                )
            }
        }

        compose.onNodeWithText("https://api.groq.com/openai/v1/chat/completions").assertExists()
        compose.onNodeWithTag("keyField").assertExists()
        compose.onNodeWithText("Enter your Groq API key").assertExists()
        compose.onNodeWithText("SAVE").performClick()
        assertEquals(1, saves)
    }
}

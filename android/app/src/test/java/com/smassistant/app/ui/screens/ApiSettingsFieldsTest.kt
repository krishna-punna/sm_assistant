package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ModelLists
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

    @Test
    fun agentsPickAModelFromTheFetchedListAndCanRefreshIt() {
        var picked: String? = null
        var refreshes = 0
        compose.setContent {
            Column {
                ApiSettingsFields(
                    serviceName = "Gemini",
                    state = ApiSettingsUiState(
                        key = "AIza-1",
                        showModels = true,
                        showImageModel = true,
                        model = "gemini-2.5-flash",
                        imageModel = "gemini-2.5-flash-image",
                        models = ModelLists(
                            text = listOf(AiModel("gemini-2.5-flash", "Gemini 2.5 Flash"), AiModel("gemini-3-pro", "Gemini 3 Pro")),
                            image = listOf(AiModel("gemini-2.5-flash-image", "Nano Banana")),
                        ),
                    ),
                    onApiChange = {},
                    onKeyChange = {},
                    onSave = {},
                    onModelChange = { picked = it },
                    onRefreshModels = { refreshes++ },
                )
            }
        }

        compose.onNodeWithTag("modelStatus").assertTextEquals("2 models available")
        compose.onNodeWithTag("imageModelPicker").assertTextEquals("Nano Banana ▾")
        compose.onNodeWithTag("modelPicker").assertTextEquals("Gemini 2.5 Flash ▾").performClick()
        compose.onNodeWithText("Gemini 3 Pro").performClick()
        assertEquals("gemini-3-pro", picked)

        compose.onNodeWithText("Refresh models").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun linkedInHasNoModelPicker() {
        compose.setContent {
            Column {
                ApiSettingsFields("LinkedIn", ApiSettingsUiState(key = "token"), onApiChange = {}, onKeyChange = {}, onSave = {})
            }
        }
        compose.onNodeWithTag("modelPicker").assertDoesNotExist()
    }

    @Test
    fun cloudflareShowsTheAccountIdField() {
        var typed = ""
        compose.setContent {
            Column {
                ApiSettingsFields(
                    serviceName = "Cloudflare",
                    state = ApiSettingsUiState(
                        api = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai",
                        showAccountId = true,
                        accountIdError = "Enter your Cloudflare account ID",
                    ),
                    onApiChange = {},
                    onKeyChange = {},
                    onSave = {},
                    onAccountIdChange = { typed = it },
                )
            }
        }

        compose.onNodeWithTag("accountIdField").performTextInput("abc")
        compose.onNodeWithText("Enter your Cloudflare account ID").assertExists()
        assertEquals("abc", typed)
    }
}

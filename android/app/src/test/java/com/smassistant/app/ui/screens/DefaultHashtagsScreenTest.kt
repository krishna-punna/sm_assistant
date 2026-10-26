package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.hashtags.DefaultHashtagsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Robolectric's default screen is small enough to clip the toggle; use a typical phone.
@Config(qualifiers = "w411dp-h891dp")
class DefaultHashtagsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(state: DefaultHashtagsUiState) = compose.setContent {
        DefaultHashtagsContent(
            state = state,
            onTextChange = { calls += "text:$it" },
            onSave = { calls += "save" },
            onClear = { calls += "clear" },
            onEnabledChange = { calls += "toggle:$it" },
            onAiEnabledChange = { calls += "ai:$it" },
            onBackToHome = { calls += "home" },
        )
    }

    @Test
    fun showsSavedTagsAndToggle() {
        show(DefaultHashtagsUiState(text = "#JavaDeveloper #AIEngineer", enabled = true))

        compose.onNodeWithText("Default Hash Tags#").assertExists()
        compose.onNodeWithText("#JavaDeveloper #AIEngineer").assertExists()
        compose.onNodeWithTag("hashtagToggle").assertIsOn()
        compose.onNodeWithText("AI-suggested hashtags").assertExists()
        compose.onNodeWithTag("aiHashtagToggle").assertIsOff()
    }

    @Test
    fun aiToggleShowsStateAndCallsBack() {
        show(DefaultHashtagsUiState(aiEnabled = true))
        compose.onNodeWithTag("aiHashtagToggle").assertIsOn()

        compose.onNodeWithTag("aiHashtagToggle").performClick()
        assertEquals(listOf("ai:false"), calls)
    }

    @Test
    fun showsErrorAndWarning() {
        show(DefaultHashtagsUiState(text = "#C++", error = "Not allowed: #C++.", warning = "Too many"))

        compose.onNodeWithText("Not allowed: #C++.").assertExists()
        compose.onNodeWithText("Too many").assertExists()
        compose.onNodeWithTag("hashtagToggle").assertIsOff()
    }

    @Test
    fun buttonsAndToggleCallBack() {
        show(DefaultHashtagsUiState())

        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithTag("hashtagToggle").performClick()
        compose.onNodeWithText("Back to Home").performClick()

        assertEquals(listOf("save", "clear", "toggle:true", "home"), calls)
    }
}

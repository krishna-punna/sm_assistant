package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.data.ApiService
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DefaultTextAgentChipsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsSelectionAndReportsClicks() {
        val picked = mutableListOf<ApiService>()
        compose.setContent { Column { DefaultTextAgentChips(ApiService.GEMINI) { picked += it } } }

        compose.onNodeWithText("Gemini").assertIsSelected()
        compose.onNodeWithText("Cloudflare").assertIsNotSelected()
        compose.onNodeWithText("Groq").performClick()
        assertEquals(listOf(ApiService.GROQ), picked)
    }
}

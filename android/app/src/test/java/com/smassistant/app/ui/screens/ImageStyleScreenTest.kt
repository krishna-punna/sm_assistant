package com.smassistant.app.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.generate.ImageStyleUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ImageStyleScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    @Test
    fun showsTheStyleAndPassesEveryChangeOn() {
        compose.setContent {
            ImageStyleContent(
                state = ImageStyleUiState(ImageStyle(audience = "developers", brandName = "SM")),
                actions = ImageStyleActions(
                    onAudience = { calls += "audience:$it" },
                    onSeries = { calls += "series:$it" },
                    onMode = { calls += "mode:$it" },
                    onSave = { calls += "save" },
                ),
                onBackToHome = {},
            )
        }
        compose.onNodeWithText("Image Style").assertExists()
        compose.onNodeWithText("developers").assertExists()
        compose.onNodeWithText("SM").assertExists()
        compose.onNodeWithTag("styleSeries").assertIsOff()
        compose.onNodeWithTag("styleMode").assertTextEquals("App-drawn (default) ▾")
        compose.onNodeWithTag("styleLayout").assertTextEquals("Auto: one poster ▾")

        compose.onNodeWithTag("styleAudience").performTextReplacement("architects")
        compose.onNodeWithTag("styleSeries").performScrollTo().performClick()
        compose.onNodeWithTag("styleMode").performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("App-drawn")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(listOf("audience:architects", "series:true", "mode:${ImageMode.DRAWN}", "save"), calls)
    }
}

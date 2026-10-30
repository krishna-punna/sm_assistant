package com.smassistant.app.ui.screens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.data.ImageFormat
import com.smassistant.app.ui.components.ImageStrip
import com.smassistant.app.ui.components.ImageTemplatePickers
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ImageTemplatePickersTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun listsEveryTemplateAndFormatAndSavesThePicks() {
        compose.setContent { ImageTemplatePickers() }

        compose.onNodeWithTag("imageTemplateDropdown").assertTextEquals("Auto (AI picks) ▾").performClick()
        assertShown("✓ Auto (AI picks)")
        assertShown("Universal Master")
        assertShown("Concept Explainer")
        assertShown("Architecture Diagram")
        assertShown("Comparison")
        assertShown("Workflow")
        assertShown("Code Visual")
        assertShown("Checklist")
        assertShown("Data and Metrics")
        assertShown("Learning Visual")
        assertShown("Series Style")
        pick("Workflow")
        compose.onNodeWithTag("imageTemplateDropdown").assertTextEquals("Workflow ▾")

        compose.onNodeWithTag("imageFormatDropdown").assertTextEquals("Auto ▾").performClick()
        listOf("Portrait 2:3", "Square 1:1").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        pick("Landscape 3:2")

        val preferences = AgentPreferences(context)
        assertEquals("workflow", preferences.imageTemplate)
        assertEquals(ImageFormat.LANDSCAPE, preferences.imageFormat)
    }

    private fun assertShown(text: String) {
        assertTrue(text, compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    /**
     * Runs the menu item's click action directly: a long menu can run past the test window, where a
     * touch on the item lands outside the popup.
     */
    private fun pick(text: String) {
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun theStripShowsEachImagesTemplate() {
        compose.setContent { ImageStrip(listOf(File("a.png"), File("b.png")), setOf(0, 1), {}, {}, labels = listOf("Workflow · 2:3", null)) }
        // The label sits inside the clickable thumbnail, which merges its text away.
        compose.onNodeWithTag("imageLabel0", useUnmergedTree = true).assertTextEquals("Workflow · 2:3")
        compose.onNodeWithTag("imageLabel1", useUnmergedTree = true).assertDoesNotExist()
    }
}

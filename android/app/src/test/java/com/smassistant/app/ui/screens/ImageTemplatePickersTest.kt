package com.smassistant.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.agents.AgentPreferences
import com.smassistant.app.data.ImageFormat
import com.smassistant.app.ui.components.ImageStrip
import com.smassistant.app.ui.components.ImageTemplatePickers
import java.io.File
import org.junit.Assert.assertEquals
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
        listOf("Universal Master", "Concept Explainer", "Architecture Diagram", "Comparison", "Code Visual", "Checklist", "Data and Metrics", "Learning Visual", "Series Style")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Workflow").performClick()
        compose.onNodeWithTag("imageTemplateDropdown").assertTextEquals("Workflow ▾")

        compose.onNodeWithTag("imageFormatDropdown").assertTextEquals("Auto ▾").performClick()
        listOf("Portrait 2:3", "Square 1:1").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Landscape 3:2").performClick()

        val preferences = AgentPreferences(context)
        assertEquals("workflow", preferences.imageTemplate)
        assertEquals(ImageFormat.LANDSCAPE, preferences.imageFormat)
    }

    @Test
    fun theStripShowsEachImagesTemplate() {
        compose.setContent { ImageStrip(listOf(File("a.png"), File("b.png")), setOf(0, 1), {}, {}, labels = listOf("Workflow · 2:3", null)) }
        compose.onNodeWithTag("imageLabel0").assertTextEquals("Workflow · 2:3")
        compose.onNodeWithTag("imageLabel1").assertDoesNotExist()
    }
}

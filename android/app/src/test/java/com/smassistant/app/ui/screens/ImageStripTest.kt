package com.smassistant.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.ui.components.ImageStrip
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ImageStripTest {
    @get:Rule val compose = createComposeRule()

    private val images = List(3) { File("missing-$it.png") }

    @Test
    fun countAndSelectionFollowTheCheckboxes() {
        compose.setContent {
            var selected by remember { mutableStateOf(images.indices.toSet()) }
            ImageStrip(images, selected, onToggle = { i -> selected = if (i in selected) selected - i else selected + i }, onOpen = {})
        }

        compose.onNodeWithText("3 images · 3 selected").assertExists()
        compose.onNodeWithTag("select1").assertIsOn().performClick()
        compose.onNodeWithTag("select1").assertIsOff()
        compose.onNodeWithText("3 images · 2 selected").assertExists()
    }

    @Test
    fun tappingAnImageOpensItsPreview() {
        var opened = -1
        compose.setContent { ImageStrip(images, setOf(0), onToggle = {}, onOpen = { opened = it }) }
        compose.onNodeWithContentDescription("Image 3").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun checkboxesAndThumbnailsAreLabelledForTalkBack() {
        compose.setContent { ImageStrip(images, setOf(0), onToggle = {}, onOpen = {}) }
        compose.onNodeWithTag("select0").assert(hasContentDescription("Post image 1"))
        compose.onNodeWithContentDescription("Image 1")
            .assert(SemanticsMatcher("has Open preview label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open preview" })
    }
}

package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.drafts.Draft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Improvement #8: the Drafts list, Edit & post, and Delete with a confirmation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class DraftsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val draft = Draft("a", 1L, "Kafka Partitions", "Kafka text")

    @Test
    fun emptyListSaysHowToSave() {
        compose.setContent { DraftsContent(emptyList(), {}, {}, {}) }
        compose.onNodeWithTag("noDrafts").assertExists()
    }

    @Test
    fun openAndDelete() {
        val opened = mutableListOf<Draft>()
        val deleted = mutableListOf<String>()
        compose.setContent { DraftsContent(listOf(draft), { opened += it }, { deleted += it }, {}) }
        compose.onNodeWithText("Kafka Partitions").assertExists()
        compose.onNodeWithText("Text", substring = true).assertExists()

        compose.onNodeWithTag("openDraft-a").performClick()
        assertEquals(listOf(draft), opened)

        compose.onNodeWithTag("deleteDraft-a").performClick()
        compose.onNodeWithTag("confirmDeleteDraft").performClick()
        assertEquals(listOf("a"), deleted)
    }
}

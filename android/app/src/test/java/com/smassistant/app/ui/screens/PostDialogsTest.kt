package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.ui.components.PostDialogs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PostDialogsTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: PostState) = compose.setContent {
        PostDialogs(
            state = state,
            onTextChange = { events += "text" },
            onConfirm = { events += "confirm" },
            onDismiss = { events += "dismiss" },
            onOpenSettings = { events += "settings" },
        )
    }

    @Test
    fun confirmShowsAddedHashtagsAndCount() {
        show(PostState.Confirm("Hello\n\n#java", listOf("#java")))
        compose.onNodeWithText("Default hashtags added: #java").assertExists()
        compose.onNodeWithText("12 / 3000 characters").assertExists()
        compose.onNodeWithText("Post").assertIsEnabled().performClick()
        assertEquals(listOf("confirm"), events)
    }

    @Test
    fun overLimitDisablesPost() {
        show(PostState.Confirm("a".repeat(3_005), emptyList()))
        compose.onNodeWithText("5 characters over LinkedIn's 3000 limit. Shorten the text to post.").assertExists()
        compose.onNodeWithText("Post").assertIsNotEnabled()
    }

    @Test
    fun failureCanOpenSettings() {
        show(PostState.Failed("LinkedIn token expired or invalid.", openSettings = true))
        compose.onNodeWithText("LinkedIn settings").performClick()
        assertEquals(listOf("dismiss", "settings"), events)
    }
}

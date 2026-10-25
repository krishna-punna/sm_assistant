package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.ui.components.ConfirmPostContent
import com.smassistant.app.ui.components.PostDialogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // The confirm body is tested outside its AlertDialog: a text field inside a dialog window
    // never lets Robolectric go idle (AppNotIdleException, then OutOfMemoryError).
    @Test
    fun confirmShowsAddedHashtagsAndCount() {
        compose.setContent { ConfirmPostContent(PostState.Confirm("Hello\n\n#java", listOf("#java"))) { events += "text" } }
        compose.onNodeWithText("Default hashtags added: #java").assertExists()
        compose.onNodeWithText("12 / 3000 characters").assertExists()
    }

    @Test
    fun overLimitShowsHowMuchToCut() {
        val state = PostState.Confirm("a".repeat(3_005), emptyList())
        compose.setContent { ConfirmPostContent(state) { events += "text" } }
        compose.onNodeWithText("5 characters over LinkedIn's 3000 limit. Shorten the text to post.").assertExists()
        assertFalse(state.canPost)
    }

    @Test
    fun failureCanOpenSettings() {
        show(PostState.Failed("LinkedIn token expired or invalid.", openSettings = true))
        compose.onNodeWithText("LinkedIn settings").performClick()
        assertEquals(listOf("dismiss", "settings"), events)
    }
}

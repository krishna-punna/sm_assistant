package com.smassistant.app.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.ui.components.ConfirmPostContent
import com.smassistant.app.ui.components.PostDialogs
import java.io.File
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
        compose.onNodeWithText("Default hashtags added at the end: #java (5 characters, counted above)").assertExists()
        compose.onNodeWithText("12 / 3000 characters").assertExists()
    }

    @Test
    fun overLimitShowsHowMuchToCut() {
        val state = PostState.Confirm("a".repeat(3_005), emptyList())
        compose.setContent { ConfirmPostContent(state) { events += "text" } }
        compose.onNodeWithText("5 characters over LinkedIn's 3000 limit. Trim or edit the text to post.").assertExists()
        assertFalse(state.canPost)
    }

    @Test
    fun trimPostOffersAiAndCutToFit() {
        val state = PostState.Confirm("a".repeat(3_005), emptyList())
        compose.setContent {
            ConfirmPostContent(state, onTrimWithAi = { events += "ai" }, onCutToFit = { events += "cut" }) { events += "text" }
        }
        compose.onNodeWithText("✂ Trim with AI").performClick()
        compose.onNodeWithText("Cut to fit").performClick()
        assertEquals(listOf("ai", "cut"), events)
    }

    @Test
    fun cutToFitIsOffOnlyWhenThePostFits() {
        compose.setContent { ConfirmPostContent(PostState.Confirm("Hello", emptyList())) { events += "text" } }
        compose.onNodeWithText("Cut to fit").assertIsNotEnabled()
        compose.onNodeWithText("✂ Trim with AI").assertDoesNotExist()
    }

    @Test
    fun videoShowsAsOneAttachment() {
        compose.setContent { ConfirmPostContent(PostState.Confirm("", emptyList(), listOf(File("video.mp4")))) { events += "text" } }
        compose.onNodeWithText("1 video attached").assertExists()
    }

    @Test
    fun failureCanOpenSettings() {
        show(PostState.Failed("LinkedIn token expired or invalid.", openSettings = true))
        compose.onNodeWithText("LinkedIn settings").performClick()
        assertEquals(listOf("dismiss", "settings"), events)
    }

    @Test
    fun countWarnsNearTheLimit() {
        compose.setContent { ConfirmPostContent(PostState.Confirm("a".repeat(2_800), emptyList())) { events += "text" } }
        compose.onNodeWithText("2800 / 3000 characters · 200 left").assertExists()
    }

    @Test
    fun previewShowsWhereTheFeedFolds() {
        val text = "Hook line\nSecond line\nThird line\nFourth line #java"
        compose.setContent { ConfirmPostContent(PostState.Confirm(text, listOf("#java"))) { events += "text" } }
        compose.onNodeWithTag("previewToggle").performClick()
        compose.onNodeWithTag("feedPreview").assertExists()
        compose.onNodeWithTag("feedFold").assertExists()
        compose.onNodeWithTag("postText").assertDoesNotExist()
        compose.onNodeWithTag("previewToggle").performClick()
        compose.onNodeWithTag("postText").assertExists()
    }
}

package com.smassistant.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.chat.ChatItem
import com.smassistant.app.chat.ChatUiState
import com.smassistant.app.ui.components.PromptCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ChatScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: ChatUiState<String>) = compose.setContent {
        ChatScreen(
            title = "Generate Technical Content",
            state = state,
            placeholder = "Paste your prompt",
            onInputChange = { events += "input:$it" },
            onSubmit = { events += "submit" },
            onRetry = { events += "retry:${it.id}" },
            onCancel = { events += "cancel" },
            onSetUpAgent = { events += "setup" },
            onBackToHome = { events += "home" },
            inputContent = { Text("You: $it") },
            resultContent = { Text("Result: $it") },
        )
    }

    @Test
    fun rendersEveryItemKind() {
        show(
            ChatUiState(
                items = listOf(
                    ChatItem.Input(0, "first"),
                    ChatItem.Result(1, 0, "done"),
                    ChatItem.Input(2, "second"),
                    ChatItem.Failure(3, 2, "Groq returned an error (HTTP 500)"),
                ),
            )
        )
        compose.onNodeWithText("Generate Technical Content").assertExists()
        compose.onNodeWithText("You: first").assertExists()
        compose.onNodeWithText("Result: done").assertExists()
        compose.onNodeWithText("Groq returned an error (HTTP 500)").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry:3"), events)
    }

    @Test
    fun loadingShowsCancelAndDisablesSubmit() {
        show(ChatUiState(items = listOf(ChatItem.Input(0, "x"), ChatItem.Loading(1, 0)), input = "more"))
        compose.onNodeWithText("Generating…").assertExists()
        compose.onNodeWithText("Submit").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun noAgentOffersSetup() {
        show(ChatUiState(items = listOf(ChatItem.Input(0, "x"), ChatItem.NoAgent(1, 0))))
        compose.onNodeWithText("Set up an AI agent").performClick()
        assertEquals(listOf("setup"), events)
    }

    @Test
    fun submitNeedsText() {
        show(ChatUiState(input = ""))
        compose.onNodeWithText("Submit").assertIsNotEnabled()
        compose.onNodeWithText("Results will appear here.").assertExists()
    }

    @Test
    fun submitEnabledWithText() {
        show(ChatUiState(input = "topic"))
        compose.onNodeWithText("Submit").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Back to Home").performClick()
        assertEquals(listOf("submit", "home"), events)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PromptCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun copyPutsFullTextOnClipboardAndExpandShowsIt() {
        val text = "Act as a Senior Principal Software Engineer. ".repeat(30)
        compose.setContent { PromptCard("URL Shortener System Design Prompt", text) }

        compose.onNodeWithText("URL Shortener System Design Prompt").assertExists()
        compose.onNodeWithText("Copy").performClick()
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(text, clipboard.primaryClip?.getItemAt(0)?.text.toString())

        compose.onNodeWithText("Expand").performClick()
        compose.onNodeWithTag("expandedPrompt").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("expandedPrompt").assertDoesNotExist()
    }
}

package com.smassistant.app.guide

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.smassistant.app.chat.ChatItem
import com.smassistant.app.chat.ChatUiState
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ConnectionStatus
import com.smassistant.app.hashtags.DefaultHashtagsUiState
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.navigation.Feature
import com.smassistant.app.settings.ApiSettingsUiState
import com.smassistant.app.ui.components.ConfirmPostContent
import com.smassistant.app.ui.components.MarkdownText
import com.smassistant.app.ui.components.PostInLinkedInButton
import com.smassistant.app.ui.components.PromptCard
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.components.UserBubble
import com.smassistant.app.ui.screens.AiAgentListScreen
import com.smassistant.app.ui.screens.ApiSettingsFields
import com.smassistant.app.ui.screens.ApiStatusContent
import com.smassistant.app.ui.screens.AssistantHubScreen
import com.smassistant.app.ui.screens.ChatScreen
import com.smassistant.app.ui.screens.DefaultHashtagsContent
import com.smassistant.app.ui.screens.HomeScreen
import com.smassistant.app.ui.theme.SmAssistantTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the screens shown in docs/user-guide/ to PNG files. Runs only when Gradle is given
 * -PscreenshotsDir=<folder> (the CI "screenshots" job does this); otherwise every test is skipped.
 * The data is made up; the keys are fake and show as dots anyway.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class UserGuideScreenshots {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = System.getProperty("screenshotsDir").orEmpty()

    @Before fun onlyWhenAsked() = assumeTrue("screenshotsDir not set", outDir.isNotBlank())

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { SmAssistantTheme { content() } }
        compose.waitForIdle()
        // Draws the window's views straight to a bitmap: captureToImage() waits on PixelCopy,
        // which times out under Robolectric.
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File(outDir, "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = capture("01-home") { HomeScreen(onNavigate = {}) }

    @Test fun linkedInSettings() = capture("02-linkedin-settings") {
        SmScreen(sectionTitle = "LinkedIn", onBackToHome = {}) {
            ApiSettingsFields(
                serviceName = "LinkedIn",
                state = ApiSettingsUiState(api = "https://api.linkedin.com/rest/posts", key = "AQXdemo-token-1234567890"),
                onApiChange = {},
                onKeyChange = {},
                onSave = {},
            )
        }
    }

    @Test fun aiAgents() = capture("03-ai-agents") { AiAgentListScreen(onAgentSelected = {}, onBackToHome = {}) }

    @Test fun claudeSettings() = capture("04-claude-settings") {
        SmScreen(sectionTitle = "Claude AI Agent", onBackToHome = {}) {
            ApiSettingsFields(
                serviceName = "Claude",
                state = ApiSettingsUiState(
                    api = "https://api.anthropic.com/v1/messages",
                    key = "sk-ant-demo-key-1234567890",
                ),
                onApiChange = {},
                onKeyChange = {},
                onSave = {},
            )
        }
    }

    @Test fun defaultHashtags() = capture("05-default-hashtags") {
        DefaultHashtagsContent(
            state = DefaultHashtagsUiState(text = "#JavaDeveloper #AIEngineer #SystemDesign", enabled = true),
            onTextChange = {},
            onSave = {},
            onClear = {},
            onEnabledChange = {},
            onBackToHome = {},
        )
    }

    @Test fun apiStatus() = capture("06-api-status") {
        ApiStatusContent(
            statuses = mapOf(
                ApiService.LINKEDIN to ConnectionStatus.Connected,
                ApiService.CLAUDE to ConnectionStatus.Connected,
                ApiService.GEMINI to ConnectionStatus.Connected,
                ApiService.GROQ to ConnectionStatus.NotConfigured,
            ),
            onTestAgain = {},
            onBackToHome = {},
        )
    }

    @Test fun hub() = capture("07-sm-assistant") { AssistantHubScreen(onFeatureSelected = {}, onBackToHome = {}) }

    @Test fun preparePrompt() = capture("08-prepare-prompt") {
        ChatScreen(
            title = Feature.PREPARE_PROMPT.title,
            state = ChatUiState(
                items = listOf(
                    ChatItem.Input(0, "Design a system for Short URL"),
                    ChatItem.Result(1, 0, "URL Shortener System Design Prompt"),
                ),
            ),
            placeholder = "Enter a topic, e.g. Design a system for Short URL",
            onInputChange = {},
            onSubmit = {},
            onRetry = {},
            onCancel = {},
            onSetUpAgent = {},
            onBackToHome = {},
            inputContent = { UserBubble(it) },
            resultContent = { PromptCard(it, SAMPLE_PROMPT) },
        )
    }

    @Test fun technicalContent() = capture("09-technical-content") {
        ChatScreen(
            title = Feature.TECHNICAL_CONTENT.title,
            state = ChatUiState(items = listOf(ChatItem.Input(0, SAMPLE_PROMPT), ChatItem.Result(1, 0, SAMPLE_POST))),
            placeholder = "Paste your prompt",
            onInputChange = {},
            onSubmit = {},
            onRetry = {},
            onCancel = {},
            onSetUpAgent = {},
            onBackToHome = {},
            inputContent = { PromptCard("Your prompt", it, collapsedLines = 4) },
            resultContent = { markdown ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarkdownText(markdown)
                    PostInLinkedInButton(onClick = {})
                }
            },
        )
    }

    // A still copy of the confirm dialog: a real AlertDialog is its own window, which isn't drawn here.
    @Test fun confirmPost() = capture("10-confirm-post") {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Post to LinkedIn?", style = MaterialTheme.typography.headlineSmall)
                    ConfirmPostContent(PostState.Confirm(SAMPLE_LINKEDIN_TEXT, listOf("#JavaDeveloper", "#AIEngineer", "#SystemDesign"))) {}
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {}) { Text("Cancel") }
                        TextButton(onClick = {}) { Text("Post") }
                    }
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_PROMPT =
            "Act as a Senior Principal Software Engineer. Write a LinkedIn post that explains how to design " +
                "a URL shortener: the API, generating short codes, storage, caching and scaling reads."

        const val SAMPLE_POST = """## How I'd design a URL shortener

A short link looks simple, but it hides **three** hard problems:

- **Unique codes**: base62 of a counter, no collisions
- **Fast reads**: a cache in front of the database
- **Scale**: reads outnumber writes 100 to 1

What would you add?"""

        val SAMPLE_LINKEDIN_TEXT = """How I'd design a URL shortener

A short link looks simple, but it hides three hard problems:

• Unique codes: base62 of a counter, no collisions
• Fast reads: a cache in front of the database
• Scale: reads outnumber writes 100 to 1

What would you add?

#JavaDeveloper #AIEngineer #SystemDesign"""
    }
}

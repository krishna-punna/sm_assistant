package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.smassistant.app.ui.components.MarkdownText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MarkdownTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsHeadingsListsTablesAndCodeWithoutMarkup() {
        compose.setContent {
            MarkdownText(
                """
                # System Design: Short URL Service
                Turn **long** URLs into short ones.

                1. Create Short URL
                - Redirect

                | Requirement | Target |
                |---|---|
                | Availability | 99.99% |

                ```
                GET /{code}
                ```
                """.trimIndent(),
            )
        }

        compose.onNodeWithText("System Design: Short URL Service").assertExists()
        compose.onNodeWithText("Turn long URLs into short ones.").assertExists()
        compose.onNodeWithText("Create Short URL").assertExists()
        compose.onNodeWithText("1.").assertExists()
        compose.onNodeWithText("Redirect").assertExists()
        compose.onNodeWithText("Availability").assertExists()
        compose.onNodeWithText("99.99%").assertExists()
        compose.onNodeWithText("GET /{code}").assertExists()
    }
}

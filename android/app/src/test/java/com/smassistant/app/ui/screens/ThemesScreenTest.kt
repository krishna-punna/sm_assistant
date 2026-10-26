package com.smassistant.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.smassistant.app.ui.theme.LocalSmTheme
import com.smassistant.app.ui.theme.SmAssistantTheme
import com.smassistant.app.ui.theme.SmThemes
import com.smassistant.app.ui.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ThemesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectingAThemeAndApplyingSwitchesTheApp() {
        val applied = mutableListOf<ThemeId>()
        var inUse: ThemeId? = null
        compose.setContent {
            var themeId by remember { mutableStateOf(ThemeId.CLASSIC) }
            SmAssistantTheme(SmThemes.byId(themeId)) {
                inUse = LocalSmTheme.current.id
                ThemesScreen(onApply = { applied += it; themeId = it }, onBackToHome = {})
            }
        }
        SmThemes.all.forEach { compose.onNodeWithTag("theme_${it.id.name}").performScrollTo() }
        compose.onNodeWithTag("theme_CLASSIC").performScrollTo().assertIsSelected()
        compose.onNodeWithText("✓ In use").assertExists()
        compose.onNodeWithTag("applyTheme").performScrollTo().assertIsNotEnabled()

        compose.onNodeWithTag("theme_MIDNIGHT_NEON").performScrollTo().performClick()
        compose.onNodeWithTag("theme_MIDNIGHT_NEON").assertIsSelected()
        compose.onNodeWithTag("applyTheme").performScrollTo().assertIsEnabled()
            .assertTextEquals("Apply Midnight Neon").performClick()

        assertEquals(listOf(ThemeId.MIDNIGHT_NEON), applied)
        assertEquals(ThemeId.MIDNIGHT_NEON, inUse)
        compose.onNodeWithTag("applyTheme").assertTextEquals("Midnight Neon is in use").assertIsNotEnabled()
    }
}

package com.smassistant.app.ui.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.smassistant.app.navigation.AiAgent
import com.smassistant.app.navigation.Feature
import com.smassistant.app.navigation.Routes
import com.smassistant.app.navigation.SmNavHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NAV-T1: Home buttons open the right routes and Back to Home always lands on Home with nothing behind it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class NavigationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var nav: NavHostController

    @Before fun start() = compose.setContent {
        nav = rememberNavController()
        SmNavHost(nav)
    }

    private fun route() = nav.currentDestination?.route

    // The app bar also says "SM Assistant", so buttons are found by text and click action.
    private fun click(text: String) = compose.onNode(hasText(text) and hasClickAction()).performClick()

    private fun backToHome() {
        click("Back to Home")
        compose.waitForIdle()
        assertEquals(Routes.HOME, route())
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun homeButtonsOpenTheirScreens() {
        listOf(
            "LinkedIn API" to Routes.LINKEDIN_SETTINGS,
            "AI Agent" to Routes.AI_AGENTS,
            "SM Assistant" to Routes.HUB,
            "Default Hash Tags" to Routes.DEFAULT_HASHTAGS,
            "Themes" to Routes.THEMES,
        ).forEach { (button, expected) ->
            click(button)
            compose.waitForIdle()
            assertEquals(button, expected, route())
            backToHome()
        }
    }

    @Test
    fun agentSettingsAreTwoLevelsDeepAndBackToHomeClearsThem() {
        click("AI Agent")
        click("${AiAgent.GROQ.displayName} AI Agent")
        compose.waitForIdle()
        assertEquals(Routes.AGENT_SETTINGS, route())
        backToHome()
    }

    @Test
    fun everyHubFeatureOpensItsScreen() {
        Feature.entries.forEach { feature ->
            click("SM Assistant")
            click(feature.title)
            compose.waitForIdle()
            assertEquals(Routes.FEATURE, route())
            assertEquals(feature.name, nav.currentBackStackEntry?.arguments?.getString("feature"))
            compose.onNodeWithText(feature.title).assertExists()
            backToHome()
        }
    }
}

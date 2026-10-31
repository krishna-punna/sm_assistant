package com.smassistant.app.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.ui.components.GoogleSignInNote
import com.smassistant.app.ui.components.isGoogleSignIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Improvement #11: the sign-in browser explains that Continue with Google can't work in an app. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class GoogleSignInNoteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recognisesGoogleSignInPages() {
        assertTrue(isGoogleSignIn("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"))
        assertFalse(isGoogleSignIn("https://www.linkedin.com/oauth/v2/authorization?client_id=x"))
        assertFalse(isGoogleSignIn("https://evil.example/accounts.google.com"))
        assertFalse(isGoogleSignIn("not a url"))
    }

    @Test
    fun noteOffersTheTokenRoute() {
        var used = 0
        compose.setContent { GoogleSignInNote(blocked = false, onUseToken = { used++ }) }
        compose.onNodeWithText("Continue with Google doesn't work inside apps.", substring = true).assertExists()
        compose.onNodeWithTag("useTokenInstead").performClick()
        assertEquals(1, used)
    }

    @Test
    fun blockedNoteSaysGoogleRefused() {
        compose.setContent { GoogleSignInNote(blocked = true, onUseToken = {}) }
        compose.onNodeWithText("Google doesn't allow signing in inside apps", substring = true).assertExists()
    }
}

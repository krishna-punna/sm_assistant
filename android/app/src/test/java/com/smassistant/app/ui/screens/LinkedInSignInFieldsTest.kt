package com.smassistant.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.smassistant.app.linkedin.LinkedInSession
import com.smassistant.app.settings.LinkedInAuthUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LinkedInSignInFieldsTest {
    @get:Rule val compose = createComposeRule()

    private var signIns = 0
    private var signOuts = 0

    private fun show(state: LinkedInAuthUiState, now: Long = 0L) = compose.setContent {
        Column {
            LinkedInSignInFields(state, now, {}, {}, {}, onSignIn = { signIns++ }, onSignOut = { signOuts++ })
        }
    }

    @Test
    fun signedOutOffersSignIn() {
        show(LinkedInAuthUiState(clientIdError = "Enter the Client ID from your LinkedIn app"))
        compose.onNodeWithText("Client ID").assertExists()
        compose.onNodeWithText("Client Secret").assertExists()
        compose.onNodeWithText("Enter the Client ID from your LinkedIn app").assertExists()
        compose.onNodeWithText("Add this exact URL under Authorized redirect URLs in your LinkedIn app.").assertExists()
        compose.onNodeWithText("Sign in with LinkedIn").performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun signedInShowsNameAndSignOut() {
        show(LinkedInAuthUiState(session = LinkedInSession("Punna Krishna", expiresAt = 10_000L, tokenHash = "h")), now = 0L)
        compose.onNodeWithText("Signed in as Punna Krishna").assertExists()
        compose.onNodeWithText("Sign in again").assertExists()
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(1, signOuts)
    }

    @Test
    fun expiredTokenAsksToSignInAgain() {
        show(LinkedInAuthUiState(session = LinkedInSession("Punna Krishna", expiresAt = 10_000L, tokenHash = "h")), now = 20_000L)
        compose.onNodeWithText("Sign in again.", substring = true).assertExists()
    }

    @Test
    fun finishingDisablesTheButton() {
        show(LinkedInAuthUiState(finishing = true))
        compose.onNodeWithText("Signing in…").assertIsNotEnabled()
    }

    @Test
    fun explainsGoogleLoginAccounts() {
        show(LinkedInAuthUiState())
        compose.onNodeWithTag("googleAccountHint").assertExists()
    }
}

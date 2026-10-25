package com.smassistant.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.linkedin.LinkedInAuthClient
import com.smassistant.app.linkedin.LinkedInAuthStore
import com.smassistant.app.linkedin.LinkedInOAuth
import com.smassistant.app.linkedin.LinkedInOAuthApp
import com.smassistant.app.linkedin.LinkedInRedirect
import com.smassistant.app.linkedin.LinkedInSession
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkedInAuthUiState(
    val clientId: String = "",
    val clientSecret: String = "",
    val redirectUri: String = LinkedInOAuthApp.DEFAULT_REDIRECT_URI,
    val clientIdError: String? = null,
    val clientSecretError: String? = null,
    val redirectUriError: String? = null,
    /** The signed-in member, if the saved token is the one sign-in produced. */
    val session: LinkedInSession? = null,
    /** Set while the in-app browser shows LinkedIn's sign-in page. */
    val authorizationUrl: String? = null,
    /** Swapping the code for a token. */
    val finishing: Boolean = false,
    /** Changes whenever sign-in or sign-out changes the saved token, so the paste form can reload. */
    val tokenVersion: Int = 0,
    /** One-off confirmation or error, cleared with [LinkedInAuthViewModel.messageShown]. */
    val message: String? = null,
) {
    fun isExpired(now: Long) = session != null && session.expiresAt <= now
}

/**
 * S2 "Sign in with LinkedIn" (Q-03, LI-Q3-I): the member's developer app (Client ID, Client Secret,
 * redirect URL) and the OAuth 2.0 authorization-code flow. The token is saved as the LinkedIn key,
 * so posting doesn't change; pasting a token stays available as a fallback.
 */
class LinkedInAuthViewModel(
    private val authStore: LinkedInAuthStore,
    private val settings: ApiSettingsStore,
    private val client: LinkedInAuthClient = LinkedInAuthClient(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newState: () -> String = LinkedInOAuth::newState,
) : ViewModel() {
    private val _state = MutableStateFlow(
        authStore.app().let {
            LinkedInAuthUiState(clientId = it.clientId, clientSecret = it.clientSecret, redirectUri = it.redirectUri, session = currentSession())
        },
    )
    val state: StateFlow<LinkedInAuthUiState> = _state.asStateFlow()

    private var pendingState: String? = null

    fun onClientIdChange(value: String) = _state.update { it.copy(clientId = value, clientIdError = null) }

    fun onClientSecretChange(value: String) = _state.update { it.copy(clientSecret = value, clientSecretError = null) }

    fun onRedirectUriChange(value: String) = _state.update { it.copy(redirectUri = value, redirectUriError = null) }

    /** Called when the screen is shown again, e.g. after a token was pasted. */
    fun refreshSession() = _state.update { it.copy(session = currentSession()) }

    /** Validates and saves the developer app, then opens LinkedIn's sign-in page. */
    fun signIn() {
        val current = _state.value
        val redirect = current.redirectUri.trim().ifEmpty { LinkedInOAuthApp.DEFAULT_REDIRECT_URI }
        val clientIdError = if (current.clientId.isBlank()) "Enter the Client ID from your LinkedIn app" else null
        val clientSecretError = if (current.clientSecret.isBlank()) "Enter the Client Secret from your LinkedIn app" else null
        val redirectError = if (!isValidHttpsUrl(redirect)) "Enter a full https:// URL" else null
        if (clientIdError != null || clientSecretError != null || redirectError != null) {
            _state.update { it.copy(clientIdError = clientIdError, clientSecretError = clientSecretError, redirectUriError = redirectError) }
            return
        }
        val app = LinkedInOAuthApp(current.clientId.trim(), current.clientSecret.trim(), redirect)
        authStore.saveApp(app)
        val state = newState().also { pendingState = it }
        _state.update {
            it.copy(clientId = app.clientId, clientSecret = app.clientSecret, redirectUri = app.redirectUri, authorizationUrl = LinkedInOAuth.authorizationUrl(app, state))
        }
    }

    /**
     * Every URL the sign-in browser is about to load. Returns true when it was the redirect, which
     * the browser must not load: it closes and the code is swapped for a token.
     */
    fun onBrowserNavigate(url: String): Boolean {
        val expected = pendingState ?: return false
        val app = authStore.app()
        val redirect = LinkedInOAuth.parseRedirect(url, app.redirectUri, expected) ?: return false
        pendingState = null
        _state.update { it.copy(authorizationUrl = null) }
        when (redirect) {
            is LinkedInRedirect.Denied -> _state.update { it.copy(message = redirect.message) }
            is LinkedInRedirect.Code -> finish(app, redirect.code)
        }
        return true
    }

    fun cancelSignIn() {
        pendingState = null
        _state.update { it.copy(authorizationUrl = null) }
    }

    fun signOut() {
        settings.save(ApiService.LINKEDIN, settings.load(ApiService.LINKEDIN).api, "")
        authStore.clearSession()
        _state.update { it.copy(session = null, tokenVersion = it.tokenVersion + 1, message = "Signed out of LinkedIn") }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun finish(app: LinkedInOAuthApp, code: String) {
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            val token = when (val result = client.exchangeCode(app, code)) {
                is ApiResult.Ok -> result.value
                is ApiResult.Failed -> {
                    _state.update { it.copy(finishing = false, message = exchangeError(result.error)) }
                    return@launch
                }
            }
            // The name is only for display; a token without `profile` can still post.
            val name = (client.memberName(token.accessToken) as? ApiResult.Ok)?.value ?: "LinkedIn member"
            settings.save(ApiService.LINKEDIN, settings.load(ApiService.LINKEDIN).api, token.accessToken)
            val session = LinkedInSession(name, now() + token.expiresInSeconds * 1000, LinkedInOAuth.hash(token.accessToken))
            authStore.saveSession(session)
            _state.update {
                it.copy(finishing = false, session = session, tokenVersion = it.tokenVersion + 1, message = "Signed in to LinkedIn as $name")
            }
        }
    }

    private fun exchangeError(error: ApiError) = when (error) {
        ApiError.InvalidKey, is ApiError.Server ->
            "LinkedIn didn't accept the sign-in. Check the Client ID, Client Secret and that the redirect URL is registered in your LinkedIn app."
        else -> error.userMessage(ApiService.LINKEDIN.displayName)
    }

    private fun currentSession(): LinkedInSession? {
        val session = authStore.session() ?: return null
        val key = settings.load(ApiService.LINKEDIN).key
        return session.takeIf { key.isNotBlank() && LinkedInOAuth.hash(key) == it.tokenHash }
    }
}

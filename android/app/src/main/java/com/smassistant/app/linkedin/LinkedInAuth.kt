package com.smassistant.app.linkedin

import android.content.Context
import com.smassistant.app.data.KeystoreCipher
import com.smassistant.app.data.SecretCipher
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.sendJson
import com.smassistant.app.net.textOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The member's LinkedIn developer app (Q-03): typed in on the LinkedIn settings screen.
 * [redirectUri] must be listed under "Authorized redirect URLs" in that app.
 */
data class LinkedInOAuthApp(val clientId: String, val clientSecret: String, val redirectUri: String = DEFAULT_REDIRECT_URI) {
    val isComplete get() = clientId.isNotBlank() && clientSecret.isNotBlank() && redirectUri.isNotBlank()

    companion object {
        /**
         * LinkedIn only accepts http(s) redirect URLs, not an app scheme, so sign-in runs in an
         * in-app browser that catches this URL before it loads. This one is LinkedIn's own (its
         * OAuth token tool uses it), so the code never goes to a site we don't control.
         */
        const val DEFAULT_REDIRECT_URI = "https://www.linkedin.com/developers/tools/oauth/redirect"
    }
}

/** Who signed in and when the token stops working. [tokenHash] ties it to the token it came with. */
data class LinkedInSession(val name: String, val expiresAt: Long, val tokenHash: String)

/** What the token endpoint returned. */
data class LinkedInToken(val accessToken: String, val expiresInSeconds: Long)

/** The redirect after the member approves (or declines) on LinkedIn. */
sealed interface LinkedInRedirect {
    data class Code(val code: String) : LinkedInRedirect
    data class Denied(val message: String) : LinkedInRedirect
}

/** OAuth 2.0 authorization-code flow for LinkedIn ("Sign in with LinkedIn using OpenID Connect" + "Share on LinkedIn"). */
object LinkedInOAuth {
    const val AUTHORIZE_URL = "https://www.linkedin.com/oauth/v2/authorization"
    const val TOKEN_URL = "https://www.linkedin.com/oauth/v2/accessToken"
    const val USERINFO_URL = "https://api.linkedin.com/v2/userinfo"
    const val SCOPES = "openid profile w_member_social"

    fun newState(): String = ByteArray(24).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun authorizationUrl(app: LinkedInOAuthApp, state: String): String = AUTHORIZE_URL.toHttpUrl().newBuilder()
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", app.clientId.trim())
        .addQueryParameter("redirect_uri", app.redirectUri.trim())
        .addQueryParameter("state", state)
        .addQueryParameter("scope", SCOPES)
        .build()
        .toString()

    /**
     * Null when [url] isn't the redirect (the browser keeps loading it). A redirect with the wrong
     * [expectedState] is refused, so a link from elsewhere can't sign the app in.
     */
    fun parseRedirect(url: String, redirectUri: String, expectedState: String): LinkedInRedirect? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val expected = redirectUri.trim().toHttpUrlOrNull() ?: return null
        if (parsed.scheme != expected.scheme || parsed.host != expected.host || parsed.encodedPath != expected.encodedPath) return null
        parsed.queryParameter("error")?.let { error ->
            val description = parsed.queryParameter("error_description")
            return LinkedInRedirect.Denied(
                if (error == "user_cancelled_login" || error == "user_cancelled_authorize") "Sign-in was cancelled."
                else "LinkedIn refused sign-in: ${description ?: error}"
            )
        }
        if (parsed.queryParameter("state") != expectedState) return LinkedInRedirect.Denied("Sign-in didn't match this request. Try again.")
        val code = parsed.queryParameter("code")
        return if (code.isNullOrBlank()) LinkedInRedirect.Denied("LinkedIn didn't return a sign-in code.") else LinkedInRedirect.Code(code)
    }

    /** Short fingerprint of a token, so the saved name and expiry are only shown for that token. */
    fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.trim().toByteArray())
        .take(12).joinToString("") { "%02x".format(it) }
}

/** Swaps the sign-in code for an access token and reads the member's name. */
open class LinkedInAuthClient(
    private val client: OkHttpClient = Http.client,
    private val tokenUrl: String = LinkedInOAuth.TOKEN_URL,
    private val userInfoUrl: String = LinkedInOAuth.USERINFO_URL,
) {
    open suspend fun exchangeCode(app: LinkedInOAuthApp, code: String): ApiResult<LinkedInToken> {
        val request = Request.Builder()
            .url(tokenUrl)
            .post(
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", app.redirectUri.trim())
                    .add("client_id", app.clientId.trim())
                    .add("client_secret", app.clientSecret.trim())
                    .build()
            )
            .build()
        return when (val result = client.sendJson(request)) {
            is ApiResult.Failed -> result
            is ApiResult.Ok -> {
                val token = result.value.path("access_token")?.textOrNull
                val expiresIn = result.value.path("expires_in")?.textOrNull?.toLongOrNull()
                if (token.isNullOrBlank()) {
                    ApiResult.Failed(ApiError.BadResponse("no access token"))
                } else {
                    ApiResult.Ok(LinkedInToken(token, expiresIn ?: DEFAULT_EXPIRES_IN))
                }
            }
        }
    }

    /** The member's display name from OpenID Connect `userinfo`. */
    open suspend fun memberName(token: String): ApiResult<String> {
        val request = Request.Builder().url(userInfoUrl).header("Authorization", "Bearer $token").get().build()
        return when (val result = client.sendJson(request)) {
            is ApiResult.Failed -> result
            is ApiResult.Ok -> {
                val json = result.value
                val name = json.path("name")?.textOrNull
                    ?: listOfNotNull(json.path("given_name")?.textOrNull, json.path("family_name")?.textOrNull).joinToString(" ")
                ApiResult.Ok(name.ifBlank { "LinkedIn member" })
            }
        }
    }

    private companion object {
        /** LinkedIn member tokens last 60 days. */
        const val DEFAULT_EXPIRES_IN = 60L * 24 * 60 * 60
    }
}

/**
 * The developer app and the signed-in session. Stored next to the API settings; the client
 * secret is encrypted like the API keys. The access token itself is the LinkedIn API key in
 * [com.smassistant.app.data.ApiSettingsStore], so posting works the same whichever way it was set.
 */
class LinkedInAuthStore(context: Context, private val cipher: SecretCipher = KeystoreCipher()) {
    private val prefs = context.applicationContext.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    fun app(): LinkedInOAuthApp = LinkedInOAuthApp(
        clientId = prefs.getString(CLIENT_ID, null).orEmpty(),
        clientSecret = prefs.getString(CLIENT_SECRET, null)?.let(cipher::decrypt).orEmpty(),
        redirectUri = prefs.getString(REDIRECT_URI, null) ?: LinkedInOAuthApp.DEFAULT_REDIRECT_URI,
    )

    /** A blank redirect URI goes back to the default. */
    fun saveApp(app: LinkedInOAuthApp) {
        prefs.edit().apply {
            putString(CLIENT_ID, app.clientId.trim())
            putString(CLIENT_SECRET, cipher.encrypt(app.clientSecret.trim()))
            if (app.redirectUri.isBlank()) remove(REDIRECT_URI) else putString(REDIRECT_URI, app.redirectUri.trim())
        }.apply()
    }

    fun session(): LinkedInSession? {
        val name = prefs.getString(SESSION_NAME, null) ?: return null
        val hash = prefs.getString(SESSION_TOKEN, null) ?: return null
        return LinkedInSession(name, prefs.getLong(SESSION_EXPIRES, 0L), hash)
    }

    fun saveSession(session: LinkedInSession) {
        prefs.edit()
            .putString(SESSION_NAME, session.name)
            .putLong(SESSION_EXPIRES, session.expiresAt)
            .putString(SESSION_TOKEN, session.tokenHash)
            .apply()
    }

    fun clearSession() {
        prefs.edit().remove(SESSION_NAME).remove(SESSION_EXPIRES).remove(SESSION_TOKEN).apply()
    }

    private companion object {
        const val CLIENT_ID = "LINKEDIN.client_id"
        const val CLIENT_SECRET = "LINKEDIN.client_secret_enc"
        const val REDIRECT_URI = "LINKEDIN.redirect_uri"
        const val SESSION_NAME = "LINKEDIN.session_name"
        const val SESSION_EXPIRES = "LINKEDIN.session_expires"
        const val SESSION_TOKEN = "LINKEDIN.session_token"
    }
}

package com.smassistant.app.linkedin

import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LinkedInOAuthTest {
    private val app = LinkedInOAuthApp("client-1", "secret-1", "https://example.com/callback")

    @Test
    fun `authorization url asks for the posting and profile scopes`() {
        val url = LinkedInOAuth.authorizationUrl(app, "state-1").toHttpUrl()
        assertEquals("www.linkedin.com", url.host)
        assertEquals("/oauth/v2/authorization", url.encodedPath)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("client-1", url.queryParameter("client_id"))
        assertEquals("https://example.com/callback", url.queryParameter("redirect_uri"))
        assertEquals("state-1", url.queryParameter("state"))
        assertEquals("openid profile w_member_social", url.queryParameter("scope"))
    }

    @Test
    fun `other pages are not the redirect`() {
        assertNull(LinkedInOAuth.parseRedirect("https://www.linkedin.com/login", app.redirectUri, "s"))
        assertNull(LinkedInOAuth.parseRedirect("https://example.com/other?code=x&state=s", app.redirectUri, "s"))
        assertNull(LinkedInOAuth.parseRedirect("not a url", app.redirectUri, "s"))
    }

    @Test
    fun `redirect with the matching state gives the code`() {
        assertEquals(
            LinkedInRedirect.Code("abc"),
            LinkedInOAuth.parseRedirect("https://example.com/callback?code=abc&state=s", app.redirectUri, "s"),
        )
    }

    @Test
    fun `redirect with another state or an error is refused`() {
        assertTrue(LinkedInOAuth.parseRedirect("https://example.com/callback?code=abc&state=x", app.redirectUri, "s") is LinkedInRedirect.Denied)
        assertEquals(
            LinkedInRedirect.Denied("Sign-in was cancelled."),
            LinkedInOAuth.parseRedirect("https://example.com/callback?error=user_cancelled_login&state=s", app.redirectUri, "s"),
        )
        assertEquals(
            LinkedInRedirect.Denied("LinkedIn refused sign-in: bad scope"),
            LinkedInOAuth.parseRedirect("https://example.com/callback?error=unauthorized_scope_error&error_description=bad%20scope", app.redirectUri, "s"),
        )
    }

    @Test
    fun `states are random and hashes stable`() {
        assertNotEquals(LinkedInOAuth.newState(), LinkedInOAuth.newState())
        assertEquals(LinkedInOAuth.hash("t"), LinkedInOAuth.hash(" t "))
        assertNotEquals(LinkedInOAuth.hash("t"), LinkedInOAuth.hash("u"))
    }
}

class LinkedInAuthClientTest {
    private val server = MockWebServer()
    private lateinit var client: LinkedInAuthClient
    private val app = LinkedInOAuthApp("client-1", "secret-1", "https://example.com/callback")

    @Before fun start() {
        server.start()
        client = LinkedInAuthClient(Http.build(timeoutSeconds = 2), server.url("/oauth/v2/accessToken").toString(), server.url("/v2/userinfo").toString())
    }

    @After fun stop() = server.shutdown()

    @Test
    fun `exchanges the code with the app's credentials`() {
        server.enqueue(MockResponse().setBody("""{"access_token":"tok","expires_in":5184000,"scope":"openid,profile,w_member_social"}"""))
        assertEquals(ApiResult.Ok(LinkedInToken("tok", 5_184_000)), runBlocking { client.exchangeCode(app, "code-1") })
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val form = request.body.readUtf8()
        listOf("grant_type=authorization_code", "code=code-1", "client_id=client-1", "client_secret=secret-1", "redirect_uri=https%3A%2F%2Fexample.com%2Fcallback")
            .forEach { assertTrue(form, form.contains(it)) }
    }

    @Test
    fun `rejected credentials fail`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_client"}"""))
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), runBlocking { client.exchangeCode(app, "code-1") })
    }

    @Test
    fun `reads the member name`() {
        server.enqueue(MockResponse().setBody("""{"sub":"abc","name":"Punna Krishna"}"""))
        assertEquals(ApiResult.Ok("Punna Krishna"), runBlocking { client.memberName("tok") })
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }
}

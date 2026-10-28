package com.smassistant.app.data

import com.smassistant.app.net.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiConnectionCheckTest {
    private val server = MockWebServer()
    private val check = ApiConnectionCheck(Http.build(timeoutSeconds = 1))

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun config(path: String = "/some/api/path?x=1", key: String = "secret") =
        ApiConfig(api = server.url(path).toString(), key = key)

    private fun statusFor(code: Int, service: ApiService = ApiService.CLAUDE) = runBlocking {
        server.enqueue(MockResponse().setResponseCode(code))
        check.check(service, config())
    }

    @Test
    fun `maps http codes to statuses`() {
        assertEquals(ConnectionStatus.Connected, statusFor(200))
        assertEquals(ConnectionStatus.Connected, statusFor(204))
        assertEquals(ConnectionStatus.InvalidKey, statusFor(400))
        assertEquals(ConnectionStatus.InvalidKey, statusFor(401))
        assertEquals(ConnectionStatus.NoPermission, statusFor(403))
        assertEquals(ConnectionStatus.Error(500), statusFor(500))
        assertEquals(ConnectionStatus.Error(429), statusFor(429))
    }

    @Test
    fun `sends the right path and auth header per service`() = runBlocking {
        val expected = mapOf(
            ApiService.CLAUDE to ("/v1/models" to ("x-api-key" to "secret")),
            ApiService.GEMINI to ("/v1beta/models" to ("x-goog-api-key" to "secret")),
            ApiService.GROQ to ("/openai/v1/models" to ("Authorization" to "Bearer secret")),
            ApiService.LINKEDIN to ("/v2/userinfo" to ("Authorization" to "Bearer secret")),
        )
        expected.forEach { (service, pathAndHeader) ->
            server.enqueue(MockResponse())
            check.check(service, config())
            val request = server.takeRequest()
            assertEquals(service.name, "GET", request.method)
            assertEquals(service.name, pathAndHeader.first, request.path)
            assertEquals(service.name, pathAndHeader.second.second, request.getHeader(pathAndHeader.second.first))
        }
    }

    @Test
    fun `claude check sends the api version`() = runBlocking {
        server.enqueue(MockResponse())
        check.check(ApiService.CLAUDE, config())
        assertEquals("2023-06-01", server.takeRequest().getHeader("anthropic-version"))
    }

    @Test
    fun `blank url or key sends nothing`() = runBlocking {
        assertEquals(ConnectionStatus.NotConfigured, check.check(ApiService.GROQ, config(key = "")))
        assertEquals(ConnectionStatus.NotConfigured, check.check(ApiService.GROQ, ApiConfig(api = " ", key = "k")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid url is unreachable`() = runBlocking {
        assertEquals(
            ConnectionStatus.Unreachable("Invalid API URL"),
            check.check(ApiService.GROQ, ApiConfig(api = "htp:/x", key = "k")),
        )
    }

    @Test
    fun `timeout or dropped connection is unreachable`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertTrue(check.check(ApiService.GEMINI, config()) is ConnectionStatus.Unreachable)
    }

    @Test
    fun `request keeps the port and drops path and query`() {
        val request = check.request(ApiService.GROQ, ApiConfig("https://example.com:8443/openai/v1/chat?x=1", "k"))!!
        assertEquals("https://example.com:8443/openai/v1/models", request.url.toString())
        assertNull(check.request(ApiService.GROQ, ApiConfig("not a url", "k")))
    }

    @Test
    fun `new agents check their keys with an authenticated request`() = runBlocking {
        val expected = mapOf(
            ApiService.POLLINATIONS to "/account/key",
        )
        expected.forEach { (service, path) ->
            server.enqueue(MockResponse())
            assertEquals(service.name, ConnectionStatus.Connected, check.check(service, config()))
            val request = server.takeRequest()
            assertEquals(service.name, path, request.path)
            assertEquals(service.name, "Bearer secret", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `cloudflare checks the account's model list and needs an account id`() = runBlocking {
        val api = server.url("/client/v4").toString().trimEnd('/') + "/accounts/{account_id}/ai"
        assertEquals(ConnectionStatus.NotConfigured, check.check(ApiService.CLOUDFLARE, ApiConfig(api, "secret")))
        assertEquals(0, server.requestCount)

        server.enqueue(MockResponse())
        assertEquals(ConnectionStatus.Connected, check.check(ApiService.CLOUDFLARE, ApiConfig(api, "secret", accountId = "acc1")))
        val request = server.takeRequest()
        assertEquals("/client/v4/accounts/acc1/ai/models/search?per_page=1", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
    }
}

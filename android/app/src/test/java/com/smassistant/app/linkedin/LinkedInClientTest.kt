package com.smassistant.app.linkedin

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LinkedInClientTest {
    private val server = MockWebServer()
    private var key = "token-1"
    private lateinit var linkedIn: LinkedInClient

    @Before fun start() {
        server.start()
        linkedIn = LinkedInClient({ ApiConfig(server.url("/rest/posts").toString(), key) }, Http.build(timeoutSeconds = 2))
    }

    @After fun stop() = server.shutdown()

    private fun userinfo() = server.enqueue(MockResponse().setBody("""{"sub":"abc123","name":"Punna Krishna"}"""))
    private fun created(urn: String = "urn:li:share:7400") =
        server.enqueue(MockResponse().setResponseCode(201).setHeader("x-restli-id", urn))

    private fun post(text: String) = runBlocking { linkedIn.createTextPost(text) }

    @Test
    fun `creates a text post as the member with restli headers`() {
        userinfo()
        created()

        val result = post("Hello (world) #java")

        assertEquals(ApiResult.Ok(LinkedInPost("urn:li:share:7400")), result)
        assertEquals("https://www.linkedin.com/feed/update/urn:li:share:7400", (result as ApiResult.Ok).value.url)

        val me = server.takeRequest()
        assertEquals("GET", me.method)
        assertEquals("/v2/userinfo", me.path)
        assertEquals("Bearer token-1", me.getHeader("Authorization"))

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/rest/posts", create.path)
        assertEquals("Bearer token-1", create.getHeader("Authorization"))
        assertEquals(LinkedInClient.API_VERSION, create.getHeader("LinkedIn-Version"))
        assertEquals("2.0.0", create.getHeader("X-Restli-Protocol-Version"))
        val body = Json.parseToJsonElement(create.body.readUtf8()) as JsonObject
        assertEquals("urn:li:person:abc123", body.path("author")?.textOrNull)
        assertEquals("Hello \\(world\\) {hashtag|\\#|java}", body.path("commentary")?.textOrNull)
        assertEquals("PUBLIC", body.path("visibility")?.textOrNull)
        assertEquals("MAIN_FEED", body.path("distribution", "feedDistribution")?.textOrNull)
        assertEquals("PUBLISHED", body.path("lifecycleState")?.textOrNull)
    }

    @Test
    fun `member id is fetched once per token`() {
        userinfo()
        created("urn:li:share:1")
        created("urn:li:share:2")
        post("one")
        post("two")
        assertEquals(3, server.requestCount)

        key = "token-2"
        userinfo()
        created("urn:li:share:3")
        post("three")
        assertEquals("/v2/userinfo", server.run { takeRequest(); takeRequest(); takeRequest(); takeRequest() }.path)
    }

    @Test
    fun `missing token sends nothing`() {
        key = ""
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), post("hi"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `errors map to ApiError with LinkedIn's message`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Expired token","status":401}"""))
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), post("hi"))

        userinfo()
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Not enough permissions","status":403}"""))
        assertEquals(ApiResult.Failed(ApiError.NoAccess), post("hi"))

        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"commentary too long","status":422}"""))
        assertEquals(ApiResult.Failed(ApiError.Server(422, "commentary too long")), post("hi"))

        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(ApiResult.Failed(ApiError.RateLimited), post("hi"))
    }

    @Test
    fun `a created post without an id is a bad response`() {
        userinfo()
        server.enqueue(MockResponse().setResponseCode(201))
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no post id")), post("hi"))
    }
}

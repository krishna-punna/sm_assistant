package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ImageFormat
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImageAgentTest {
    private val server = MockWebServer()
    private val dir = Files.createTempDirectory("images").toFile()
    private var config = ApiConfig("", "secret", "gemini-2.5-flash", "image-model")
    private lateinit var agent: GeminiImageAgent

    @Before fun start() {
        server.start()
        config = config.copy(api = server.url("/v1beta").toString())
        agent = GeminiImageAgent({ config }, MediaCache(dir), Http.build(timeoutSeconds = 2))
    }

    @After fun stop() = server.shutdown()

    private fun image(bytes: ByteArray, mime: String = "image/png") = server.enqueue(
        MockResponse().setBody(
            """{"candidates":[{"content":{"parts":[{"text":"Here is a diagram"},""" +
                """{"inlineData":{"mimeType":"$mime","data":"${Base64.getEncoder().encodeToString(bytes)}"}}]}}]}""",
        ),
    )

    private fun generate(count: Int) = runBlocking { agent.generate("Draw a URL shortener", count) }

    @Test
    fun `asks for the requested count and saves each image`() {
        image(byteArrayOf(1, 2), "image/jpeg")
        image(byteArrayOf(3))
        image(byteArrayOf(4))

        val files = (generate(3) as ApiResult.Ok).value

        assertEquals(3, files.size)
        assertEquals(3, server.requestCount)
        assertTrue(files.all { it.parentFile == dir && it.exists() })
        assertEquals(setOf(listOf<Byte>(1, 2), listOf<Byte>(3), listOf<Byte>(4)), files.map { it.readBytes().toList() }.toSet())
        assertTrue(files.any { it.name.endsWith(".jpg") })

        val request = server.takeRequest()
        assertEquals("/v1beta/models/image-model:generateContent", request.path)
        assertEquals("secret", request.getHeader("x-goog-api-key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()) as JsonObject
        assertEquals("Draw a URL shortener", body.path("contents", 0, "parts", 0, "text")?.textOrNull)
        assertEquals(2, (body.path("generationConfig", "responseModalities") as JsonArray).size)
        assertEquals(null, body.path("generationConfig", "imageConfig"))
    }

    @Test
    fun `a format is sent as the aspect ratio`() {
        for ((format, ratio) in listOf(ImageFormat.PORTRAIT to "2:3", ImageFormat.SQUARE to "1:1", ImageFormat.LANDSCAPE to "3:2")) {
            image(byteArrayOf(1))
            runBlocking { agent.generate("Draw a URL shortener", 1, format) }
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
            assertEquals(ratio, body.path("generationConfig", "imageConfig", "aspectRatio")?.textOrNull)
        }
    }

    @Test
    fun `keeps the images that worked`() {
        image(byteArrayOf(9))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))

        val files = (generate(2) as ApiResult.Ok).value
        assertEquals(1, files.size)
        assertArrayEquals(byteArrayOf(9), files.single().readBytes())
    }

    @Test
    fun `fails when every call fails`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"API key not valid"}}"""))
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), generate(1))

        server.enqueue(MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no image in the reply (SAFETY)")), generate(1))
    }

    @Test
    fun `missing key or image model sends nothing`() {
        config = config.copy(key = "")
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), generate(4))
        config = config.copy(key = "secret", imageModel = "")
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), generate(4))
        assertEquals(0, server.requestCount)
    }
}

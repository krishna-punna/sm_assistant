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
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderImageAgentsTest {
    private val server = MockWebServer()
    private val dir = Files.createTempDirectory("images").toFile()
    private val cache = MediaCache(dir)
    private val client = Http.build(timeoutSeconds = 2)

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2)
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 3, 4)

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun RecordedRequest.json() = Json.parseToJsonElement(body.readUtf8()) as JsonObject

    private fun bytes(data: ByteArray, type: String) =
        server.enqueue(MockResponse().setHeader("Content-Type", type).setBody(Buffer().write(data)))

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun base64(data: ByteArray) = Base64.getEncoder().encodeToString(data)

    private fun ImageAgent.run(count: Int = 1, prompt: String = "Draw a cache") = runBlocking { generate(prompt, count) }

    @Test
    fun `cloudflare reads flux base64 json and stable diffusion png bytes`() {
        json("""{"result":{"image":"${base64(jpeg)}"},"success":true}""")
        bytes(png, "image/png")
        val api = server.url("/client/v4").toString().trimEnd('/') + "/accounts/{account_id}/ai"
        var config = ApiConfig(api, "cf_token", "m", "@cf/black-forest-labs/flux-1-schnell", accountId = "acc1")
        val agent = CloudflareImageAgent({ config }, cache, client)

        assertArrayEquals(jpeg, (agent.run() as ApiResult.Ok).value.single().readBytes())
        val request = server.takeRequest()
        assertEquals("/client/v4/accounts/acc1/ai/run/@cf/black-forest-labs/flux-1-schnell", request.path)
        assertEquals("Bearer cf_token", request.getHeader("Authorization"))
        assertEquals("Draw a cache", request.json().path("prompt")?.textOrNull)

        config = config.copy(imageModel = "@cf/stabilityai/stable-diffusion-xl-base-1.0")
        val file = (agent.run() as ApiResult.Ok).value.single()
        assertArrayEquals(png, file.readBytes())
        assertEquals("png", file.extension)
    }

    @Test
    fun `pollinations asks for b64_json from the openai images endpoint`() {
        json("""{"created":1,"data":[{"b64_json":"${base64(png)}"}]}""")
        val config = ApiConfig(server.url("/v1/chat/completions").toString(), "sk_test", "m", "flux")

        val file = (PollinationsImageAgent({ config }, cache, client).run() as ApiResult.Ok).value.single()

        assertArrayEquals(png, file.readBytes())
        val request = server.takeRequest()
        assertEquals("/v1/images/generations", request.path)
        assertEquals("Bearer sk_test", request.getHeader("Authorization"))
        val body = request.json()
        assertEquals("flux", body.path("model")?.textOrNull)
        assertEquals("b64_json", body.path("response_format")?.textOrNull)
        assertEquals("1024x1024", body.path("size")?.textOrNull)
    }

    @Test
    fun `pollinations asks for the size of the format`() {
        assertEquals("1024x1536", pollinationsSize(ImageFormat.PORTRAIT))
        assertEquals("1024x1024", pollinationsSize(ImageFormat.SQUARE))
        assertEquals("1536x1024", pollinationsSize(ImageFormat.LANDSCAPE))
        json("""{"data":[{"b64_json":"${base64(png)}"}]}""")
        val config = ApiConfig(server.url("/v1/chat/completions").toString(), "sk_test", "m", "flux")
        runBlocking { PollinationsImageAgent({ config }, cache, client).generate("Draw a cache", 1, ImageFormat.PORTRAIT) }
        assertEquals("1024x1536", server.takeRequest().json().path("size")?.textOrNull)
    }

    @Test
    fun `keeps the images that worked and fails when none did`() {
        json("""{"data":[{"b64_json":"${base64(png)}"}]}""")
        json("""{"error":{"message":"busy"}}""", 503)
        val config = ApiConfig(server.url("/v1/chat/completions").toString(), "sk_test", "m", "flux")
        val agent = PollinationsImageAgent({ config }, cache, client)

        assertEquals(1, (agent.run(count = 2) as ApiResult.Ok).value.size)

        json("""{"error":{"message":"bad key"}}""", 401)
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), agent.run())

        json("""{"data":[]}""")
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no image in the reply")), agent.run())
    }

    @Test
    fun `missing key, image model or account id sends nothing`() {
        val url = server.url("/x").toString()
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), PollinationsImageAgent({ ApiConfig(url, "k", "m", "") }, cache, client).run())
        assertEquals(
            ApiResult.Failed(ApiError.NotConfigured),
            CloudflareImageAgent({ ApiConfig("$url/{account_id}/ai", "k", "m", "img") }, cache, client).run(),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `long prompts keep their start and end`() {
        val prompt = "STYLE " + "x".repeat(5_000) + " TOPIC"
        val short = compactPrompt(prompt, 100)
        assertEquals(100, short.length)
        assertTrue(short.startsWith("STYLE "))
        assertTrue(short.endsWith(" TOPIC"))
        assertEquals("short", compactPrompt("short", 100))
    }

    @Test
    fun `image type is read from the bytes`() {
        assertEquals("png", imageExtension(png))
        assertEquals("jpg", imageExtension(jpeg))
        assertEquals("webp", imageExtension("RIFF0000WEBPVP8 ".toByteArray()))
        assertNull(imageExtension("{}".toByteArray()))
    }
}

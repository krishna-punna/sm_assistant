package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
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

    private fun hfConfig(model: String) = ApiConfig(server.url("/v1/chat/completions").toString(), "hf_token", "m", model)

    private fun hfAgent(model: String) = HuggingFaceImageAgent({ hfConfig(model) }, cache, client, pollDelayMs = 0)

    @Test
    fun `hugging face falls back to hf-inference and saves the image bytes`() {
        json("""{"error":"Repository not found"}""", 404)
        bytes(png, "image/png")
        bytes(jpeg, "image/jpeg")

        val files = (hfAgent("black-forest-labs/FLUX.1-schnell").run(count = 2) as ApiResult.Ok).value

        assertEquals(2, files.size)
        assertEquals(setOf("png", "jpg"), files.map { it.extension }.toSet())
        val lookup = server.takeRequest()
        assertEquals("/api/models/black-forest-labs/FLUX.1-schnell?expand%5B%5D=inferenceProviderMapping", lookup.path)
        assertEquals("Bearer hf_token", lookup.getHeader("Authorization"))
        val request = server.takeRequest()
        assertEquals("/hf-inference/models/black-forest-labs/FLUX.1-schnell", request.path)
        assertEquals("Bearer hf_token", request.getHeader("Authorization"))
        assertEquals("Draw a cache", request.json().path("inputs")?.textOrNull)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `hugging face error text is passed on`() {
        json("""{"inferenceProviderMapping":{"hf-inference":{"status":"live","providerId":"black-forest-labs/x","task":"text-to-image"}}}""")
        json("""{"error":"Model black-forest-labs/x is not supported by provider hf-inference"}""", 400)

        val result = hfAgent("black-forest-labs/x").run()

        assertEquals(ApiResult.Failed(ApiError.Server(400, "Model black-forest-labs/x is not supported by provider hf-inference")), result)
    }

    @Test
    fun `hugging face runs Qwen-Image on fal-ai through the queue`() {
        json(
            """{"inferenceProviderMapping":[
                {"provider":"replicate","providerId":"qwen/qwen-image","status":"live","task":"text-to-image"},
                {"provider":"fal-ai","providerId":"fal-ai/qwen-image","status":"live","task":"text-to-image"}
            ]}""",
        )
        val requestUrl = server.url("/fal-ai/qwen-image/requests/r1").toString()
        json("""{"request_id":"r1","status":"IN_QUEUE","response_url":"$requestUrl"}""")
        json("""{"status":"IN_PROGRESS"}""")
        json("""{"status":"COMPLETED"}""")
        json("""{"images":[{"url":"${server.url("/files/out.png")}","width":1024}]}""")
        bytes(png, "image/png")

        val file = (hfAgent("Qwen/Qwen-Image").run() as ApiResult.Ok).value.single()

        assertArrayEquals(png, file.readBytes())
        assertEquals("/api/models/Qwen/Qwen-Image?expand%5B%5D=inferenceProviderMapping", server.takeRequest().path)
        val submit = server.takeRequest()
        assertEquals("POST", submit.method)
        assertEquals("/fal-ai/fal-ai/qwen-image?_subdomain=queue", submit.path)
        assertEquals("Bearer hf_token", submit.getHeader("Authorization"))
        assertEquals("Draw a cache", submit.json().path("prompt")?.textOrNull)
        assertEquals("/fal-ai/qwen-image/requests/r1/status", server.takeRequest().path)
        assertEquals("/fal-ai/qwen-image/requests/r1/status", server.takeRequest().path)
        assertEquals("/fal-ai/qwen-image/requests/r1", server.takeRequest().path)
        val download = server.takeRequest()
        assertEquals("/files/out.png", download.path)
        assertNull(download.getHeader("Authorization"))
    }

    @Test
    fun `hugging face fal-ai request that fails is reported`() {
        json("""{"inferenceProviderMapping":{"fal-ai":{"status":"live","providerId":"fal-ai/qwen-image","task":"text-to-image"}}}""")
        json("""{"request_id":"r1","response_url":"${server.url("/fal-ai/qwen-image/requests/r1")}"}""")
        json("""{"status":"FAILED"}""")

        assertEquals(ApiResult.Failed(ApiError.BadResponse("the request failed")), hfAgent("Qwen/Qwen-Image").run())
    }

    @Test
    fun `hugging face uses together when it is the first live provider`() {
        json(
            """{"inferenceProviderMapping":{
                "fal-ai":{"status":"staging","providerId":"fal-ai/x","task":"text-to-image"},
                "together":{"status":"live","providerId":"black-forest-labs/FLUX.1-schnell-Free","task":"text-to-image"}
            }}""",
        )
        json("""{"data":[{"b64_json":"${base64(jpeg)}"}]}""")

        assertArrayEquals(jpeg, (hfAgent("black-forest-labs/FLUX.1-schnell").run() as ApiResult.Ok).value.single().readBytes())
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/together/v1/images/generations", request.path)
        val body = request.json()
        assertEquals("black-forest-labs/FLUX.1-schnell-Free", body.path("model")?.textOrNull)
        assertEquals("base64", body.path("response_format")?.textOrNull)
    }

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
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), HuggingFaceImageAgent({ ApiConfig(url, "", "m", "img") }, cache, client).run())
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

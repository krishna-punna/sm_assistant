package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HuggingFaceVideoAgentTest {
    private val server = MockWebServer()
    private val cache = MediaCache(Files.createTempDirectory("videos").toFile())
    private val client = Http.build(timeoutSeconds = 2)
    private val mp4 = byteArrayOf(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70)

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private fun agent(videoModel: String = "Wan-AI/Wan2.2-T2V-A14B", key: String = "hf_token") = HuggingFaceVideoAgent(
        { ApiConfig(server.url("/v1/chat/completions").toString(), key, "m", "Qwen/Qwen-Image", videoModel) },
        cache,
        client,
        pollDelayMs = 0,
    )

    private fun HuggingFaceVideoAgent.run() = runBlocking { generate("Explain caching") }

    @Test
    fun `runs Wan 2_2 on fal-ai and saves the clip as mp4`() {
        json("""{"inferenceProviderMapping":[{"provider":"fal-ai","providerId":"fal-ai/wan/v2.2-a14b/text-to-video","status":"live","task":"text-to-video"}]}""")
        json("""{"request_id":"v1","response_url":"${server.url("/fal-ai/wan/v2.2-a14b/text-to-video/requests/v1")}"}""")
        json("""{"status":"IN_PROGRESS"}""")
        json("""{"status":"COMPLETED"}""")
        json("""{"video":{"url":"${server.url("/files/clip.mp4")}"}}""")
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(mp4)))

        val file = (agent().run() as ApiResult.Ok).value

        assertEquals("mp4", file.extension)
        assertArrayEquals(mp4, file.readBytes())
        assertEquals("/api/models/Wan-AI/Wan2.2-T2V-A14B?expand%5B%5D=inferenceProviderMapping", server.takeRequest().path)
        val submit = server.takeRequest()
        assertEquals("/fal-ai/fal-ai/wan/v2.2-a14b/text-to-video?_subdomain=queue", submit.path)
        assertEquals("Bearer hf_token", submit.getHeader("Authorization"))
        val body = Json.parseToJsonElement(submit.body.readUtf8()) as JsonObject
        assertEquals("Explain caching", body.path("prompt")?.textOrNull)
        assertEquals("/fal-ai/wan/v2.2-a14b/text-to-video/requests/v1/status", server.takeRequest().path)
        assertEquals("/fal-ai/wan/v2.2-a14b/text-to-video/requests/v1/status", server.takeRequest().path)
        assertEquals("/fal-ai/wan/v2.2-a14b/text-to-video/requests/v1", server.takeRequest().path)
        assertEquals("/files/clip.mp4", server.takeRequest().path)
    }

    @Test
    fun `a model that isn't text-to-video gives null, so a slideshow is made`() {
        json("""{"inferenceProviderMapping":{"fal-ai":{"status":"live","providerId":"fal-ai/qwen-image","task":"text-to-image"}}}""")
        assertNull(agent(videoModel = "Qwen/Qwen-Image").run())

        json("""{"error":"Repository not found"}""", 404)
        assertNull(agent(videoModel = "nobody/nothing").run())
    }

    @Test
    fun `no key or no video model sends nothing`() {
        assertNull(agent(key = "").run())
        assertNull(agent(videoModel = "").run())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a failed fal-ai request is reported`() {
        json("""{"inferenceProviderMapping":{"fal-ai":{"status":"live","providerId":"fal-ai/wan","task":"text-to-video"}}}""")
        json("""{"error":{"message":"Not enough credits"}}""", 402)

        assertEquals(ApiResult.Failed(ApiError.Server(402, "Not enough credits")), agent().run())
    }
}

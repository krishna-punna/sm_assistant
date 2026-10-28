package com.smassistant.app.data

import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ModelCatalogTest {
    private val server = MockWebServer()
    private val catalog = ModelCatalog(Http.build(timeoutSeconds = 2))

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun fetch(service: ApiService, body: String, code: Int = 200) = runBlocking {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        catalog.fetch(service, ApiConfig(server.url("/v1/messages").toString(), "secret"))
    }

    @Test
    fun `claude lists its models by id with display names`() {
        val result = fetch(
            ApiService.CLAUDE,
            """{"data":[{"id":"claude-sonnet-5-5","display_name":"Claude Sonnet 5.5"},{"id":"claude-haiku-5-5","display_name":"Claude Haiku 5.5"}]}""",
        )
        assertEquals(
            ApiResult.Ok(ModelLists(text = listOf(AiModel("claude-haiku-5-5", "Claude Haiku 5.5"), AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5")))),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("secret", request.getHeader("x-api-key"))
    }

    @Test
    fun `gemini splits text and image models and skips ones that can't generate`() {
        val result = fetch(
            ApiService.GEMINI,
            """{"models":[
                {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","supportedGenerationMethods":["generateContent","countTokens"]},
                {"name":"models/gemini-2.5-flash-image","displayName":"Nano Banana","supportedGenerationMethods":["generateContent"]},
                {"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent"]},
                {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
            ]}""",
        )
        assertEquals(
            ApiResult.Ok(
                ModelLists(
                    text = listOf(AiModel("gemini-2.5-flash", "Gemini 2.5 Flash")),
                    image = listOf(AiModel("gemini-2.5-flash-image", "Nano Banana")),
                ),
            ),
            result,
        )
        assertEquals("/v1beta/models?pageSize=1000", server.takeRequest().path)
    }

    @Test
    fun `groq leaves out speech models`() {
        val result = fetch(
            ApiService.GROQ,
            """{"data":[{"id":"whisper-large-v3"},{"id":"openai/gpt-oss-120b"},{"id":"llama-3.3-70b-versatile"},{"id":"playai-tts"}]}""",
        )
        assertEquals(ApiResult.Ok(ModelLists(text = listOf(AiModel("llama-3.3-70b-versatile"), AiModel("openai/gpt-oss-120b")))), result)
        assertEquals("Bearer secret", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `errors and empty lists fail and a missing key sends nothing`() {
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), fetch(ApiService.GROQ, """{"error":{"message":"bad key"}}""", code = 401))
        assertEquals(ApiResult.Failed(ApiError.BadResponse("no models in the reply")), fetch(ApiService.CLAUDE, """{"data":[]}"""))
        val noKey = runBlocking { catalog.fetch(ApiService.CLAUDE, ApiConfig(server.url("/").toString(), "")) }
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), noKey)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `cloudflare splits models by task`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"result":[
                    {"name":"@cf/meta/llama-3.3-70b-instruct-fp8-fast","task":{"name":"Text Generation"}},
                    {"name":"@cf/black-forest-labs/flux-1-schnell","task":{"name":"Text-to-Image"}},
                    {"name":"@cf/openai/whisper","task":{"name":"Automatic Speech Recognition"}}
                ]}""",
            ),
        )
        val api = server.url("/client/v4").toString().trimEnd('/') + "/accounts/{account_id}/ai"

        val result = catalog.fetch(ApiService.CLOUDFLARE, ApiConfig(api, "cf", accountId = "acc1"))

        assertEquals(
            ApiResult.Ok(
                ModelLists(
                    text = listOf(AiModel("@cf/meta/llama-3.3-70b-instruct-fp8-fast")),
                    image = listOf(AiModel("@cf/black-forest-labs/flux-1-schnell")),
                ),
            ),
            result,
        )
        val request = server.takeRequest()
        assertEquals("/client/v4/accounts/acc1/ai/models/search?per_page=1000", request.path)
        assertEquals("Bearer cf", request.getHeader("Authorization"))
    }

    @Test
    fun `cloudflare without an account id sends nothing`() = runBlocking {
        val api = server.url("/").toString() + "accounts/{account_id}/ai"
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), catalog.fetch(ApiService.CLOUDFLARE, ApiConfig(api, "cf")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `pollinations splits text and image models by output modality and skips video`() {
        val result = fetch(
            ApiService.POLLINATIONS,
            """{"object":"list","data":[
                {"id":"openai/gpt-4o-mini","title":"GPT-4o mini","output_modalities":["text"]},
                {"id":"black-forest-labs/flux.1-schnell","title":"FLUX.1 schnell","output_modalities":["image"]},
                {"id":"google/veo-3.1-fast","output_modalities":["video"]}
            ]}""",
        )
        assertEquals(
            ApiResult.Ok(
                ModelLists(
                    text = listOf(AiModel("openai/gpt-4o-mini", "GPT-4o mini")),
                    image = listOf(AiModel("black-forest-labs/flux.1-schnell", "FLUX.1 schnell")),
                ),
            ),
            result,
        )
        assertEquals("/v1/models", server.takeRequest().path)
    }
}

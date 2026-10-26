package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
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
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TextAgentTest {
    private val server = MockWebServer()
    private val client = Http.build(timeoutSeconds = 2)

    @Before fun start() = server.start()
    @After fun stop() = server.shutdown()

    private fun agent(service: ApiService, path: String, model: String = "test-model", key: String = "secret") =
        textAgentFor(service, { ApiConfig(server.url(path).toString().trimEnd('/'), key, model) }, client)

    private fun reply(json: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(json))

    private fun RecordedRequest.json() = Json.parseToJsonElement(body.readUtf8()) as JsonObject

    private fun generate(agent: TextAgent) = runBlocking { agent.generate("You are helpful", "Design a URL shortener") }

    @Test
    fun `claude sends messages request and joins text blocks`() {
        reply("""{"content":[{"type":"text","text":"Hello "},{"type":"tool_use"},{"type":"text","text":"world"}]}""")

        val result = generate(agent(ApiService.CLAUDE, "/v1/messages"))

        assertEquals(ApiResult.Ok("Hello world"), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/messages", request.path)
        assertEquals("secret", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = request.json()
        assertEquals("test-model", body.path("model")?.textOrNull)
        assertEquals("You are helpful", body.path("system")?.textOrNull)
        assertEquals("user", body.path("messages", 0, "role")?.textOrNull)
        assertEquals("Design a URL shortener", body.path("messages", 0, "content")?.textOrNull)
    }

    @Test
    fun `gemini calls generateContent for the model`() {
        reply("""{"candidates":[{"content":{"parts":[{"text":"Part 1. "},{"text":"Part 2."}]}}]}""")

        val result = generate(agent(ApiService.GEMINI, "/v1beta", model = "gemini-2.5-flash"))

        assertEquals(ApiResult.Ok("Part 1. Part 2."), result)
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", request.path)
        assertEquals("secret", request.getHeader("x-goog-api-key"))
        val body = request.json()
        assertEquals("You are helpful", body.path("systemInstruction", "parts", 0, "text")?.textOrNull)
        assertEquals("Design a URL shortener", body.path("contents", 0, "parts", 0, "text")?.textOrNull)
    }

    @Test
    fun `groq sends system and user messages with bearer key`() {
        reply("""{"choices":[{"message":{"role":"assistant","content":"  Answer  "}}]}""")

        val result = generate(agent(ApiService.GROQ, "/openai/v1/chat/completions"))

        assertEquals(ApiResult.Ok("Answer"), result)
        val request = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", request.path)
        assertEquals("Bearer secret", request.getHeader("Authorization"))
        val body = request.json()
        assertEquals("system", body.path("messages", 0, "role")?.textOrNull)
        assertEquals("You are helpful", body.path("messages", 0, "content")?.textOrNull)
        assertEquals("user", body.path("messages", 1, "role")?.textOrNull)
    }

    @Test
    fun `missing key or model sends nothing`() {
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), generate(agent(ApiService.CLAUDE, "/v1/messages", key = "")))
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), generate(agent(ApiService.GROQ, "/x", model = "")))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `http errors carry the provider message`() {
        reply("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""", 401)
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), generate(agent(ApiService.CLAUDE, "/v1/messages")))

        reply("""{"error":{"message":"Rate limit reached"}}""", 429)
        assertEquals(ApiResult.Failed(ApiError.RateLimited), generate(agent(ApiService.GROQ, "/x")))

        reply("""{"error":{"message":"Overloaded"}}""", 529)
        assertEquals(ApiResult.Failed(ApiError.Server(529, "Overloaded")), generate(agent(ApiService.CLAUDE, "/v1/messages")))
    }

    @Test
    fun `gemini bad key 400 maps to invalid key`() {
        reply("""{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""", 400)
        assertEquals(ApiResult.Failed(ApiError.InvalidKey), generate(agent(ApiService.GEMINI, "/v1beta")))

        reply("""{"error":{"code":400,"message":"Request contains an invalid argument."}}""", 400)
        assertEquals(
            ApiResult.Failed(ApiError.Server(400, "Request contains an invalid argument.")),
            generate(agent(ApiService.GEMINI, "/v1beta")),
        )
    }

    @Test
    fun `unexpected or empty bodies are bad responses`() {
        reply("<html>oops</html>")
        assertTrue((generate(agent(ApiService.GROQ, "/x")) as ApiResult.Failed).error is ApiError.BadResponse)

        reply("""{"choices":[]}""")
        assertTrue((generate(agent(ApiService.GROQ, "/x")) as ApiResult.Failed).error is ApiError.BadResponse)

        reply("""{"content":[{"type":"text","text":"   "}]}""")
        assertTrue((generate(agent(ApiService.CLAUDE, "/v1/messages")) as ApiResult.Failed).error is ApiError.BadResponse)
    }

    @Test
    fun `invalid url is unreachable without crashing`() {
        val bad = textAgentFor(ApiService.GROQ, { ApiConfig("not a url", "k", "m") }, client)
        assertEquals(ApiResult.Failed(ApiError.Unreachable("Invalid API URL")), generate(bad))
    }

    @Test
    fun `hugging face and pollinations use openai chat completions with a bearer key`() {
        listOf(ApiService.HUGGINGFACE to "/v1/chat/completions", ApiService.POLLINATIONS to "/v1/chat/completions").forEach { (service, path) ->
            reply("""{"choices":[{"message":{"role":"assistant","content":"Hi"}}]}""")

            assertEquals(service.name, ApiResult.Ok("Hi"), generate(agent(service, path, model = "openai/gpt-oss-120b")))
            val request = server.takeRequest()
            assertEquals(service.name, path, request.path)
            assertEquals(service.name, "Bearer secret", request.getHeader("Authorization"))
            assertEquals(service.name, "openai/gpt-oss-120b", request.json().path("model")?.textOrNull)
        }
    }

    @Test
    fun `cloudflare puts the account id in the url`() {
        reply("""{"choices":[{"message":{"role":"assistant","content":"Hi"}}]}""")
        val api = server.url("/client/v4").toString().trimEnd('/') + "/accounts/{account_id}/ai"
        val cloudflare = textAgentFor(ApiService.CLOUDFLARE, { ApiConfig(api, "token", "@cf/meta/llama", accountId = "abc123") }, client)

        assertEquals(ApiResult.Ok("Hi"), generate(cloudflare))
        val request = server.takeRequest()
        assertEquals("/client/v4/accounts/abc123/ai/v1/chat/completions", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("@cf/meta/llama", request.json().path("model")?.textOrNull)
    }

    @Test
    fun `cloudflare without an account id sends nothing`() {
        val api = server.url("/").toString() + "accounts/{account_id}/ai"
        val cloudflare = textAgentFor(ApiService.CLOUDFLARE, { ApiConfig(api, "token", "@cf/meta/llama") }, client)
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), generate(cloudflare))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cloudflare and hugging face error messages are read`() {
        reply("""{"success":false,"errors":[{"code":7003,"message":"No route for that URI"}]}""", 400)
        assertEquals(ApiResult.Failed(ApiError.Server(400, "No route for that URI")), generate(agent(ApiService.HUGGINGFACE, "/x")))

        reply("""{"error":"Model is loading"}""", 503)
        assertEquals(ApiResult.Failed(ApiError.Server(503, "Model is loading")), generate(agent(ApiService.HUGGINGFACE, "/x")))
    }
}

package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.postJson
import com.smassistant.app.net.textOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/** An AI agent that turns a system prompt and a user message into text (docs/tasks AGC-1). */
interface TextAgent {
    val service: ApiService

    suspend fun generate(system: String, user: String): ApiResult<String>
}

/** Builds the client for [service] from its current settings, read at call time so a newly saved key applies. */
fun textAgentFor(
    service: ApiService,
    config: () -> ApiConfig,
    client: OkHttpClient = Http.generationClient,
): TextAgent = when (service) {
    ApiService.CLAUDE -> ClaudeAgent(config, client)
    ApiService.GEMINI -> GeminiAgent(config, client)
    ApiService.GROQ -> GroqAgent(config, client)
    ApiService.LINKEDIN -> error("LinkedIn is not an AI agent")
}

/** True when the agent has everything needed to make a call. */
fun ApiConfig.isUsableAgent() = api.isNotBlank() && key.isNotBlank() && model.isNotBlank()

private abstract class BaseAgent(private val config: () -> ApiConfig) : TextAgent {
    override suspend fun generate(system: String, user: String): ApiResult<String> {
        val current = config()
        if (!current.isUsableAgent()) return ApiResult.Failed(ApiError.NotConfigured)
        return when (val result = call(current, system, user)) {
            is ApiResult.Failed -> ApiResult.Failed(refine(result.error))
            is ApiResult.Ok -> text(result.value)?.takeIf { it.isNotBlank() }
                ?.let { ApiResult.Ok(it.trim()) }
                ?: ApiResult.Failed(ApiError.BadResponse("no text in the reply"))
        }
    }

    protected abstract suspend fun call(config: ApiConfig, system: String, user: String): ApiResult<JsonObject>

    protected abstract fun text(response: JsonObject): String?

    protected open fun refine(error: ApiError): ApiError = error
}

/** Anthropic Messages API: POST {api} (e.g. https://api.anthropic.com/v1/messages). */
private class ClaudeAgent(config: () -> ApiConfig, private val client: OkHttpClient) : BaseAgent(config) {
    override val service = ApiService.CLAUDE

    override suspend fun call(config: ApiConfig, system: String, user: String) = client.postJson(
        url = config.api,
        headers = mapOf("x-api-key" to config.key, "anthropic-version" to "2023-06-01"),
        body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 4096)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        },
    )

    override fun text(response: JsonObject) = (response["content"] as? JsonArray)
        ?.filter { it.path("type")?.textOrNull == "text" }
        ?.mapNotNull { it.path("text")?.textOrNull }
        ?.joinToString("")
}

/** Google Gemini: POST {api}/models/{model}:generateContent (api e.g. https://generativelanguage.googleapis.com/v1beta). */
private class GeminiAgent(config: () -> ApiConfig, private val client: OkHttpClient) : BaseAgent(config) {
    override val service = ApiService.GEMINI

    override suspend fun call(config: ApiConfig, system: String, user: String) = client.postJson(
        url = "${config.api.trimEnd('/')}/models/${config.model}:generateContent",
        headers = mapOf("x-goog-api-key" to config.key),
        body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
        },
    )

    override fun text(response: JsonObject) = (response.path("candidates", 0, "content", "parts") as? JsonArray)
        ?.mapNotNull { it.path("text")?.textOrNull }
        ?.joinToString("")

    override fun refine(error: ApiError) = geminiKeyError(error)
}

/** Groq (OpenAI-compatible): POST {api} (e.g. https://api.groq.com/openai/v1/chat/completions). */
private class GroqAgent(config: () -> ApiConfig, private val client: OkHttpClient) : BaseAgent(config) {
    override val service = ApiService.GROQ

    override suspend fun call(config: ApiConfig, system: String, user: String) = client.postJson(
        url = config.api,
        headers = mapOf("Authorization" to "Bearer ${config.key}"),
        body = buildJsonObject {
            put("model", config.model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
        },
    )

    override fun text(response: JsonObject) = response.path("choices", 0, "message", "content")?.textOrNull
}

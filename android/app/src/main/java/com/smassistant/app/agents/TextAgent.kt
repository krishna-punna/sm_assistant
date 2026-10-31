package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.postJson
import com.smassistant.app.net.textOrNull
import okhttp3.OkHttpClient
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** An AI agent that turns a system prompt and a user message into text (docs/tasks AGC-1). */
interface TextAgent {
    val service: ApiService

    suspend fun generate(system: String, user: String): ApiResult<String>

    /**
     * Like [generate], with a [jpeg] image sent along with the user message, for models that can
     * see images (Gemini, and vision models behind the OpenAI-compatible APIs). A model
     * that can't answers with an error.
     */
    suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> =
        ApiResult.Failed(ApiError.BadResponse("${service.displayName} can't look at images"))
}

/** Builds the client for [service] from its current settings, read at call time so a newly saved key applies. */
fun textAgentFor(
    service: ApiService,
    config: () -> ApiConfig,
    client: OkHttpClient = Http.generationClient,
): TextAgent = when (service) {
    ApiService.GEMINI -> GeminiAgent(config, client)
    ApiService.GROQ, ApiService.POLLINATIONS -> OpenAiChatAgent(service, config, client) { it.apiUrl }
    ApiService.CLOUDFLARE -> OpenAiChatAgent(service, config, client) { "${it.apiUrl.trimEnd('/')}/v1/chat/completions" }
    ApiService.LINKEDIN -> error("LinkedIn is not an AI agent")
}

/** True when the agent has everything needed to make a call. */
fun ApiConfig.isUsableAgent() = isConfigured() && model.isNotBlank()

private abstract class BaseAgent(private val config: () -> ApiConfig) : TextAgent {
    override suspend fun generate(system: String, user: String): ApiResult<String> = reply { call(it, system, user, null) }

    override suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> =
        reply { call(it, system, user, Base64.getEncoder().encodeToString(jpeg)) }

    private suspend fun reply(send: suspend (ApiConfig) -> ApiResult<JsonObject>): ApiResult<String> {
        val current = config()
        if (!current.isUsableAgent()) return ApiResult.Failed(ApiError.NotConfigured)
        return when (val result = send(current)) {
            is ApiResult.Failed -> ApiResult.Failed(refine(result.error))
            is ApiResult.Ok -> text(result.value)?.takeIf { it.isNotBlank() }
                ?.let { ApiResult.Ok(it.trim()) }
                ?: ApiResult.Failed(ApiError.BadResponse("no text in the reply"))
        }
    }

    /** Sends the request; [jpegBase64] is an image to send with the user message, or null. */
    protected abstract suspend fun call(config: ApiConfig, system: String, user: String, jpegBase64: String?): ApiResult<JsonObject>

    protected abstract fun text(response: JsonObject): String?

    protected open fun refine(error: ApiError): ApiError = error
}

/** Google Gemini: POST {api}/models/{model}:generateContent (api e.g. https://generativelanguage.googleapis.com/v1beta). */
private class GeminiAgent(config: () -> ApiConfig, private val client: OkHttpClient) : BaseAgent(config) {
    override val service = ApiService.GEMINI

    override suspend fun call(config: ApiConfig, system: String, user: String, jpegBase64: String?) = client.postJson(
        url = "${config.api.trimEnd('/')}/models/${config.model}:generateContent",
        headers = mapOf("x-goog-api-key" to config.key),
        body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        jpegBase64?.let { data ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", data)
                                }
                            }
                        }
                        addJsonObject { put("text", user) }
                    }
                }
            }
        },
    )

    override fun text(response: JsonObject) = (response.path("candidates", 0, "content", "parts") as? JsonArray)
        ?.mapNotNull { it.path("text")?.textOrNull }
        ?.joinToString("")

    override fun refine(error: ApiError) = geminiKeyError(error)
}

/**
 * OpenAI-compatible chat completions, POST to [urlOf] the settings with a Bearer key: Groq
 * (https://api.groq.com/openai/v1/chat/completions), Pollinations.AI
 * (https://gen.pollinations.ai/v1/chat/completions) and Cloudflare Workers AI
 * ({api}/v1/chat/completions, with the account id in {api}).
 */
private class OpenAiChatAgent(
    override val service: ApiService,
    config: () -> ApiConfig,
    private val client: OkHttpClient,
    private val urlOf: (ApiConfig) -> String,
) : BaseAgent(config) {
    override suspend fun call(config: ApiConfig, system: String, user: String, jpegBase64: String?) = client.postJson(
        url = urlOf(config),
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
                    if (jpegBase64 == null) {
                        put("content", user)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", user)
                            }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", "data:image/jpeg;base64,$jpegBase64") }
                            }
                        }
                    }
                }
            }
        },
    )

    override fun text(response: JsonObject) = response.path("choices", 0, "message", "content")?.textOrNull
}

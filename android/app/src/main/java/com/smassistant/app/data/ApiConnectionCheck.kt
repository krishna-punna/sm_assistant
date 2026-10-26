package com.smassistant.app.data

import com.smassistant.app.net.Http
import com.smassistant.app.net.await
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Result of testing one service with its current settings. */
sealed interface ConnectionStatus {
    data object NotConfigured : ConnectionStatus
    data object Checking : ConnectionStatus
    data object Connected : ConnectionStatus
    data object InvalidKey : ConnectionStatus
    data object NoPermission : ConnectionStatus
    data class Unreachable(val reason: String) : ConnectionStatus
    data class Error(val httpCode: Int) : ConnectionStatus
}

/**
 * Checks access to each API with a cheap read-only request (listing models,
 * or the signed-in member for LinkedIn). Nothing is generated or posted.
 * See docs/requirements/11-api-status.md (AS-3, AS-4).
 */
class ApiConnectionCheck(private val client: OkHttpClient = Http.client) {

    suspend fun check(service: ApiService, config: ApiConfig): ConnectionStatus {
        if (!config.isConfigured()) return ConnectionStatus.NotConfigured
        val request = request(service, config) ?: return ConnectionStatus.Unreachable("Invalid API URL")
        return try {
            client.newCall(request).await().use { response ->
                when (val code = response.code) {
                    in 200..299 -> ConnectionStatus.Connected
                    400, 401 -> ConnectionStatus.InvalidKey
                    403 -> ConnectionStatus.NoPermission
                    else -> ConnectionStatus.Error(code)
                }
            }
        } catch (e: IOException) {
            ConnectionStatus.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * The check request for [service], sent to the scheme, host and port of the configured API URL
     * (for Cloudflare, under the URL itself, since the account id is in its path).
     */
    internal fun request(service: ApiService, config: ApiConfig): Request? {
        val origin = origin(config.apiUrl) ?: return null
        val builder = Request.Builder().get()
        when (service) {
            ApiService.CLAUDE -> builder.url(origin.resolve("/v1/models")!!)
                .header("x-api-key", config.key)
                .header("anthropic-version", "2023-06-01")
            ApiService.GEMINI -> builder.url(origin.resolve("/v1beta/models")!!)
                .header("x-goog-api-key", config.key)
            ApiService.GROQ -> builder.url(origin.resolve("/openai/v1/models")!!)
                .header("Authorization", "Bearer ${config.key}")
            ApiService.LINKEDIN -> builder.url(origin.resolve("/v2/userinfo")!!)
                .header("Authorization", "Bearer ${config.key}")
            // The router lists models without a key, so check the token with the Hub instead.
            ApiService.HUGGINGFACE -> builder.url(huggingFaceHub(origin).resolve("/api/whoami-v2")!!)
                .header("Authorization", "Bearer ${config.key}")
            ApiService.CLOUDFLARE -> builder.url(
                config.apiUrl.trimEnd('/').toHttpUrlOrNull()?.newBuilder()
                    ?.addPathSegments("models/search")?.addQueryParameter("per_page", "1")?.build() ?: return null,
            ).header("Authorization", "Bearer ${config.key}")
            // Model lists are public, so check the key with an account endpoint.
            ApiService.POLLINATIONS -> builder.url(origin.resolve("/account/integrations")!!)
                .header("Authorization", "Bearer ${config.key}")
        }
        return builder.build()
    }

    /** e.g. https://api.groq.com/openai/v1/chat/completions → https://api.groq.com/ */
    private fun origin(api: String): HttpUrl? =
        api.trim().toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.fragment(null)?.build()
}

/** Hugging Face's Hub (huggingface.co) for the router's URL, or the same host for any other URL, e.g. a test server. */
internal fun huggingFaceHub(origin: HttpUrl): HttpUrl =
    if (origin.host == "router.huggingface.co") origin.newBuilder().host("huggingface.co").build() else origin

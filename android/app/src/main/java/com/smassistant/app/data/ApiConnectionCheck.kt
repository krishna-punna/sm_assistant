package com.smassistant.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 */
object ApiConnectionCheck {
    private const val TIMEOUT_MS = 10_000

    suspend fun check(service: ApiService, config: ApiConfig): ConnectionStatus =
        withContext(Dispatchers.IO) {
            if (config.api.isBlank() || config.key.isBlank()) return@withContext ConnectionStatus.NotConfigured
            try {
                val origin = origin(config.api)
                val connection = when (service) {
                    ApiService.CLAUDE -> open("$origin/v1/models").apply {
                        setRequestProperty("x-api-key", config.key)
                        setRequestProperty("anthropic-version", "2023-06-01")
                    }
                    ApiService.GEMINI -> open("$origin/v1beta/models").apply {
                        setRequestProperty("x-goog-api-key", config.key)
                    }
                    ApiService.GROQ -> open("$origin/openai/v1/models").apply {
                        setRequestProperty("Authorization", "Bearer ${config.key}")
                    }
                    ApiService.LINKEDIN -> open("$origin/v2/userinfo").apply {
                        setRequestProperty("Authorization", "Bearer ${config.key}")
                    }
                }
                try {
                    when (val code = connection.responseCode) {
                        in 200..299 -> ConnectionStatus.Connected
                        400, 401 -> ConnectionStatus.InvalidKey
                        403 -> ConnectionStatus.NoPermission
                        else -> ConnectionStatus.Error(code)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                ConnectionStatus.Unreachable(e.message ?: e.javaClass.simpleName)
            } catch (e: IllegalArgumentException) {
                ConnectionStatus.Unreachable("Invalid API URL")
            }
        }

    /** Scheme, host and port of the configured endpoint, e.g. https://api.groq.com. */
    private fun origin(api: String): String {
        val uri = URI(api.trim())
        require(uri.scheme != null && uri.host != null)
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
    }
}

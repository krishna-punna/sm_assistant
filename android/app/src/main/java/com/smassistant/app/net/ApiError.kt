package com.smassistant.app.net

import java.io.IOException
import java.net.SocketTimeoutException

/** Why a call to LinkedIn or an AI agent failed. Shared by every client. */
sealed interface ApiError {
    /** API URL or key is missing; no request was sent. */
    data object NotConfigured : ApiError
    data object InvalidKey : ApiError
    data object NoAccess : ApiError
    data object RateLimited : ApiError
    data class Unreachable(val reason: String) : ApiError
    data class Server(val httpCode: Int, val detail: String? = null) : ApiError
    /** The service answered 2xx but the body wasn't what we expected. */
    data class BadResponse(val reason: String) : ApiError

    companion object {
        fun fromHttp(code: Int, detail: String? = null): ApiError = when (code) {
            401 -> InvalidKey
            403 -> NoAccess
            429 -> RateLimited
            else -> Server(code, detail)
        }

        fun fromException(e: IOException): ApiError = when (e) {
            is SocketTimeoutException -> Unreachable("Timed out")
            else -> Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }
}

/** A short message for the user, e.g. "Gemini rejected the API key". */
fun ApiError.userMessage(serviceName: String): String = when (this) {
    ApiError.NotConfigured -> "$serviceName isn't set up. Add the API and key in settings."
    ApiError.InvalidKey -> "$serviceName rejected the API key. Check it in settings."
    ApiError.NoAccess -> "The $serviceName key doesn't have permission for this."
    ApiError.RateLimited -> "$serviceName is rate limiting requests. Try again in a minute."
    is ApiError.Unreachable -> "Couldn't reach $serviceName: $reason"
    is ApiError.Server -> "$serviceName returned an error (HTTP $httpCode)" + (detail?.let { ": $it" } ?: "")
    is ApiError.BadResponse -> "Unexpected response from $serviceName: $reason"
}

/** Success or an [ApiError]. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Failed(val error: ApiError) : ApiResult<Nothing>
}

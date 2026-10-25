package com.smassistant.app.net

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * POSTs [body] as JSON and returns the parsed JSON response. Non-2xx answers become an
 * [ApiError] carrying the provider's `error.message` (the shape Anthropic, Google and Groq share).
 */
suspend fun OkHttpClient.postJson(url: String, headers: Map<String, String>, body: JsonElement): ApiResult<JsonObject> {
    val request = try {
        Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    } catch (e: IllegalArgumentException) {
        return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
    }
    return sendJson(request)
}

/** Sends [request] and returns the parsed JSON response, with errors mapped as for [postJson]. */
suspend fun OkHttpClient.sendJson(request: Request): ApiResult<JsonObject> {
    return try {
        newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                null
            }
            if (response.isSuccessful) {
                json?.let { ApiResult.Ok(it) } ?: ApiResult.Failed(ApiError.BadResponse("not JSON"))
            } else {
                ApiResult.Failed(ApiError.fromHttp(response.code, json?.errorMessage()))
            }
        }
    } catch (e: IOException) {
        ApiResult.Failed(ApiError.fromException(e))
    }
}

/** `error.message` from an error body, if present. */
fun JsonObject.errorMessage(): String? =
    path("error", "message")?.textOrNull

/** Follows object keys and array indexes, e.g. `path("choices", 0, "message")`; null if anything is missing. */
fun JsonElement.path(vararg keys: Any): JsonElement? = keys.fold(this as JsonElement?) { element, key ->
    when (key) {
        is String -> (element as? JsonObject)?.get(key)
        is Int -> (element as? JsonArray)?.getOrNull(key)
        else -> null
    }
}

val JsonElement.textOrNull: String? get() = (this as? JsonPrimitive)?.contentOrNull


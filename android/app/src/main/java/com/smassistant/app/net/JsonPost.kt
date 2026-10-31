package com.smassistant.app.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * POSTs [body] as JSON and returns the parsed JSON response. Non-2xx answers become an
 * [ApiError] carrying the provider's `error.message` (the shape Google and Groq share).
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

/**
 * The provider's message from an error body, if present: `error.message` (Google,
 * OpenAI-compatible), `error` as a string or `errors[0].message` (Cloudflare).
 */
fun JsonObject.errorMessage(): String? =
    path("error", "message")?.textOrNull
        ?: path("error")?.textOrNull
        ?: path("errors", 0, "message")?.textOrNull

/** A binary reply: the body and its Content-Type (null if the server sent none). */
class BinaryReply(val bytes: ByteArray, val contentType: String?)

/**
 * POSTs [body] as JSON and returns the raw reply, for APIs that answer with image bytes.
 * Non-2xx answers become an [ApiError] as for [postJson].
 */
suspend fun OkHttpClient.postForBytes(url: String, headers: Map<String, String>, body: JsonElement): ApiResult<BinaryReply> {
    val request = try {
        Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    } catch (e: IllegalArgumentException) {
        return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
    }
    return try {
        newCall(request).await().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (response.isSuccessful) {
                ApiResult.Ok(BinaryReply(bytes, response.header("Content-Type")))
            } else {
                val json = try {
                    Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                } catch (e: SerializationException) {
                    null
                }
                ApiResult.Failed(ApiError.fromHttp(response.code, json?.errorMessage()))
            }
        }
    } catch (e: IOException) {
        ApiResult.Failed(ApiError.fromException(e))
    }
}

/** Follows object keys and array indexes, e.g. `path("choices", 0, "message")`; null if anything is missing. */
fun JsonElement.path(vararg keys: Any): JsonElement? = keys.fold(this as JsonElement?) { element, key ->
    when (key) {
        is String -> (element as? JsonObject)?.get(key)
        is Int -> (element as? JsonArray)?.getOrNull(key)
        else -> null
    }
}

val JsonElement.textOrNull: String? get() = (this as? JsonPrimitive)?.contentOrNull


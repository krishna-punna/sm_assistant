package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageSize
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.extensionFor
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.postJson
import com.smassistant.app.net.textOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * How many images one Generate Image submit makes: one result, regenerated on request instead of
 * picked from several (docs/design/single-image-carousel.md, SC-I1).
 */
const val IMAGES_PER_REQUEST = 1

/** Turns a prompt into image files (docs/tasks AGC-2), with any of [IMAGE_AGENTS]. */
fun interface ImageAgent {
    /** The service named in errors, e.g. "Gemini is rate limiting requests." */
    val service: ApiService get() = ApiService.GEMINI

    suspend fun generate(prompt: String, count: Int): ApiResult<List<File>>

    /**
     * [count] images in [format] (docs/design/image-templates.md §2), or the agent's own default
     * when null. An agent that can't take a size (Cloudflare FLUX, always square) ignores it.
     */
    suspend fun generate(prompt: String, count: Int, format: ImageSize?): ApiResult<List<File>> = generate(prompt, count)

    /** The one format an agent that can't take a size paints in (Cloudflare FLUX: square), null for the others. */
    val onlyFormat: ImageSize? get() = null
}

/** The ratio Gemini's `imageConfig.aspectRatio` takes for [format]. */
internal val ImageSize.aspectRatio: String
    get() = when (this) {
        ImageSize.PORTRAIT -> "2:3"
        ImageSize.SQUARE -> "1:1"
        ImageSize.LANDSCAPE -> "3:2"
    }

/**
 * Gemini image model: POST {api}/models/{imageModel}:generateContent, one image per call, so
 * [count] calls run in parallel. Images arrive as base64 `inlineData` and are written to [cache].
 * Returns the images that worked; fails only if none did.
 */
class GeminiImageAgent(
    private val config: () -> ApiConfig,
    private val cache: MediaCache,
    private val client: OkHttpClient = Http.generationClient,
) : ImageAgent {
    override suspend fun generate(prompt: String, count: Int): ApiResult<List<File>> = generate(prompt, count, null)

    override suspend fun generate(prompt: String, count: Int, format: ImageSize?): ApiResult<List<File>> {
        val current = config()
        if (current.api.isBlank() || current.key.isBlank() || current.imageModel.isBlank()) {
            return ApiResult.Failed(ApiError.NotConfigured)
        }
        val results = coroutineScope { List(count) { async { generateOne(current, prompt, format) } }.awaitAll() }
        val files = results.filterIsInstance<ApiResult.Ok<File>>().map { it.value }
        return if (files.isNotEmpty()) ApiResult.Ok(files) else results.first() as ApiResult.Failed
    }

    private suspend fun generateOne(config: ApiConfig, prompt: String, format: ImageSize?): ApiResult<File> {
        val result = client.postJson(
            url = "${config.api.trimEnd('/')}/models/${config.imageModel}:generateContent",
            headers = mapOf("x-goog-api-key" to config.key),
            body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                    }
                }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add("TEXT")
                        add("IMAGE")
                    }
                    format?.let { putJsonObject("imageConfig") { put("aspectRatio", it.aspectRatio) } }
                }
            },
        )
        val response = when (result) {
            is ApiResult.Failed -> return ApiResult.Failed(geminiKeyError(result.error))
            is ApiResult.Ok -> result.value
        }
        val image = (response.path("candidates", 0, "content", "parts") as? JsonArray)
            ?.firstNotNullOfOrNull { part -> part.path("inlineData") ?: part.path("inline_data") }
        val data = image?.path("data")?.textOrNull
        if (data.isNullOrEmpty()) {
            val reason = response.path("promptFeedback", "blockReason")?.textOrNull
                ?: response.path("candidates", 0, "finishReason")?.textOrNull
            return ApiResult.Failed(ApiError.BadResponse("no image in the reply" + (reason?.let { " ($it)" } ?: "")))
        }
        val bytes = try {
            Base64.getDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            return ApiResult.Failed(ApiError.BadResponse("image data isn't base64"))
        }
        val mimeType = (image.path("mimeType") ?: image.path("mime_type"))?.textOrNull ?: "image/png"
        return ApiResult.Ok(cache.save(bytes, extensionFor(mimeType)))
    }
}

/** Gemini answers a bad key with 400 "API key not valid" rather than 401. */
internal fun geminiKeyError(error: ApiError) =
    if (error is ApiError.Server && error.httpCode == 400 && error.detail?.contains("API key", ignoreCase = true) == true) {
        ApiError.InvalidKey
    } else {
        error
    }

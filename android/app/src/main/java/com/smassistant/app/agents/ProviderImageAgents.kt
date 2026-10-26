package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.media.MediaCache
import com.smassistant.app.media.extensionFor
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.BinaryReply
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.postForBytes
import com.smassistant.app.net.textOrNull
import java.io.File
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * Diffusion models (FLUX, Stable Diffusion) read only the start of a prompt, and Cloudflare's
 * FLUX rejects prompts over 2,048 characters, so longer prompts are cut to this length.
 */
const val DIFFUSION_PROMPT_MAX_CHARS = 2_000

/**
 * Keeps the start of [prompt] (the house style) and its end (the topic), dropping the middle
 * (the few-shot examples), so the result fits in [maxChars].
 */
fun compactPrompt(prompt: String, maxChars: Int = DIFFUSION_PROMPT_MAX_CHARS): String {
    if (prompt.length <= maxChars) return prompt
    val gap = "\n…\n"
    val tail = (maxChars - gap.length) / 2
    val head = maxChars - gap.length - tail
    return prompt.take(head) + gap + prompt.takeLast(tail)
}

/**
 * An image agent that makes one image per call, so [count] calls run in parallel. Returns
 * the images that worked; fails only if none did.
 */
abstract class ProviderImageAgent(
    final override val service: ApiService,
    private val config: () -> ApiConfig,
    private val cache: MediaCache,
    protected val client: OkHttpClient,
) : ImageAgent {
    override suspend fun generate(prompt: String, count: Int): ApiResult<List<File>> {
        val current = config()
        if (!current.isUsableImageAgent(service)) return ApiResult.Failed(ApiError.NotConfigured)
        val short = compactPrompt(prompt)
        val results = coroutineScope { List(count) { async { generateOne(current, short) } }.awaitAll() }
        val images = results.filterIsInstance<ApiResult.Ok<ByteArray>>().map { it.value }
        if (images.isEmpty()) return results.first() as ApiResult.Failed
        return ApiResult.Ok(images.map { cache.save(it, imageExtension(it) ?: "png") })
    }

    /** One image's bytes. */
    protected abstract suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray>

    protected fun bearer(config: ApiConfig) = mapOf("Authorization" to "Bearer ${config.key}")
}

/**
 * Hugging Face text-to-image on the hf-inference provider:
 * POST https://router.huggingface.co/hf-inference/models/{imageModel} with `{"inputs": prompt}`.
 * The reply is the image bytes.
 */
class HuggingFaceImageAgent(
    config: () -> ApiConfig,
    cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
) : ProviderImageAgent(ApiService.HUGGINGFACE, config, cache, client) {
    override suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray> {
        val origin = config.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build()
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val url = origin.newBuilder().addPathSegments("hf-inference/models").addPathSegments(config.imageModel.trim()).build()
        val reply = client.postForBytes(
            url = url.toString(),
            headers = bearer(config) + ("Accept" to "image/png"),
            body = buildJsonObject { put("inputs", prompt) },
        )
        return when (reply) {
            is ApiResult.Failed -> reply
            is ApiResult.Ok -> imageBytes(reply.value)
        }
    }
}

/**
 * Cloudflare Workers AI: POST {api}/run/{imageModel} with `{"prompt": prompt}`. FLUX answers
 * JSON with a base64 `result.image`; Stable Diffusion answers with the PNG bytes.
 */
class CloudflareImageAgent(
    config: () -> ApiConfig,
    cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
) : ProviderImageAgent(ApiService.CLOUDFLARE, config, cache, client) {
    override suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray> {
        val reply = client.postForBytes(
            url = "${config.apiUrl.trimEnd('/')}/run/${config.imageModel.trim()}",
            headers = bearer(config),
            body = buildJsonObject { put("prompt", prompt) },
        )
        return when (reply) {
            is ApiResult.Failed -> reply
            is ApiResult.Ok -> if (reply.value.isJson()) {
                base64Image(reply.value.json()?.path("result", "image")?.textOrNull)
            } else {
                imageBytes(reply.value)
            }
        }
    }
}

/**
 * Pollinations.AI, OpenAI-compatible: POST https://gen.pollinations.ai/v1/images/generations,
 * asking for `b64_json`.
 */
class PollinationsImageAgent(
    config: () -> ApiConfig,
    cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
) : ProviderImageAgent(ApiService.POLLINATIONS, config, cache, client) {
    override suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray> {
        val origin = config.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build()
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val reply = client.postForBytes(
            url = origin.resolve("/v1/images/generations").toString(),
            headers = bearer(config),
            body = buildJsonObject {
                put("model", config.imageModel.trim())
                put("prompt", prompt)
                put("n", 1)
                put("size", "1024x1024")
                put("response_format", "b64_json")
            },
        )
        return when (reply) {
            is ApiResult.Failed -> reply
            is ApiResult.Ok -> base64Image(reply.value.json()?.path("data", 0, "b64_json")?.textOrNull)
        }
    }
}

private fun BinaryReply.isJson() = contentType?.contains("json", ignoreCase = true) == true

private fun BinaryReply.json(): JsonObject? = try {
    Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
} catch (e: SerializationException) {
    null
}

/** A reply that should be the image itself. */
private fun imageBytes(reply: BinaryReply): ApiResult<ByteArray> = when {
    reply.bytes.isEmpty() -> ApiResult.Failed(ApiError.BadResponse("no image in the reply"))
    reply.contentType?.startsWith("image/", ignoreCase = true) == true || imageExtension(reply.bytes) != null ->
        ApiResult.Ok(reply.bytes)
    else -> ApiResult.Failed(ApiError.BadResponse("no image in the reply"))
}

private fun base64Image(data: String?): ApiResult<ByteArray> {
    if (data.isNullOrEmpty()) return ApiResult.Failed(ApiError.BadResponse("no image in the reply"))
    return try {
        ApiResult.Ok(Base64.getDecoder().decode(data.substringAfter("base64,")))
    } catch (e: IllegalArgumentException) {
        ApiResult.Failed(ApiError.BadResponse("image data isn't base64"))
    }
}

/** The file extension for an image's bytes (PNG, JPEG or WebP), or null if they aren't one of those. */
internal fun imageExtension(bytes: ByteArray): String? = when {
    bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> extensionFor("image/png")
    bytes.startsWith(0xFF, 0xD8) -> extensionFor("image/jpeg")
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" && bytes.copyOfRange(8, 12).decodeToString() == "WEBP" ->
        extensionFor("image/webp")
    else -> null
}

private fun ByteArray.startsWith(vararg prefix: Int) =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

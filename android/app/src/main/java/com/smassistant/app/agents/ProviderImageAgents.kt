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
import com.smassistant.app.net.postJson
import com.smassistant.app.net.textOrNull
import java.io.File
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
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

/** The Hugging Face providers this app can call for text-to-image, as the Hub names them. */
internal val HUGGING_FACE_IMAGE_PROVIDERS = listOf("hf-inference", "fal-ai", "together")

/**
 * Hugging Face text-to-image through the router (https://router.huggingface.co/{provider}/...).
 * The Hub says which providers serve the model, in the user's preferred order ([HuggingFaceRouter]);
 * the first live one this app supports is used, else hf-inference:
 * - hf-inference: POST /hf-inference/models/{model} with `{"inputs": prompt}`; the reply is the image.
 * - fal-ai (e.g. Qwen/Qwen-Image): a queued request ([HuggingFaceRouter.falAi]), then `images[0].url` downloaded.
 * - together: POST /together/v1/images/generations, asking for base64.
 */
class HuggingFaceImageAgent(
    config: () -> ApiConfig,
    cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
    pollDelayMs: Long = 1_000,
) : ProviderImageAgent(ApiService.HUGGINGFACE, config, cache, client) {
    private val router = HuggingFaceRouter(client, pollDelayMs)
    private val providers = mutableMapOf<String, HfProvider>()
    private val providerLock = Mutex()

    override suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray> {
        val origin = config.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build()
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val model = config.imageModel.trim()
        val target = providerLock.withLock {
            providers.getOrPut(model) {
                router.provider(origin, config.key, model, "text-to-image", HUGGING_FACE_IMAGE_PROVIDERS)
                    ?: HfProvider("hf-inference", model)
            }
        }
        return when (target.provider) {
            "fal-ai" -> falAi(origin, config, target.providerId, prompt)
            "together" -> together(origin, config, target.providerId, prompt)
            else -> hfInference(origin, config, model, prompt)
        }
    }

    private suspend fun hfInference(origin: HttpUrl, config: ApiConfig, model: String, prompt: String): ApiResult<ByteArray> {
        val url = origin.newBuilder().addPathSegments("hf-inference/models").addPathSegments(model).build()
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

    private suspend fun falAi(origin: HttpUrl, config: ApiConfig, providerId: String, prompt: String): ApiResult<ByteArray> {
        val result = when (val reply = router.falAi(origin, config.key, providerId, buildJsonObject { put("prompt", prompt) })) {
            is ApiResult.Failed -> return reply
            is ApiResult.Ok -> reply.value
        }
        val imageUrl = result.path("images", 0, "url")?.textOrNull
            ?: return ApiResult.Failed(ApiError.BadResponse("no image in the reply"))
        if (imageUrl.startsWith("data:")) return base64Image(imageUrl)
        return when (val file = router.download(imageUrl)) {
            is ApiResult.Failed -> file
            is ApiResult.Ok -> imageBytes(file.value)
        }
    }

    private suspend fun together(origin: HttpUrl, config: ApiConfig, providerId: String, prompt: String): ApiResult<ByteArray> {
        val url = origin.newBuilder().addPathSegments("together/v1/images/generations").build()
        val body = buildJsonObject {
            put("model", providerId)
            put("prompt", prompt)
            put("response_format", "base64")
        }
        return when (val reply = client.postJson(url.toString(), bearer(config), body)) {
            is ApiResult.Failed -> reply
            is ApiResult.Ok -> base64Image(reply.value.path("data", 0, "b64_json")?.textOrNull)
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

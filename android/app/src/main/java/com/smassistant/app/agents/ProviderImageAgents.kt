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
import com.smassistant.app.data.huggingFaceHub
import com.smassistant.app.net.await
import com.smassistant.app.net.postForBytes
import com.smassistant.app.net.postJson
import com.smassistant.app.net.sendJson
import com.smassistant.app.net.textOrNull
import java.io.File
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

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

/** Where Hugging Face serves a model: the provider and the model's id there. */
internal data class HfProvider(val provider: String, val providerId: String)

/**
 * Hugging Face text-to-image through the router (https://router.huggingface.co/{provider}/...).
 * The Hub says which providers serve the model, in the user's preferred order
 * (`GET https://huggingface.co/api/models/{model}?expand[]=inferenceProviderMapping`); the first
 * live one this app supports is used, else hf-inference:
 * - hf-inference: POST /hf-inference/models/{model} with `{"inputs": prompt}`; the reply is the image.
 * - fal-ai (e.g. Qwen/Qwen-Image): POST /fal-ai/{providerId}?_subdomain=queue, poll the queued
 *   request until COMPLETED, then download `images[0].url`.
 * - together: POST /together/v1/images/generations, asking for base64.
 */
class HuggingFaceImageAgent(
    config: () -> ApiConfig,
    cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
    private val pollDelayMs: Long = 1_000,
    private val maxPolls: Int = 240,
) : ProviderImageAgent(ApiService.HUGGINGFACE, config, cache, client) {
    private val providers = mutableMapOf<String, HfProvider>()
    private val providerLock = Mutex()

    override suspend fun generateOne(config: ApiConfig, prompt: String): ApiResult<ByteArray> {
        val origin = config.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build()
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val model = config.imageModel.trim()
        val target = providerLock.withLock { providers.getOrPut(model) { findProvider(origin, config, model) } }
        return when (target.provider) {
            "fal-ai" -> falAi(origin, config, target.providerId, prompt)
            "together" -> together(origin, config, target.providerId, prompt)
            else -> hfInference(origin, config, model, prompt)
        }
    }

    /** The first live text-to-image provider for [model] that this app supports; hf-inference if none or the lookup fails. */
    private suspend fun findProvider(origin: HttpUrl, config: ApiConfig, model: String): HfProvider {
        val fallback = HfProvider("hf-inference", model)
        val url = huggingFaceHub(origin).newBuilder().encodedPath("/api/models").addPathSegments(model)
            .addQueryParameter("expand[]", "inferenceProviderMapping").build()
        val json = when (val reply = client.sendJson(Request.Builder().url(url).headers(bearer(config).toHeaders()).build())) {
            is ApiResult.Failed -> return fallback
            is ApiResult.Ok -> reply.value
        }
        return providerMappings(json, model).firstOrNull { (provider, providerId, status, task) ->
            provider in HUGGING_FACE_IMAGE_PROVIDERS && status == "live" && task == "text-to-image" && providerId.isNotBlank()
        }?.let { HfProvider(it.provider, it.providerId) } ?: fallback
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
        val submit = origin.newBuilder().addPathSegment("fal-ai").addPathSegments(providerId)
            .addQueryParameter("_subdomain", "queue").build()
        val queued = when (val reply = client.postJson(submit.toString(), bearer(config), buildJsonObject { put("prompt", prompt) })) {
            is ApiResult.Failed -> return reply
            is ApiResult.Ok -> reply.value
        }
        val responseUrl = queued.path("response_url")?.textOrNull?.toHttpUrlOrNull()
            ?: return ApiResult.Failed(ApiError.BadResponse("no queued request in the reply"))
        // As the Hugging Face client does: through the router, the queue's paths sit under /fal-ai.
        val base = responseUrl.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/') +
            (if (responseUrl.host == "router.huggingface.co") "/fal-ai" else "")
        val query = responseUrl.encodedQuery?.let { "?$it" }.orEmpty()
        val request = base + responseUrl.encodedPath
        repeat(maxPolls) {
            val status = when (val reply = client.sendJson(get("$request/status$query", config))) {
                is ApiResult.Failed -> return reply
                is ApiResult.Ok -> reply.value.path("status")?.textOrNull
            }
            if (status == "COMPLETED") {
                val result = when (val reply = client.sendJson(get("$request$query", config))) {
                    is ApiResult.Failed -> return reply
                    is ApiResult.Ok -> reply.value
                }
                val imageUrl = result.path("images", 0, "url")?.textOrNull
                    ?: return ApiResult.Failed(ApiError.BadResponse("no image in the reply"))
                return if (imageUrl.startsWith("data:")) base64Image(imageUrl) else download(imageUrl)
            }
            if (status != "IN_QUEUE" && status != "IN_PROGRESS") {
                return ApiResult.Failed(ApiError.BadResponse("image request ${status?.lowercase() ?: "failed"}"))
            }
            delay(pollDelayMs)
        }
        return ApiResult.Failed(ApiError.Unreachable("Timed out"))
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

    private fun get(url: String, config: ApiConfig) = Request.Builder().url(url).headers(bearer(config).toHeaders()).build()

    /** The finished image, from the provider's own storage (no Hugging Face token). */
    private suspend fun download(url: String): ApiResult<ByteArray> {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            return ApiResult.Failed(ApiError.BadResponse("image URL isn't valid"))
        }
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return ApiResult.Failed(ApiError.fromHttp(response.code))
                imageBytes(BinaryReply(response.body?.bytes() ?: ByteArray(0), response.header("Content-Type")))
            }
        } catch (e: IOException) {
            ApiResult.Failed(ApiError.fromException(e))
        }
    }
}

internal data class HfMapping(val provider: String, val providerId: String, val status: String?, val task: String?)

/** The Hub's `inferenceProviderMapping`, which is either a list of mappings or an object keyed by provider. */
internal fun providerMappings(json: JsonObject, model: String): List<HfMapping> =
    when (val mapping = json["inferenceProviderMapping"]) {
        is JsonArray -> mapping.filterIsInstance<JsonObject>().mapNotNull { m ->
            m.path("provider")?.textOrNull?.let { HfMapping(it, m.path("providerId")?.textOrNull.orEmpty(), m.path("status")?.textOrNull, m.path("task")?.textOrNull) }
        }
        is JsonObject -> mapping.map { (provider, m) ->
            HfMapping(provider, m.path("providerId")?.textOrNull ?: model, m.path("status")?.textOrNull, m.path("task")?.textOrNull)
        }
        else -> emptyList()
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

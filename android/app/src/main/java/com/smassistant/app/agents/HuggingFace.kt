package com.smassistant.app.agents

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.huggingFaceHub
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.BinaryReply
import com.smassistant.app.net.Http
import com.smassistant.app.net.await
import com.smassistant.app.net.path
import com.smassistant.app.net.postJson
import com.smassistant.app.net.sendJson
import com.smassistant.app.net.textOrNull
import java.io.File
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Where Hugging Face serves a model: the provider and the model's id there. */
internal data class HfProvider(val provider: String, val providerId: String)

internal data class HfMapping(val provider: String, val providerId: String, val status: String?, val task: String?)

/**
 * The Hugging Face router calls that the image and video agents share, following the official
 * huggingface.js client: which providers serve a model, fal-ai's queue, and downloading results.
 */
internal class HuggingFaceRouter(
    private val client: OkHttpClient,
    private val pollDelayMs: Long = 1_000,
    private val maxPolls: Int = 600,
) {
    /**
     * The first live provider in [supported] that serves [model] for [task], in the user's preferred
     * order (`GET https://huggingface.co/api/models/{model}?expand[]=inferenceProviderMapping`).
     * Null if none does or the lookup fails.
     */
    suspend fun provider(origin: HttpUrl, key: String, model: String, task: String, supported: List<String>): HfProvider? {
        val url = huggingFaceHub(origin).newBuilder().encodedPath("/api/models").addPathSegments(model)
            .addQueryParameter("expand[]", "inferenceProviderMapping").build()
        val json = when (val reply = client.sendJson(get(url.toString(), key))) {
            is ApiResult.Failed -> return null
            is ApiResult.Ok -> reply.value
        }
        return providerMappings(json, model).firstOrNull { (provider, providerId, status, mappedTask) ->
            provider in supported && status == "live" && mappedTask == task && providerId.isNotBlank()
        }?.let { HfProvider(it.provider, it.providerId) }
    }

    /**
     * fal-ai through the router: POST /fal-ai/{providerId}?_subdomain=queue with [body], poll the
     * queued request's `/status` until COMPLETED, then return its result JSON.
     */
    suspend fun falAi(origin: HttpUrl, key: String, providerId: String, body: JsonObject): ApiResult<JsonObject> {
        val submit = origin.newBuilder().addPathSegment("fal-ai").addPathSegments(providerId)
            .addQueryParameter("_subdomain", "queue").build()
        val queued = when (val reply = client.postJson(submit.toString(), bearer(key), body)) {
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
            val status = when (val reply = client.sendJson(get("$request/status$query", key))) {
                is ApiResult.Failed -> return reply
                is ApiResult.Ok -> reply.value.path("status")?.textOrNull
            }
            if (status == "COMPLETED") return client.sendJson(get("$request$query", key))
            if (status != "IN_QUEUE" && status != "IN_PROGRESS") {
                return ApiResult.Failed(ApiError.BadResponse("the request ${status?.lowercase() ?: "failed"}"))
            }
            delay(pollDelayMs)
        }
        return ApiResult.Failed(ApiError.Unreachable("Timed out"))
    }

    /** A finished file from the provider's own storage (no Hugging Face token). */
    suspend fun download(url: String): ApiResult<BinaryReply> {
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            return ApiResult.Failed(ApiError.BadResponse("the result URL isn't valid"))
        }
        return try {
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) return ApiResult.Failed(ApiError.fromHttp(response.code))
                ApiResult.Ok(BinaryReply(response.body?.bytes() ?: ByteArray(0), response.header("Content-Type")))
            }
        } catch (e: IOException) {
            ApiResult.Failed(ApiError.fromException(e))
        }
    }

    private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

    private fun get(url: String, key: String) = Request.Builder().url(url).header("Authorization", "Bearer $key").build()
}

/** The Hub's `inferenceProviderMapping`, which is either a list of mappings or an object keyed by provider. */
internal fun providerMappings(json: JsonObject, model: String): List<HfMapping> =
    when (val mapping = json["inferenceProviderMapping"]) {
        is JsonArray -> mapping.filterIsInstance<JsonObject>().mapNotNull { m ->
            m.path("provider")?.textOrNull?.let {
                HfMapping(it, m.path("providerId")?.textOrNull.orEmpty(), m.path("status")?.textOrNull, m.path("task")?.textOrNull)
            }
        }
        is JsonObject -> mapping.map { (provider, m) ->
            HfMapping(provider, m.path("providerId")?.textOrNull ?: model, m.path("status")?.textOrNull, m.path("task")?.textOrNull)
        }
        else -> emptyList()
    }

/** The Hugging Face providers this app can call for text-to-video, as the Hub names them. */
internal val HUGGING_FACE_VIDEO_PROVIDERS = listOf("fal-ai")

/** Makes a short video clip from a prompt with a text-to-video model, instead of a slideshow. */
interface VideoClipAgent {
    val service: ApiService

    /** The clip, or null when the chosen model isn't a text-to-video model, so a slideshow is made instead. */
    suspend fun generate(prompt: String): ApiResult<File>?
}

/**
 * Hugging Face text-to-video (e.g. Wan-AI/Wan2.2-T2V-A14B) on fal-ai through the router:
 * a queued request ([HuggingFaceRouter.falAi]) with `{"prompt": prompt}`, then `video.url` downloaded
 * as an MP4. The model is the video model from the settings ([ApiConfig.videoModel]).
 */
class HuggingFaceVideoAgent(
    private val config: () -> ApiConfig,
    private val cache: MediaCache,
    client: OkHttpClient = Http.generationClient,
    pollDelayMs: Long = 2_000,
) : VideoClipAgent {
    override val service = ApiService.HUGGINGFACE
    private val router = HuggingFaceRouter(client, pollDelayMs)

    override suspend fun generate(prompt: String): ApiResult<File>? {
        val current = config()
        val model = current.videoModel.trim()
        if (!current.isConfigured() || model.isEmpty()) return null
        val origin = current.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.build()
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val target = router.provider(origin, current.key, model, "text-to-video", HUGGING_FACE_VIDEO_PROVIDERS) ?: return null
        val result = when (val reply = router.falAi(origin, current.key, target.providerId, buildJsonObject { put("prompt", prompt) })) {
            is ApiResult.Failed -> return reply
            is ApiResult.Ok -> reply.value
        }
        val videoUrl = result.path("video", "url")?.textOrNull
            ?: return ApiResult.Failed(ApiError.BadResponse("no video in the reply"))
        return when (val file = router.download(videoUrl)) {
            is ApiResult.Failed -> file
            is ApiResult.Ok -> if (file.value.bytes.isEmpty()) {
                ApiResult.Failed(ApiError.BadResponse("the video is empty"))
            } else {
                ApiResult.Ok(cache.newFile("mp4").apply { writeBytes(file.value.bytes) })
            }
        }
    }
}

package com.smassistant.app.data

import com.smassistant.app.agents.HUGGING_FACE_IMAGE_PROVIDERS
import com.smassistant.app.agents.HUGGING_FACE_VIDEO_PROVIDERS
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.sendJson
import com.smassistant.app.net.sendJsonArray
import com.smassistant.app.net.textOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One model a provider offers: [id] goes in requests, [label] is what the picker shows. */
data class AiModel(val id: String, val label: String = id)

/**
 * The models an agent can use for text and, for the agents that make images, for images. [video]
 * holds text-to-video models (Hugging Face), which make a clip instead of a slideshow.
 */
data class ModelLists(
    val text: List<AiModel> = emptyList(),
    val image: List<AiModel> = emptyList(),
    val video: List<AiModel> = emptyList(),
)

/**
 * Lists the models each AI agent offers, from the provider's own models endpoint (the same
 * request the API Status check sends), so new models show up without an app update.
 */
open class ModelCatalog(private val client: OkHttpClient = Http.client) {
    open suspend fun fetch(service: ApiService, config: ApiConfig): ApiResult<ModelLists> {
        if (!config.isConfigured()) return ApiResult.Failed(ApiError.NotConfigured)
        val request = listRequest(service, config) ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        val lists = when (val result = client.sendJson(request)) {
            is ApiResult.Failed -> return result
            is ApiResult.Ok -> parse(service, result.value)
        }
        // Hugging Face's image and video models come from the Hub; if those lists fail, the text models still help.
        val withImages = if (service == ApiService.HUGGINGFACE) {
            lists.copy(
                image = hubModels(config, "text-to-image", HUGGING_FACE_IMAGE_PROVIDERS),
                video = hubModels(config, "text-to-video", HUGGING_FACE_VIDEO_PROVIDERS),
            )
        } else {
            lists
        }
        return withImages
            .takeIf { it.text.isNotEmpty() || it.image.isNotEmpty() || it.video.isNotEmpty() }
            ?.let { ApiResult.Ok(it) }
            ?: ApiResult.Failed(ApiError.BadResponse("no models in the reply"))
    }

    /** The provider's models endpoint: the API Status check request, or its own where that check is something else. */
    private fun listRequest(service: ApiService, config: ApiConfig): Request? {
        val bearer = "Bearer ${config.key}"
        return when (service) {
            ApiService.HUGGINGFACE, ApiService.POLLINATIONS -> origin(config)?.resolve("/v1/models")
                ?.let { Request.Builder().url(it).header("Authorization", bearer).build() }
            ApiService.CLOUDFLARE -> config.apiUrl.trimEnd('/').toHttpUrlOrNull()?.newBuilder()
                ?.addPathSegments("models/search")?.addQueryParameter("per_page", "1000")?.build()
                ?.let { Request.Builder().url(it).header("Authorization", bearer).build() }
            else -> {
                val check = ApiConnectionCheck(client).request(service, config) ?: return null
                // Gemini pages its list at 50 by default; ask for all of them at once.
                if (service == ApiService.GEMINI) {
                    check.newBuilder().url(check.url.newBuilder().addQueryParameter("pageSize", "1000").build()).build()
                } else {
                    check
                }
            }
        }
    }

    /** [task] models (e.g. text-to-image) served by any of [providers] on Hugging Face, most liked first; empty if the Hub fails. */
    private suspend fun hubModels(config: ApiConfig, task: String, providers: List<String>): List<AiModel> {
        val hub = origin(config)?.let(::huggingFaceHub) ?: return emptyList()
        val url = hub.newBuilder().encodedPath("/api/models")
            .addQueryParameter("pipeline_tag", task)
            .addQueryParameter("inference_provider", providers.joinToString(","))
            .addQueryParameter("sort", "likes")
            .addQueryParameter("limit", "100")
            .build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${config.key}").build()
        return when (val reply = client.sendJsonArray(request)) {
            is ApiResult.Ok -> huggingFaceModels(reply.value)
            is ApiResult.Failed -> emptyList()
        }
    }

    private fun origin(config: ApiConfig): HttpUrl? =
        config.apiUrl.toHttpUrlOrNull()?.newBuilder()?.encodedPath("/")?.query(null)?.fragment(null)?.build()

    internal fun huggingFaceModels(json: JsonArray): List<AiModel> =
        json.filterIsInstance<JsonObject>().mapNotNull { m -> m.path("id")?.textOrNull?.let { AiModel(it) } }

    internal fun parse(service: ApiService, json: JsonObject): ModelLists = when (service) {
        ApiService.CLAUDE -> ModelLists(
            text = entries(json, "data").mapNotNull { m ->
                m.path("id")?.textOrNull?.let { AiModel(it, m.path("display_name")?.textOrNull ?: it) }
            }.sortedBy { it.id },
        )
        ApiService.GEMINI -> {
            val models = entries(json, "models").mapNotNull { m ->
                val methods = (m.path("supportedGenerationMethods") as? JsonArray)?.mapNotNull { it.textOrNull }.orEmpty()
                val id = m.path("name")?.textOrNull?.removePrefix("models/")
                if (id == null || "generateContent" !in methods) null else AiModel(id, m.path("displayName")?.textOrNull ?: id)
            }.sortedBy { it.id }
            val (image, text) = models.partition { "image" in it.id }
            ModelLists(text = text.filterNot { "tts" in it.id || "embedding" in it.id }, image = image)
        }
        // Groq also serves speech models from this list; they can't write posts.
        ApiService.GROQ -> ModelLists(
            text = entries(json, "data").mapNotNull { it.path("id")?.textOrNull }
                .filterNot { "whisper" in it || "tts" in it }
                .sorted()
                .map { AiModel(it) },
        )
        // Hugging Face and Pollinations.AI list OpenAI-style `data[].id`, with output modalities.
        ApiService.HUGGINGFACE -> ModelLists(text = openAiModels(json, "text"))
        ApiService.POLLINATIONS -> ModelLists(text = openAiModels(json, "text"), image = openAiModels(json, "image"))
        ApiService.CLOUDFLARE -> {
            val byTask = entries(json, "result").groupBy({ it.path("task", "name")?.textOrNull }) { m ->
                m.path("name")?.textOrNull
            }
            ModelLists(
                text = byTask["Text Generation"].orEmpty().filterNotNull().sorted().map { AiModel(it) },
                image = byTask["Text-to-Image"].orEmpty().filterNotNull().sorted().map { AiModel(it) },
            )
        }
        ApiService.LINKEDIN -> ModelLists()
    }

    /**
     * `data[].id` of the models whose `architecture.output_modalities` (Hugging Face) or
     * `output_modalities` (Pollinations.AI) include [modality]. Entries that don't say are text models.
     */
    private fun openAiModels(json: JsonObject, modality: String): List<AiModel> = entries(json, "data").mapNotNull { m ->
        val id = m.path("id")?.textOrNull ?: return@mapNotNull null
        val outputs = ((m.path("output_modalities") ?: m.path("architecture", "output_modalities")) as? JsonArray)
            ?.mapNotNull { it.textOrNull }
        val produces = outputs?.contains(modality) ?: (modality == "text")
        if (produces) AiModel(id, m.path("title")?.textOrNull ?: id) else null
    }.sortedBy { it.id }

    private fun entries(json: JsonObject, key: String): List<JsonObject> =
        (json[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
}

package com.smassistant.app.data

import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.Http
import com.smassistant.app.net.path
import com.smassistant.app.net.sendJson
import com.smassistant.app.net.textOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient

/** One model a provider offers: [id] goes in requests, [label] is what the picker shows. */
data class AiModel(val id: String, val label: String = id)

/** The models an agent can use for text, and for Gemini also for images. */
data class ModelLists(val text: List<AiModel> = emptyList(), val image: List<AiModel> = emptyList())

/**
 * Lists the models each AI agent offers, from the provider's own models endpoint (the same
 * request the API Status check sends), so new models show up without an app update.
 */
open class ModelCatalog(private val client: OkHttpClient = Http.client) {
    open suspend fun fetch(service: ApiService, config: ApiConfig): ApiResult<ModelLists> {
        if (config.api.isBlank() || config.key.isBlank()) return ApiResult.Failed(ApiError.NotConfigured)
        val check = ApiConnectionCheck(client).request(service, config)
            ?: return ApiResult.Failed(ApiError.Unreachable("Invalid API URL"))
        // Gemini pages its list at 50 by default; ask for all of them at once.
        val request = if (service == ApiService.GEMINI) {
            check.newBuilder().url(check.url.newBuilder().addQueryParameter("pageSize", "1000").build()).build()
        } else {
            check
        }
        return when (val result = client.sendJson(request)) {
            is ApiResult.Failed -> result
            is ApiResult.Ok -> parse(service, result.value)
                .takeIf { it.text.isNotEmpty() || it.image.isNotEmpty() }
                ?.let { ApiResult.Ok(it) }
                ?: ApiResult.Failed(ApiError.BadResponse("no models in the reply"))
        }
    }

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
        ApiService.LINKEDIN -> ModelLists()
    }

    private fun entries(json: JsonObject, key: String): List<JsonObject> =
        (json[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
}

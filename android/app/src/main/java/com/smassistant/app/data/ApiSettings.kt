package com.smassistant.app.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Defaults bundled in `assets/app.yml` (see [parseAppConfig]). */
object AppConfig {
    @Volatile private var cached: Map<ApiService, ApiConfig>? = null

    fun defaults(context: Context, service: ApiService): ApiConfig =
        (cached ?: context.assets.open("app.yml").use(::parseAppConfig).also { cached = it }).getValue(service)

    @Volatile private var cachedRules: PostRules? = null

    fun postRules(context: Context): PostRules =
        cachedRules ?: context.assets.open("app.yml").use(::parsePostRules).also { cachedRules = it }
}

/**
 * API settings for each service: values the user saved on a settings screen,
 * falling back to the defaults in app.yml. Keys are encrypted with [cipher];
 * URLs and model choices are stored as plain text.
 */
class ApiSettingsStore(context: Context, private val cipher: SecretCipher = KeystoreCipher()) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    fun load(service: ApiService): ApiConfig {
        val defaults = AppConfig.defaults(appContext, service)
        return defaults.copy(
            api = prefs.getString(apiPref(service), null) ?: defaults.api,
            key = savedKey(service) ?: defaults.key,
            model = prefs.getString(modelPref(service), null) ?: defaults.model,
            imageModel = prefs.getString(imageModelPref(service), null) ?: defaults.imageModel,
        )
    }

    /** Saves the chosen models. A blank one removes the override so the app.yml default applies again. */
    fun saveModels(service: ApiService, model: String, imageModel: String = "") {
        prefs.edit().apply {
            if (model.isBlank()) remove(modelPref(service)) else putString(modelPref(service), model.trim())
            if (imageModel.isBlank()) remove(imageModelPref(service)) else putString(imageModelPref(service), imageModel.trim())
        }.apply()
    }

    /** The model lists last fetched for [service] and when, so the picker has options before the next fetch. */
    fun cachedModels(service: ApiService): CachedModels? = runCatching {
        val json = Json.parseToJsonElement(prefs.getString(modelsPref(service), null) ?: return null) as JsonObject
        CachedModels(
            ModelLists(models(json["text"]), models(json["image"])),
            fetchedAt = json.getValue("fetchedAt").jsonPrimitive.content.toLong(),
        )
    }.getOrNull()

    fun saveCachedModels(service: ApiService, lists: ModelLists, fetchedAt: Long = System.currentTimeMillis()) {
        val json = buildJsonObject {
            put("text", models(lists.text))
            put("image", models(lists.image))
            put("fetchedAt", fetchedAt.toString())
        }
        prefs.edit().putString(modelsPref(service), json.toString()).apply()
    }

    private fun models(list: List<AiModel>) =
        JsonArray(list.map { JsonArray(listOf(JsonPrimitive(it.id), JsonPrimitive(it.label))) })

    private fun models(json: JsonElement?): List<AiModel> = (json as? JsonArray).orEmpty().map {
        val pair = it as JsonArray
        AiModel(pair[0].jsonPrimitive.content, pair[1].jsonPrimitive.content)
    }

    /** Saves both values, trimmed. A blank [api] removes the override so the app.yml default applies again. */
    fun save(service: ApiService, api: String, key: String) {
        prefs.edit().apply {
            if (api.isBlank()) remove(apiPref(service)) else putString(apiPref(service), api.trim())
            putString(encryptedKeyPref(service), cipher.encrypt(key.trim()))
            remove(legacyKeyPref(service))
        }.apply()
    }

    private fun savedKey(service: ApiService): String? {
        prefs.getString(encryptedKeyPref(service), null)?.let { return cipher.decrypt(it) }
        // Builds before encryption stored the key in plain text: encrypt it once and drop the plain copy.
        val legacy = prefs.getString(legacyKeyPref(service), null) ?: return null
        prefs.edit()
            .putString(encryptedKeyPref(service), cipher.encrypt(legacy))
            .remove(legacyKeyPref(service))
            .apply()
        return legacy
    }

    private fun apiPref(service: ApiService) = "${service.name}.api"
    private fun modelPref(service: ApiService) = "${service.name}.model"
    private fun imageModelPref(service: ApiService) = "${service.name}.image_model"
    private fun modelsPref(service: ApiService) = "${service.name}.models"
    private fun encryptedKeyPref(service: ApiService) = "${service.name}.key_enc"
    private fun legacyKeyPref(service: ApiService) = "${service.name}.key"
}

data class CachedModels(val lists: ModelLists, val fetchedAt: Long)

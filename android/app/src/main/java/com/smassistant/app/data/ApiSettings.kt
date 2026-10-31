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

    @Volatile private var cachedImageChecks: ImageCheckRules? = null

    fun imageChecks(context: Context): ImageCheckRules =
        cachedImageChecks ?: context.assets.open("app.yml").use(::parseImageCheckRules).also { cachedImageChecks = it }

    @Volatile private var cachedImageStyle: ImageStyle? = null

    fun imageStyle(context: Context): ImageStyle =
        cachedImageStyle ?: context.assets.open("app.yml").use(::parseImageStyle).also { cachedImageStyle = it }

    @Volatile private var cachedVideoStyle: VideoStyle? = null

    fun videoStyle(context: Context): VideoStyle =
        cachedVideoStyle ?: context.assets.open("app.yml").use(::parseVideoStyle).also { cachedVideoStyle = it }
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
            videoModel = prefs.getString(videoModelPref(service), null) ?: defaults.videoModel,
            accountId = prefs.getString(accountIdPref(service), null) ?: defaults.accountId,
        )
    }

    /**
     * [load] with the text model chosen for writing image or video content ([purpose], SP-12): each
     * keeps its own, and until one is chosen it is the agent's text model. A null [purpose] is [load].
     */
    fun loadForText(service: ApiService, purpose: MediaPurpose?): ApiConfig {
        val config = load(service)
        if (purpose == null) return config
        return prefs.getString(textModelPref(service, purpose), null)?.let { config.copy(model = it) } ?: config
    }

    /** Saves the chosen models. A blank one removes the override so the app.yml default applies again. */
    fun saveModels(service: ApiService, model: String, imageModel: String = "", videoModel: String = "") {
        prefs.edit().apply {
            if (model.isBlank()) remove(modelPref(service)) else putString(modelPref(service), model.trim())
            if (imageModel.isBlank()) remove(imageModelPref(service)) else putString(imageModelPref(service), imageModel.trim())
            if (videoModel.isBlank()) remove(videoModelPref(service)) else putString(videoModelPref(service), videoModel.trim())
        }.apply()
    }

    /** Saves just the text model: the agent's own, or the image or video one ([purpose]) from the pickers on those screens. */
    fun saveTextModel(service: ApiService, model: String, purpose: MediaPurpose? = null) {
        val pref = purpose?.let { textModelPref(service, it) } ?: modelPref(service)
        prefs.edit().apply { if (model.isBlank()) remove(pref) else putString(pref, model.trim()) }.apply()
    }

    /** Saves just the image or video model, e.g. from the picker on the image and video screens. */
    fun saveMediaModel(service: ApiService, purpose: MediaPurpose, model: String) {
        val pref = if (purpose == MediaPurpose.VIDEO) videoModelPref(service) else imageModelPref(service)
        prefs.edit().apply { if (model.isBlank()) remove(pref) else putString(pref, model.trim()) }.apply()
    }

    /** Saves the account id (Cloudflare). A blank one removes the override so the app.yml default applies again. */
    fun saveAccountId(service: ApiService, accountId: String) {
        prefs.edit().apply {
            if (accountId.isBlank()) remove(accountIdPref(service)) else putString(accountIdPref(service), accountId.trim())
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

    /**
     * Saves both values, trimmed. A blank [api] removes the override so the app.yml default applies again.
     * The last connection check no longer applies to the new values, so it's forgotten.
     */
    fun save(service: ApiService, api: String, key: String) {
        prefs.edit().apply {
            if (api.isBlank()) remove(apiPref(service)) else putString(apiPref(service), api.trim())
            putString(encryptedKeyPref(service), cipher.encrypt(key.trim()))
            remove(legacyKeyPref(service))
            remove(lastStatusPref(service))
        }.apply()
    }

    /** The result of the last connection check (API Status or a settings Test), or null when there's none since the last save. */
    fun lastStatus(service: ApiService): ConnectionStatus? = decodeStatus(prefs.getString(lastStatusPref(service), null))

    /** Remembers [status] for the agent warnings (GEN-6); Checking is never stored. */
    fun saveLastStatus(service: ApiService, status: ConnectionStatus) {
        val encoded = encodeStatus(status) ?: return
        prefs.edit().putString(lastStatusPref(service), encoded).apply()
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
    private fun textModelPref(service: ApiService, purpose: MediaPurpose) = "${service.name}.${purpose.name.lowercase()}_text_model"
    private fun imageModelPref(service: ApiService) = "${service.name}.image_model"
    private fun accountIdPref(service: ApiService) = "${service.name}.account_id"
    private fun videoModelPref(service: ApiService) = "${service.name}.video_model"
    private fun modelsPref(service: ApiService) = "${service.name}.models"
    private fun encryptedKeyPref(service: ApiService) = "${service.name}.key_enc"
    private fun legacyKeyPref(service: ApiService) = "${service.name}.key"
    private fun lastStatusPref(service: ApiService) = "${service.name}.last_status"
}

/** A [ConnectionStatus] as a short string for storage; null for Checking, which is never stored. */
fun encodeStatus(status: ConnectionStatus): String? = when (status) {
    ConnectionStatus.Checking -> null
    ConnectionStatus.NotConfigured -> "not_configured"
    ConnectionStatus.Connected -> "connected"
    ConnectionStatus.InvalidKey -> "invalid_key"
    ConnectionStatus.NoPermission -> "no_permission"
    is ConnectionStatus.Unreachable -> "unreachable:${status.reason}"
    is ConnectionStatus.Error -> "error:${status.httpCode}"
}

fun decodeStatus(text: String?): ConnectionStatus? = when {
    text == null -> null
    text == "not_configured" -> ConnectionStatus.NotConfigured
    text == "connected" -> ConnectionStatus.Connected
    text == "invalid_key" -> ConnectionStatus.InvalidKey
    text == "no_permission" -> ConnectionStatus.NoPermission
    text.startsWith("unreachable:") -> ConnectionStatus.Unreachable(text.removePrefix("unreachable:"))
    text.startsWith("error:") -> text.removePrefix("error:").toIntOrNull()?.let { ConnectionStatus.Error(it) }
    else -> null
}

data class CachedModels(val lists: ModelLists, val fetchedAt: Long)

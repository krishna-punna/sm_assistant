package com.smassistant.app.data

import android.content.Context
import org.yaml.snakeyaml.Yaml

/** External services the app calls. [configKey] is the service's entry under `apis:` in app.yml. */
enum class ApiService(val displayName: String, val configKey: String) {
    LINKEDIN("LinkedIn", "linkedin"),
    CLAUDE("Claude", "claude"),
    GEMINI("Gemini", "gemini"),
    GROQ("Groq", "groq"),
}

data class ApiConfig(val api: String, val key: String, val model: String = "")

/**
 * Defaults bundled in `assets/app.yml`, in this shape:
 *
 * ```yaml
 * apis:
 *   claude:
 *     api: https://api.anthropic.com/v1/messages
 *     model: claude-sonnet-5-5
 *     key: ""
 * ```
 */
object AppConfig {
    @Volatile private var cached: Map<ApiService, ApiConfig>? = null

    fun defaults(context: Context, service: ApiService): ApiConfig =
        (cached ?: load(context).also { cached = it }).getValue(service)

    private fun load(context: Context): Map<ApiService, ApiConfig> {
        val root: Map<String, Any?> = context.assets.open("app.yml").use { Yaml().load(it) } ?: emptyMap()
        val apis = root["apis"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return ApiService.entries.associateWith { service ->
            val entry = apis[service.configKey] as? Map<*, *> ?: emptyMap<String, Any?>()
            ApiConfig(
                api = entry["api"]?.toString().orEmpty(),
                key = entry["key"]?.toString().orEmpty(),
                model = entry["model"]?.toString().orEmpty(),
            )
        }
    }
}

/**
 * API settings for each service: values the user saved on a settings screen,
 * falling back to the defaults in app.yml.
 */
class ApiSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    fun load(service: ApiService): ApiConfig {
        val defaults = AppConfig.defaults(appContext, service)
        return defaults.copy(
            api = prefs.getString("${service.name}.api", null) ?: defaults.api,
            key = prefs.getString("${service.name}.key", null) ?: defaults.key,
        )
    }

    fun save(service: ApiService, api: String, key: String) {
        prefs.edit()
            .putString("${service.name}.api", api.trim())
            .putString("${service.name}.key", key.trim())
            .apply()
    }
}

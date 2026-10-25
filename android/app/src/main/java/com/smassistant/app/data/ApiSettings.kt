package com.smassistant.app.data

import android.content.Context

/** Defaults bundled in `assets/app.yml` (see [parseAppConfig]). */
object AppConfig {
    @Volatile private var cached: Map<ApiService, ApiConfig>? = null

    fun defaults(context: Context, service: ApiService): ApiConfig =
        (cached ?: context.assets.open("app.yml").use(::parseAppConfig).also { cached = it }).getValue(service)
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

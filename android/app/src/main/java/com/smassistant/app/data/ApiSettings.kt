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
 * falling back to the defaults in app.yml. Keys are encrypted with [cipher];
 * URLs are stored as plain text.
 */
class ApiSettingsStore(context: Context, private val cipher: SecretCipher = KeystoreCipher()) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    fun load(service: ApiService): ApiConfig {
        val defaults = AppConfig.defaults(appContext, service)
        return defaults.copy(
            api = prefs.getString(apiPref(service), null) ?: defaults.api,
            key = savedKey(service) ?: defaults.key,
        )
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
    private fun encryptedKeyPref(service: ApiService) = "${service.name}.key_enc"
    private fun legacyKeyPref(service: ApiService) = "${service.name}.key"
}

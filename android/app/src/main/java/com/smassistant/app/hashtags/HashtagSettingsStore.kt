package com.smassistant.app.hashtags

import android.content.Context

/**
 * Saved default hashtags, the "Add default hashtags to LinkedIn posts" toggle and the
 * "AI-suggested hashtags" toggle (HT-20).
 * Not secret, so plain SharedPreferences (excluded from backup with the rest of the app data).
 */
class HashtagSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hashtag_settings", Context.MODE_PRIVATE)

    val tags: List<String>
        get() = prefs.getString(KEY_TAGS, "").orEmpty().split(" ").filter { it.isNotEmpty() }

    /** Off on first install (HT-8). */
    val enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * Saves [tags] (already normalised). The first save of a non-empty list turns the toggle on;
     * after that only [setEnabled] changes it (HT-8). An empty list keeps the toggle as it is (HT-9).
     * Returns the toggle state after saving.
     */
    fun save(tags: List<String>): Boolean {
        val autoEnable = tags.isNotEmpty() && !prefs.getBoolean(KEY_AUTO_ENABLED, false)
        prefs.edit().apply {
            putString(KEY_TAGS, Hashtags.format(tags))
            if (autoEnable) {
                putBoolean(KEY_ENABLED, true)
                putBoolean(KEY_AUTO_ENABLED, true)
            }
        }.apply()
        return enabled
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Whether the AI agent adds hashtags that fit each generated post. Off on first install (HT-20). */
    val aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ENABLED, false)

    fun setAiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_TAGS = "tags"
        const val KEY_ENABLED = "enabled"
        const val KEY_AUTO_ENABLED = "auto_enabled_once"
        const val KEY_AI_ENABLED = "ai_enabled"
    }
}

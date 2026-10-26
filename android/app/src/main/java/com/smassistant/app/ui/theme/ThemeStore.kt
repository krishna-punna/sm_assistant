package com.smassistant.app.ui.theme

import android.content.Context

/** The chosen theme. Not secret, so plain SharedPreferences. */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)

    /** The saved theme, or Classic when none was saved or the saved id no longer exists. */
    val themeId: ThemeId
        get() = prefs.getString(KEY_THEME, null)
            ?.let { saved -> ThemeId.entries.firstOrNull { it.name == saved } }
            ?: ThemeId.CLASSIC

    fun save(id: ThemeId) {
        prefs.edit().putString(KEY_THEME, id.name).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_id"
    }
}

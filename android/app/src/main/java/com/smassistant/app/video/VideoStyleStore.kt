package com.smassistant.app.video

import android.content.Context
import com.smassistant.app.data.AppConfig
import com.smassistant.app.data.VideoStyle

/**
 * The Video Style settings (docs/requirements/19, SP-15): what the user saved for videos, over the
 * app.yml `video_style` [defaults], kept apart from the Image Style. Until a video value is saved,
 * the audience, brand and style guide saved on the Image Style screen carry over, so nothing the
 * user chose before the split is lost.
 */
class VideoStyleStore(
    context: Context,
    private val defaults: () -> VideoStyle = { AppConfig.videoStyle(context.applicationContext) },
) {
    private val prefs = context.applicationContext.getSharedPreferences("video_style", Context.MODE_PRIVATE)
    private val earlier = context.applicationContext.getSharedPreferences("image_style", Context.MODE_PRIVATE)

    fun load(): VideoStyle {
        val base = defaults()
        fun saved(key: String) = prefs.getString(key, null) ?: earlier.getString(key, null)
        return VideoStyle(
            audience = saved(AUDIENCE) ?: base.audience,
            brandName = saved(BRAND) ?: base.brandName,
            styleGuide = saved(STYLE_GUIDE) ?: base.styleGuide,
        )
    }

    fun save(style: VideoStyle) {
        prefs.edit()
            .putString(AUDIENCE, style.audience.trim())
            .putString(BRAND, style.brandName.trim())
            .putString(STYLE_GUIDE, style.styleGuide.trim())
            .apply()
    }

    /** Forgets the saved values, so app.yml's apply again. */
    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val AUDIENCE = "audience"
        const val BRAND = "brand_name"
        const val STYLE_GUIDE = "style_guide"
    }
}

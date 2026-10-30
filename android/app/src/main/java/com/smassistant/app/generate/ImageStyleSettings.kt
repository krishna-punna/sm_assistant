package com.smassistant.app.generate

import android.content.Context
import androidx.lifecycle.ViewModel
import com.smassistant.app.data.AppConfig
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The Image Style settings (docs/tasks IT-I15): what the user saved on the Image Style screen, over
 * the app.yml `image_style` [defaults]. The format stays with the image screens' Format picker.
 */
class ImageStyleStore(
    context: Context,
    private val defaults: () -> ImageStyle = { AppConfig.imageStyle(context.applicationContext) },
) {
    private val prefs = context.applicationContext.getSharedPreferences("image_style", Context.MODE_PRIVATE)

    /** The defaults with every saved value in its place. */
    fun load(): ImageStyle {
        val base = defaults()
        return base.copy(
            audience = prefs.getString(AUDIENCE, null) ?: base.audience,
            brandName = prefs.getString(BRAND, null) ?: base.brandName,
            styleGuide = prefs.getString(STYLE_GUIDE, null) ?: base.styleGuide,
            series = if (prefs.contains(SERIES)) prefs.getBoolean(SERIES, base.series) else base.series,
            mode = ImageMode.of(prefs.getString(MODE, null)) ?: base.mode,
        )
    }

    fun save(style: ImageStyle) {
        prefs.edit()
            .putString(AUDIENCE, style.audience.trim())
            .putString(BRAND, style.brandName.trim())
            .putString(STYLE_GUIDE, style.styleGuide.trim())
            .putBoolean(SERIES, style.series)
            .putString(MODE, style.mode.key)
            .apply()
    }

    /** Forgets the saved values, so app.yml's apply again. */
    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val AUDIENCE = "audience"
        const val BRAND = "brand_name"
        const val STYLE_GUIDE = "style_guide"
        const val SERIES = "series"
        const val MODE = "mode"
    }
}

/** The Image Style screen: the values being edited and a one-off confirmation. */
data class ImageStyleUiState(val style: ImageStyle, val message: String? = null)

class ImageStyleViewModel(private val store: ImageStyleStore) : ViewModel() {
    private val _state = MutableStateFlow(ImageStyleUiState(store.load()))
    val state: StateFlow<ImageStyleUiState> = _state.asStateFlow()

    private fun edit(change: (ImageStyle) -> ImageStyle) = _state.update { it.copy(style = change(it.style)) }

    fun onAudience(text: String) = edit { it.copy(audience = text) }
    fun onBrandName(text: String) = edit { it.copy(brandName = text) }
    fun onStyleGuide(text: String) = edit { it.copy(styleGuide = text) }
    fun onSeries(on: Boolean) = edit { it.copy(series = on) }
    fun onMode(mode: ImageMode) = edit { it.copy(mode = mode) }

    fun save() {
        store.save(_state.value.style)
        _state.update { ImageStyleUiState(store.load(), "Image style saved") }
    }

    fun reset() {
        store.reset()
        _state.update { ImageStyleUiState(store.load(), "Back to the app defaults") }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

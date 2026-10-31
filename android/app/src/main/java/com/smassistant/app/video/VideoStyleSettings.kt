package com.smassistant.app.video

import androidx.lifecycle.ViewModel
import com.smassistant.app.data.VideoStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The Video Style screen: the values being edited and a one-off confirmation. */
data class VideoStyleUiState(val style: VideoStyle, val message: String? = null)

class VideoStyleViewModel(private val store: VideoStyleStore) : ViewModel() {
    private val _state = MutableStateFlow(VideoStyleUiState(store.load()))
    val state: StateFlow<VideoStyleUiState> = _state.asStateFlow()

    private fun edit(change: (VideoStyle) -> VideoStyle) = _state.update { it.copy(style = change(it.style)) }

    fun onAudience(text: String) = edit { it.copy(audience = text) }
    fun onBrandName(text: String) = edit { it.copy(brandName = text) }
    fun onStyleGuide(text: String) = edit { it.copy(styleGuide = text) }

    fun save() {
        store.save(_state.value.style)
        _state.update { VideoStyleUiState(store.load(), "Video style saved") }
    }

    fun reset() {
        store.reset()
        _state.update { VideoStyleUiState(store.load(), "Back to the app defaults") }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

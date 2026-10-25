package com.smassistant.app.hashtags

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DefaultHashtagsUiState(
    val text: String = "",
    val enabled: Boolean = false,
    /** Inline error under the text box; nothing was saved. */
    val error: String? = null,
    /** Gentle warning shown after saving more than [RECOMMENDED_MAX_HASHTAGS] tags. */
    val warning: String? = null,
    /** One-off confirmation, cleared with [DefaultHashtagsViewModel.messageShown]. */
    val message: String? = null,
)

/** S14 Default Hash Tags (docs/requirements/10-default-hashtags.md, HT-2 to HT-9). */
class DefaultHashtagsViewModel(private val store: HashtagSettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(
        DefaultHashtagsUiState(text = Hashtags.format(store.tags), enabled = store.enabled)
    )
    val state: StateFlow<DefaultHashtagsUiState> = _state.asStateFlow()

    fun onTextChange(text: String) = _state.update { it.copy(text = text, error = null) }

    /** Empties the box only; the saved list changes on the next Save (HT-5). */
    fun onClear() = _state.update { it.copy(text = "", error = null, warning = null) }

    fun onSave() {
        val parsed = Hashtags.parse(_state.value.text)
        if (!parsed.isValid) {
            _state.update {
                it.copy(
                    error = "Not allowed: ${parsed.invalid.joinToString(" ")}. " +
                        "Hashtags can only use letters, numbers and _.",
                )
            }
            return
        }
        val enabled = store.save(parsed.tags)
        val count = parsed.tags.size
        _state.update {
            it.copy(
                text = Hashtags.format(parsed.tags),
                enabled = enabled,
                error = null,
                warning = if (count > RECOMMENDED_MAX_HASHTAGS) {
                    "LinkedIn suggests 3–5 hashtags per post. You have $count."
                } else {
                    null
                },
                message = when (count) {
                    0 -> "Default hashtags removed"
                    1 -> "Saved 1 hashtag"
                    else -> "Saved $count hashtags"
                },
            )
        }
    }

    fun onEnabledChange(enabled: Boolean) {
        store.setEnabled(enabled)
        _state.update { it.copy(enabled = enabled) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

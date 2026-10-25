package com.smassistant.app.settings

import androidx.lifecycle.ViewModel
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ApiSettingsUiState(
    val api: String = "",
    val key: String = "",
    val apiError: String? = null,
    val keyError: String? = null,
    /** One-off confirmation, cleared with [ApiSettingsViewModel.messageShown]. */
    val message: String? = null,
)

/** True for an absolute `https://host/...` URL. */
fun isValidHttpsUrl(value: String): Boolean = try {
    val uri = URI(value.trim())
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
} catch (e: URISyntaxException) {
    false
}

/** S2 and S4–S6: API + API key for one service (LI-1 to LI-3, AG-2, AG-3). */
class ApiSettingsViewModel(
    private val service: ApiService,
    private val store: ApiSettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(store.load(service).let { ApiSettingsUiState(api = it.api, key = it.key) })
    val state: StateFlow<ApiSettingsUiState> = _state.asStateFlow()

    fun onApiChange(api: String) = _state.update { it.copy(api = api, apiError = null) }

    fun onKeyChange(key: String) = _state.update { it.copy(key = key, keyError = null) }

    fun onSave() {
        val api = _state.value.api.trim()
        val key = _state.value.key.trim()
        val apiError = if (api.isNotEmpty() && !isValidHttpsUrl(api)) "Enter a full https:// URL" else null
        val keyError = if (key.isEmpty()) "Enter your ${service.displayName} API key" else null
        if (apiError != null || keyError != null) {
            _state.update { it.copy(apiError = apiError, keyError = keyError) }
            return
        }
        store.save(service, api, key)
        val saved = store.load(service)
        _state.update {
            it.copy(
                api = saved.api,
                key = saved.key,
                message = if (api.isEmpty()) {
                    "${service.displayName} settings saved, using the default API"
                } else {
                    "${service.displayName} settings saved"
                },
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

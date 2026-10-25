package com.smassistant.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ModelCatalog
import com.smassistant.app.data.ModelLists
import com.smassistant.app.net.ApiResult
import com.smassistant.app.net.userMessage
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiSettingsUiState(
    val api: String = "",
    val key: String = "",
    val apiError: String? = null,
    val keyError: String? = null,
    /** One-off confirmation, cleared with [ApiSettingsViewModel.messageShown]. */
    val message: String? = null,
    /** Model pickers (AI agents only): the chosen ids and what the provider last listed. */
    val showModels: Boolean = false,
    val showImageModel: Boolean = false,
    val model: String = "",
    val imageModel: String = "",
    val models: ModelLists = ModelLists(),
    val loadingModels: Boolean = false,
    val modelsError: String? = null,
) {
    /** The listed models, with the current choice kept even before the list has it. */
    val textOptions get() = withCurrent(model, models.text)
    val imageOptions get() = withCurrent(imageModel, models.image)

    private fun withCurrent(id: String, list: List<AiModel>) =
        if (id.isBlank() || list.any { it.id == id }) list else listOf(AiModel(id)) + list
}

/** How long a fetched model list is used before the screen fetches it again on its own. */
const val MODEL_LIST_MAX_AGE_MS = 24 * 60 * 60 * 1000L

/** True for an absolute `https://host/...` URL. */
fun isValidHttpsUrl(value: String): Boolean = try {
    val uri = URI(value.trim())
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
} catch (e: URISyntaxException) {
    false
}

/**
 * S2 and S4–S6: API + API key for one service (LI-1 to LI-3, AG-2, AG-3). With a [catalog]
 * (the AI agents) it also picks the model: the provider's list is fetched when the screen
 * opens if the saved one is over a day old, and again on Refresh or after a new key is saved.
 */
class ApiSettingsViewModel(
    private val service: ApiService,
    private val store: ApiSettingsStore,
    private val catalog: ModelCatalog? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(
        store.load(service).let {
            ApiSettingsUiState(
                api = it.api,
                key = it.key,
                showModels = catalog != null,
                showImageModel = catalog != null && service == ApiService.GEMINI,
                model = it.model,
                imageModel = it.imageModel,
                models = store.cachedModels(service)?.lists ?: ModelLists(),
            )
        },
    )
    val state: StateFlow<ApiSettingsUiState> = _state.asStateFlow()

    init {
        val cached = store.cachedModels(service)
        if (cached == null || now() - cached.fetchedAt > MODEL_LIST_MAX_AGE_MS) refreshModels()
    }

    fun onModelChange(id: String) = _state.update { it.copy(model = id) }

    fun onImageModelChange(id: String) = _state.update { it.copy(imageModel = id) }

    /** Fetches the provider's model list with the saved settings. Does nothing without a key. */
    fun refreshModels() {
        val catalog = catalog ?: return
        val config = store.load(service)
        if (config.key.isBlank() || _state.value.loadingModels) return
        _state.update { it.copy(loadingModels = true, modelsError = null) }
        viewModelScope.launch {
            when (val result = catalog.fetch(service, config)) {
                is ApiResult.Ok -> {
                    store.saveCachedModels(service, result.value, now())
                    _state.update { it.copy(loadingModels = false, models = result.value) }
                }
                is ApiResult.Failed -> _state.update {
                    it.copy(loadingModels = false, modelsError = "Couldn't update the model list. ${result.error.userMessage(service.displayName)}")
                }
            }
        }
    }

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
        val keyChanged = key != store.load(service).key
        store.save(service, api, key)
        if (catalog != null) store.saveModels(service, _state.value.model, _state.value.imageModel)
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
        if (keyChanged || _state.value.models.text.isEmpty()) refreshModels()
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

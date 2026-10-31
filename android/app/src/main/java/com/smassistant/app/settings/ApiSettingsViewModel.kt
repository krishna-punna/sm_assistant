package com.smassistant.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.agents.IMAGE_AGENTS
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ConnectionStatus
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
    /** Cloudflare only: the account id that goes in the API URL. */
    val showAccountId: Boolean = false,
    val accountId: String = "",
    val accountIdError: String? = null,
    val apiError: String? = null,
    val keyError: String? = null,
    /** One-off confirmation, cleared with [ApiSettingsViewModel.messageShown]. */
    val message: String? = null,
    /** Model pickers (AI agents only): the chosen ids and what the provider last listed. */
    val showModels: Boolean = false,
    val showImageModel: Boolean = false,
    val model: String = "",
    val imageModel: String = "",
    val videoModel: String = "",
    val models: ModelLists = ModelLists(),
    val loadingModels: Boolean = false,
    val modelsError: String? = null,
    /** Test (AS-7): shown when the screen can check the key; the last result, or Checking while it runs. */
    val canTest: Boolean = false,
    val testStatus: ConnectionStatus? = null,
) {
    /** The listed models, with the current choice kept even before the list has it. */
    val textOptions get() = withCurrent(model, models.text)
    val imageOptions get() = withCurrent(imageModel, mediaModels)
    val videoOptions get() = withCurrent(videoModel, mediaModels)
    private val mediaModels get() = models.image

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
    /** The API Status check, for the Test button (AS-7); null hides it. */
    private val check: (suspend (ApiService, ApiConfig) -> ConnectionStatus)? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        store.load(service).let {
            ApiSettingsUiState(
                api = it.api,
                key = it.key,
                showAccountId = ApiConfig.ACCOUNT_ID_PLACEHOLDER in it.api,
                accountId = it.accountId,
                showModels = catalog != null,
                showImageModel = catalog != null && service in IMAGE_AGENTS,
                model = it.model,
                imageModel = it.imageModel,
                videoModel = it.videoModel,
                models = store.cachedModels(service)?.lists ?: ModelLists(),
                canTest = check != null,
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

    fun onVideoModelChange(id: String) = _state.update { it.copy(videoModel = id) }

    /** Fetches the provider's model list with the saved settings. Does nothing without a key. */
    fun refreshModels() {
        val catalog = catalog ?: return
        val config = store.load(service)
        if (!config.isConfigured() || _state.value.loadingModels) return
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

    fun onApiChange(api: String) = _state.update { it.copy(api = api, apiError = null, testStatus = null) }

    fun onKeyChange(key: String) = _state.update { it.copy(key = key, keyError = null, testStatus = null) }

    fun onAccountIdChange(accountId: String) = _state.update { it.copy(accountId = accountId, accountIdError = null, testStatus = null) }

    /**
     * Test (AS-7, Q-22): checks the key with the same request as API Status and shows the result
     * under SAVE. Unsaved changes are saved first, so it tests what's on the screen; the result is
     * remembered for the agent warnings, like API Status.
     */
    fun test() {
        val check = check ?: return
        if (_state.value.testStatus == ConnectionStatus.Checking) return
        val saved = store.load(service)
        val current = _state.value
        val changed = current.api.trim() != saved.api || current.key.trim() != saved.key ||
            (current.showAccountId && current.accountId.trim() != saved.accountId)
        // Nothing saved yet counts as a change, so an empty key shows its field error instead of testing.
        if ((changed || saved.key.isEmpty()) && !onSave()) return
        _state.update { it.copy(testStatus = ConnectionStatus.Checking) }
        viewModelScope.launch {
            val status = check(service, store.load(service))
            store.saveLastStatus(service, status)
            _state.update { it.copy(testStatus = status) }
        }
    }

    /** Validates and saves; false (with the errors shown) when a field is wrong. */
    fun onSave(): Boolean {
        val api = _state.value.api.trim()
        val key = _state.value.key.trim()
        val accountId = _state.value.accountId.trim()
        // The {account_id} placeholder isn't valid in a URL until it's filled in.
        val apiError = if (api.isNotEmpty() && !isValidHttpsUrl(api.replace(ApiConfig.ACCOUNT_ID_PLACEHOLDER, "id"))) {
            "Enter a full https:// URL"
        } else {
            null
        }
        val keyError = if (key.isEmpty()) "Enter your ${service.displayName} API key" else null
        val accountIdError = when {
            !_state.value.showAccountId -> null
            accountId.isEmpty() -> "Enter your ${service.displayName} account ID"
            !accountId.all { it.isLetterOrDigit() } -> "Use only letters and numbers"
            else -> null
        }
        if (apiError != null || keyError != null || accountIdError != null) {
            _state.update { it.copy(apiError = apiError, keyError = keyError, accountIdError = accountIdError) }
            return false
        }
        val before = store.load(service)
        val keyChanged = key != before.key || accountId != before.accountId
        store.save(service, api, key)
        if (_state.value.showAccountId) store.saveAccountId(service, accountId)
        if (catalog != null) _state.value.let { store.saveModels(service, it.model, it.imageModel, it.videoModel) }
        val saved = store.load(service)
        _state.update {
            it.copy(
                api = saved.api,
                key = saved.key,
                accountId = saved.accountId,
                message = if (api.isEmpty()) {
                    "${service.displayName} settings saved, using the default API"
                } else {
                    "${service.displayName} settings saved"
                },
            )
        }
        if (keyChanged || _state.value.models.text.isEmpty()) refreshModels()
        return true
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /** Shows the saved values again, e.g. after LinkedIn sign-in replaced the token. */
    fun reload() {
        val saved = store.load(service)
        _state.update { it.copy(api = saved.api, key = saved.key, apiError = null, keyError = null, testStatus = null) }
    }
}

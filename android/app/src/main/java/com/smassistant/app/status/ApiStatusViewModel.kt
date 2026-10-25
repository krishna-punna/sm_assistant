package com.smassistant.app.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ConnectionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S15 API Status (docs/requirements/11-api-status.md, AS-1 and AS-5). */
class ApiStatusViewModel(
    private val loadConfig: (ApiService) -> ApiConfig,
    private val check: suspend (ApiService, ApiConfig) -> ConnectionStatus,
) : ViewModel() {
    private val _statuses = MutableStateFlow(allChecking())
    val statuses: StateFlow<Map<ApiService, ConnectionStatus>> = _statuses.asStateFlow()

    private var round: Job? = null

    init {
        testAll()
    }

    /** Resets every card to Checking and tests all services in parallel, cancelling any round still running. */
    fun testAll() {
        round?.cancel()
        _statuses.value = allChecking()
        round = viewModelScope.launch {
            ApiService.entries.forEach { service ->
                launch {
                    val status = check(service, loadConfig(service))
                    _statuses.update { it + (service to status) }
                }
            }
        }
    }

    private fun allChecking() = ApiService.entries.associateWith<ApiService, ConnectionStatus> { ConnectionStatus.Checking }
}

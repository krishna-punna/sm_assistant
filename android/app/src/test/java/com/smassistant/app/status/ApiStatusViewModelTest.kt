package com.smassistant.app.status

import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ConnectionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStatusViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val configs = mutableListOf<ApiService>()
    private fun load(service: ApiService): ApiConfig {
        configs += service
        return ApiConfig("https://${service.configKey}", "k")
    }

    @Test
    fun `tests every service on open, starting as Checking`() = runTest(dispatcher) {
        val vm = ApiStatusViewModel(::load) { service, _ ->
            if (service == ApiService.GROQ) ConnectionStatus.InvalidKey else ConnectionStatus.Connected
        }
        assertTrue(vm.statuses.value.values.all { it == ConnectionStatus.Checking })

        advanceUntilIdle()

        assertEquals(ConnectionStatus.InvalidKey, vm.statuses.value[ApiService.GROQ])
        assertEquals(ConnectionStatus.Connected, vm.statuses.value[ApiService.GEMINI])
        assertEquals(ApiService.entries.toSet(), configs.toSet())
    }

    @Test
    fun `checks run in parallel`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val started = mutableSetOf<ApiService>()
        ApiStatusViewModel(::load) { service, _ ->
            started += service
            gate.await()
            ConnectionStatus.Connected
        }
        advanceUntilIdle()
        assertEquals(ApiService.entries.toSet(), started)
        gate.complete(Unit)
    }

    @Test
    fun `test again cancels the previous round so a slow old result can't win`() = runTest(dispatcher) {
        var round = 0
        val firstRound = CompletableDeferred<Unit>()
        val vm = ApiStatusViewModel(::load) { _, _ ->
            if (round == 0) {
                firstRound.await()
                ConnectionStatus.Error(500)
            } else {
                ConnectionStatus.Connected
            }
        }
        advanceUntilIdle()

        round = 1
        vm.testAll()
        assertTrue(vm.statuses.value.values.all { it == ConnectionStatus.Checking })
        firstRound.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.statuses.value.values.all { it == ConnectionStatus.Connected })
    }

}

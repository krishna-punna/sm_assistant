package com.smassistant.app.chat

import com.smassistant.app.net.ApiError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun kinds(vm: ChatViewModel<*>) = vm.state.value.items.map { it::class.simpleName }

    @Test
    fun `submit shows input and loading, then the result`() = runTest(dispatcher) {
        val vm = ChatViewModel { Outcome.Ok("Prompt for: $it") }
        vm.onInputChange("  Design a system for Short URL ")
        vm.submit()

        assertEquals(listOf("Input", "Loading"), kinds(vm))
        assertEquals("", vm.state.value.input)
        assertTrue(vm.state.value.busy)

        advanceUntilIdle()

        assertEquals(listOf("Input", "Result"), kinds(vm))
        val result = vm.state.value.items[1] as ChatItem.Result<String>
        assertEquals("Prompt for: Design a system for Short URL", result.value)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `blank input and submits while busy are ignored`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = ChatViewModel { gate.await(); Outcome.Ok(it) }
        vm.onInputChange("   ")
        vm.submit()
        assertTrue(vm.state.value.items.isEmpty())

        vm.onInputChange("one")
        vm.submit()
        vm.onInputChange("two")
        vm.submit()
        assertEquals(listOf("Input", "Loading"), kinds(vm))
        assertEquals("two", vm.state.value.input)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `conversation keeps earlier exchanges`() = runTest(dispatcher) {
        val vm = ChatViewModel { Outcome.Ok(it.uppercase()) }
        listOf("a", "b").forEach {
            vm.onInputChange(it)
            vm.submit()
            advanceUntilIdle()
        }
        assertEquals(listOf("Input", "Result", "Input", "Result"), kinds(vm))
    }

    @Test
    fun `failure shows a message and retry runs the same input again`() = runTest(dispatcher) {
        var calls = 0
        val vm = ChatViewModel<String> { input ->
            calls++
            if (calls == 1) Outcome.Failed(ApiError.InvalidKey, "Claude") else Outcome.Ok("ok:$input")
        }
        vm.onInputChange("topic")
        vm.submit()
        advanceUntilIdle()

        val failure = vm.state.value.items[1] as ChatItem.Failure
        assertEquals("Claude rejected the API key. Check it in settings.", failure.message)

        vm.retry(failure)
        advanceUntilIdle()

        assertEquals(listOf("Input", "Result"), kinds(vm))
        assertEquals("ok:topic", (vm.state.value.items[1] as ChatItem.Result<String>).value)
    }

    @Test
    fun `no agent shows the set-up item`() = runTest(dispatcher) {
        val vm = ChatViewModel<String> { Outcome.NoAgent }
        vm.onInputChange("topic")
        vm.submit()
        advanceUntilIdle()
        assertEquals(listOf("Input", "NoAgent"), kinds(vm))
    }

    @Test
    fun `cancel stops the generation`() = runTest(dispatcher) {
        val vm = ChatViewModel<String> { CompletableDeferred<Unit>().await(); Outcome.Ok(it) }
        vm.onInputChange("topic")
        vm.submit()
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()

        assertEquals("Cancelled", (vm.state.value.items[1] as ChatItem.Failure).message)
        assertFalse(vm.state.value.busy)
    }
}

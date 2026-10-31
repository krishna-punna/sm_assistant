package com.smassistant.app.chat

import androidx.lifecycle.SavedStateHandle
import com.smassistant.app.generate.ContentAndMedia
import com.smassistant.app.generate.finish
import com.smassistant.app.net.ApiError
import com.smassistant.app.pipeline.Pipelines
import com.smassistant.app.pipeline.StageId
import com.smassistant.app.pipeline.StageStatus
import com.smassistant.app.pipeline.reportStage
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

/** The stages each prompt shows on its way to LinkedIn (docs/requirements/20, PS-1, PS-4, PS-6). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPipelineTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun ChatViewModel<*>.stages() = state.value.pipelines.values.single().stages.associate { it.id to it.status }

    @Test
    fun `submit starts the pipeline and a result leaves Post waiting`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val vm = ChatViewModel(pipeline = Pipelines.image) {
            reportStage(StageId.WRITE_CONTENT, StageStatus.DONE)
            reportStage(StageId.DRAW_IMAGE, StageStatus.RUNNING)
            gate.await()
            Outcome.Ok("image")
        }
        vm.onInputChange("caching")
        vm.submit()
        advanceUntilIdle()
        assertEquals(
            mapOf(StageId.PROMPT to StageStatus.DONE, StageId.WRITE_CONTENT to StageStatus.DONE, StageId.DRAW_IMAGE to StageStatus.RUNNING, StageId.POST to StageStatus.WAITING),
            vm.stages(),
        )

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(StageStatus.DONE, vm.stages()[StageId.DRAW_IMAGE])
        assertEquals("Ready: tap Post in LinkedIn", vm.state.value.pipelines.values.single().statusLine)
    }

    @Test
    fun `a failure fails the stage it was on`() = runTest(dispatcher) {
        val vm = ChatViewModel(pipeline = Pipelines.text) { Outcome.Failed(ApiError.RateLimited, "Gemini") }
        vm.onInputChange("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals(StageStatus.FAILED, vm.stages()[StageId.WRITE_POST])
        assertEquals(StageStatus.WAITING, vm.stages()[StageId.POST])
    }

    @Test
    fun `cancel fails the running stage`() = runTest(dispatcher) {
        val vm = ChatViewModel(pipeline = Pipelines.video) { CompletableDeferred<Unit>().await(); Outcome.Ok("v") }
        vm.onInputChange("x")
        vm.submit()
        advanceUntilIdle()
        vm.cancel()
        advanceUntilIdle()
        val p = vm.state.value.pipelines.values.single()
        assertEquals(StageStatus.FAILED, p[StageId.WRITE_SLIDES]?.status)
        assertEquals("Cancelled", p[StageId.WRITE_SLIDES]?.note)
    }

    @Test
    fun `markStage moves the Post stage`() = runTest(dispatcher) {
        val vm = ChatViewModel(pipeline = Pipelines.text) { Outcome.Ok("post") }
        vm.onInputChange("x")
        vm.submit()
        advanceUntilIdle()
        val input = vm.state.value.items.first().id
        vm.markStage(input, StageId.POST, StageStatus.DONE)
        assertEquals("Posted", vm.state.value.pipelines.getValue(input).statusLine)
    }

    @Test
    fun `a half that failed fails its own stages`() = runTest(dispatcher) {
        val vm = ChatViewModel<ContentAndMedia<String>>(pipeline = Pipelines.contentAndImage, finished = { r, p -> r.finish(p) }) {
            Outcome.Ok(ContentAndMedia(markdown = "post", mediaError = "No image agent"))
        }
        vm.onInputChange("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals(StageStatus.DONE, vm.stages()[StageId.WRITE_POST])
        assertEquals(StageStatus.FAILED, vm.stages()[StageId.WRITE_CONTENT])
        assertEquals(StageStatus.WAITING, vm.stages()[StageId.DRAW_IMAGE])
    }

    @Test
    fun `stages are saved, and a running one comes back stopped`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = ChatViewModel(handle, TextSaver, pipeline = Pipelines.text) { CompletableDeferred<Unit>().await(); Outcome.Ok(it) }
        first.onInputChange("x")
        first.submit()
        advanceUntilIdle()

        val restored = ChatViewModel(handle, TextSaver, pipeline = Pipelines.text) { Outcome.Ok("again") }
        val p = restored.state.value.pipelines.values.single()
        assertEquals(StageStatus.FAILED, p[StageId.WRITE_POST]?.status)
        assertEquals("Stopped", p[StageId.WRITE_POST]?.note)
        assertTrue(p[StageId.PROMPT]?.status == StageStatus.DONE)
    }

    @Test
    fun `no pipeline without one for the feature`() = runTest(dispatcher) {
        val vm = ChatViewModel { Outcome.Ok("x") }
        vm.onInputChange("x")
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.state.value.pipelines.isEmpty())
    }
}

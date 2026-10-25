package com.smassistant.app.linkedin

import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
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
class PostToLinkedInTest {
    private val dispatcher = StandardTestDispatcher()
    private var tags = listOf("#systemdesign", "#java")
    private var enabled = true
    private var configured = true
    private val published = mutableListOf<String>()
    private var reply: suspend () -> ApiResult<LinkedInPost> = { ApiResult.Ok(LinkedInPost("urn:li:share:1")) }

    private val useCase = PostToLinkedInUseCase(
        tags = { tags },
        tagsEnabled = { enabled },
        configured = { configured },
        publish = { text -> published += text; reply() },
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `prepare converts Markdown and appends hashtags that aren't already there`() {
        val prepared = useCase.prepare("## Caching\n- **Redis** for hot keys\n\n#java")
        assertEquals("Caching\n\n• Redis for hot keys\n\n#java\n\n#systemdesign", prepared.text)
        assertEquals(listOf("#systemdesign"), prepared.addedTags)
    }

    @Test
    fun `prepare leaves hashtags off when the toggle is off`() {
        enabled = false
        assertEquals("Plain post", useCase.prepare("Plain post").text)
    }

    @Test
    fun `post refuses missing settings and over-limit text without a request`() = runTest(dispatcher) {
        configured = false
        assertEquals(ApiResult.Failed(ApiError.NotConfigured), useCase.post("x"))
        configured = true
        assertTrue(useCase.post("a".repeat(3_001)) is ApiResult.Failed)
        assertTrue(useCase.post(" ") is ApiResult.Failed)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `confirm then post shows the result and ignores a second tap`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<LinkedInPost>>()
        reply = { gate.await() }
        val vm = PostViewModel(useCase)

        vm.start("Hello")
        assertEquals(PostState.Confirm("Hello\n\n#systemdesign #java", listOf("#systemdesign", "#java")), vm.state.value)

        vm.confirm()
        vm.confirm()
        vm.start("Again")
        assertEquals(PostState.Posting, vm.state.value)

        gate.complete(ApiResult.Ok(LinkedInPost("urn:li:share:9")))
        advanceUntilIdle()
        assertEquals(PostState.Posted(LinkedInPost("urn:li:share:9")), vm.state.value)
        assertEquals(1, published.size)

        vm.dismiss()
        assertEquals(PostState.Idle, vm.state.value)
    }

    @Test
    fun `not set up goes straight to the settings prompt`() {
        configured = false
        val vm = PostViewModel(useCase)
        vm.start("Hello")
        val state = vm.state.value as PostState.Failed
        assertTrue(state.openSettings)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `expired token and missing permission point to settings`() = runTest(dispatcher) {
        val vm = PostViewModel(useCase)
        reply = { ApiResult.Failed(ApiError.InvalidKey) }
        vm.start("Hello")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(
            PostState.Failed("LinkedIn token expired or invalid. Paste a new access token in LinkedIn settings.", openSettings = true),
            vm.state.value,
        )

        reply = { ApiResult.Failed(ApiError.RateLimited) }
        vm.start("Hello")
        vm.confirm()
        advanceUntilIdle()
        assertFalse((vm.state.value as PostState.Failed).openSettings)
    }

    @Test
    fun `over-limit text can't be posted until it's shortened`() = runTest(dispatcher) {
        enabled = false
        val vm = PostViewModel(useCase)
        vm.start("a".repeat(3_010))
        val confirm = vm.state.value as PostState.Confirm
        assertEquals(10, confirm.overLimitBy)
        assertFalse(confirm.canPost)

        vm.confirm()
        assertTrue(vm.state.value is PostState.Confirm)

        vm.onTextChange("a".repeat(3_000))
        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.state.value is PostState.Posted)
    }
}

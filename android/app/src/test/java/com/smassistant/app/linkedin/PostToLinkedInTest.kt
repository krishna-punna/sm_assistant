package com.smassistant.app.linkedin

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.generate.SmartTrimUseCase
import com.smassistant.app.hashtags.postLength
import com.smassistant.app.history.PostMedia
import com.smassistant.app.history.PostSource
import com.smassistant.app.history.PublishedPost
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PostToLinkedInTest {
    private val dispatcher = StandardTestDispatcher()
    private var tags = listOf("#systemdesign", "#java")
    private var enabled = true
    private var configured = true
    private val published = mutableListOf<String>()
    private val attached = mutableListOf<File>()
    private var reply: suspend () -> ApiResult<LinkedInPost> = { ApiResult.Ok(LinkedInPost("urn:li:share:1")) }

    private val history = mutableListOf<PublishedPost>()

    private val useCase = PostToLinkedInUseCase(
        tags = { tags },
        tagsEnabled = { enabled },
        configured = { configured },
        publish = { text, images -> published += text; attached += images; reply() },
        onPublished = { history += it },
        now = { 77L },
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `prepare converts Markdown and merges hashtags that aren't already there into the last hashtag line`() {
        val prepared = useCase.prepare("## Caching\n- **Redis** for hot keys\n\n#java")
        assertEquals("Caching\n\n• Redis for hot keys\n\n#systemdesign #java", prepared.text)
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
            PostState.Failed("LinkedIn token expired or invalid. Sign in again in LinkedIn settings.", openSettings = true),
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

    @Test
    fun `images can be posted without body text`() = runTest(dispatcher) {
        enabled = false
        val images = listOf(File("a.png"), File("b.png"))
        val vm = PostViewModel(useCase)
        vm.start("", images)
        val confirm = vm.state.value as PostState.Confirm
        assertEquals("", confirm.text)
        assertEquals(images, confirm.images)
        assertTrue(confirm.canPost)

        vm.confirm()
        advanceUntilIdle()
        assertTrue(vm.state.value is PostState.Posted)
        assertEquals(images, attached)
    }

    @Test
    fun `an image-only post gets the hashtags as its text`() {
        assertEquals("#systemdesign #java", useCase.prepare("").text)
    }

    @Test
    fun `more than twenty images are refused`() = runTest(dispatcher) {
        assertTrue(useCase.post("x", List(21) { File("$it.png") }) is ApiResult.Failed)
        assertTrue(attached.isEmpty())
    }

    @Test
    fun `a video goes alone`() = runTest(dispatcher) {
        assertEquals(
            ApiResult.Failed(ApiError.BadResponse("LinkedIn posts take one video and no images")),
            useCase.post("x", listOf(File("v.mp4"), File("a.png"))),
        )
        assertTrue(attached.isEmpty())
    }

    @Test
    fun `cut to fit gets an over-limit post under the limit and keeps the hashtags`() {
        val vm = PostViewModel(useCase)
        vm.start("Hook\n\n" + "word ".repeat(700).trim())
        vm.cutToFit()
        val confirm = vm.state.value as PostState.Confirm
        assertTrue(confirm.canPost)
        assertTrue(confirm.text.startsWith("Hook"))
        assertTrue(confirm.text.endsWith("…\n\n#systemdesign #java"))
    }

    private class TrimAgent(private val reply: suspend () -> ApiResult<String>) : TextAgent {
        override val service = ApiService.GEMINI
        var request = ""
        override suspend fun generate(system: String, user: String): ApiResult<String> {
            request = user
            return reply()
        }
    }

    @Test
    fun `trim with AI condenses the body and puts the hashtags back`() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<String>>()
        val agent = TrimAgent { gate.await() }
        val vm = PostViewModel(useCase, SmartTrimUseCase { agent })
        vm.start("Hook\n\n" + "word ".repeat(700).trim())
        vm.trimWithAi()
        advanceUntilIdle()
        val trimming = vm.state.value as PostState.Confirm
        assertTrue(trimming.trimming)
        assertFalse(trimming.canPost)
        // The hashtags aren't sent, and their room is taken out of the budget.
        assertFalse(agent.request.contains("#java"))
        assertTrue(agent.request.contains("at most ${3_000 - postLength("#systemdesign #java") - 2} characters"))

        gate.complete(ApiResult.Ok("Hook\n\nThe key points."))
        advanceUntilIdle()
        assertEquals("Hook\n\nThe key points.\n\n#systemdesign #java", (vm.state.value as PostState.Confirm).text)
        assertTrue((vm.state.value as PostState.Confirm).canPost)
    }

    @Test
    fun `trim with AI on a post that fits aims a fifth shorter`() = runTest(dispatcher) {
        enabled = false
        val agent = TrimAgent { ApiResult.Ok("Short") }
        val vm = PostViewModel(useCase, SmartTrimUseCase { agent })
        vm.start("a ".repeat(50).trim())
        vm.trimWithAi()
        advanceUntilIdle()
        assertTrue(agent.request.contains("at most 79 characters"))
        assertEquals("Short", (vm.state.value as PostState.Confirm).text)
    }

    @Test
    fun `a failed trim keeps the text and says why`() = runTest(dispatcher) {
        val vm = PostViewModel(useCase, SmartTrimUseCase { TrimAgent { ApiResult.Failed(ApiError.RateLimited) } })
        vm.start("Hello")
        vm.trimWithAi()
        advanceUntilIdle()
        val confirm = vm.state.value as PostState.Confirm
        assertEquals("Hello\n\n#systemdesign #java", confirm.text)
        assertFalse(confirm.trimming)
        assertTrue(confirm.trimMessage != null)
    }

    @Test
    fun `a published post goes into the history, a failed one doesn't`() = runTest(dispatcher) {
        useCase.post("Caching in 3 steps\n\nBody", listOf(File("a.png")))
        val entry = history.single()
        assertEquals("Caching in 3 steps", entry.title)
        assertEquals(PostSource.MANUAL, entry.source)
        assertEquals(PostMedia.IMAGES, entry.media)
        assertEquals("https://www.linkedin.com/feed/update/urn:li:share:1", entry.url)
        assertEquals(77L, entry.postedAt)

        reply = { ApiResult.Failed(ApiError.RateLimited) }
        useCase.post("Another")
        assertEquals(1, history.size)
    }

    @Test
    fun `an approved scheduler post is recorded as scheduled with its title`() = runTest(dispatcher) {
        val vm = PostViewModel(useCase)
        vm.review("Final text #java", title = "Kafka Partitions")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(PostSource.SCHEDULED, history.single().source)
        assertEquals("Kafka Partitions", history.single().title)

        vm.start("Hello")
        vm.confirm()
        advanceUntilIdle()
        assertEquals(PostSource.MANUAL, history.last().source)
    }
}

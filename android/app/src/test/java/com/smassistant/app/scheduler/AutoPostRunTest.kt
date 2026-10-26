package com.smassistant.app.scheduler

import com.smassistant.app.chat.Outcome
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.generate.GeneratedVideo
import com.smassistant.app.linkedin.LinkedInPost
import com.smassistant.app.linkedin.PostToLinkedInUseCase
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.PreparedPrompt
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPostRunTest {
    private var configured = true
    private var tagsOn = true
    private val topics = mutableListOf<String>()
    private val published = mutableListOf<Pair<String, List<File>>>()
    private var prepare: Outcome<PreparedPrompt> = Outcome.Ok(PreparedPrompt("Kafka Partitions", "Act as a Kafka expert"))
    private var text: Outcome<String> = Outcome.Ok("Kafka partitions explained")
    private var images: Outcome<List<File>> = Outcome.Ok(listOf(File("a.png"), File("b.png")))
    private var video: Outcome<GeneratedVideo> = Outcome.Ok(GeneratedVideo(File("v.mp4"), emptyList()))
    private var reply: ApiResult<LinkedInPost> = ApiResult.Ok(LinkedInPost("urn:li:share:7"))
    private val prompts = mutableListOf<String>()

    private val linkedIn = PostToLinkedInUseCase(
        tags = { listOf("#java") },
        tagsEnabled = { tagsOn },
        configured = { configured },
        publish = { body, files -> published += body to files; reply },
        limit = 60,
    )

    private val run = AutoPostRun(
        preparePrompt = { topics += it; prepare },
        generateText = { prompts += it; text },
        generateImages = { prompts += "images: $it"; images },
        generateVideo = { prompts += "video: $it"; video },
        linkedIn = linkedIn,
        now = { 1000L },
    )

    private fun scheduler(type: PostType = PostType.TEXT) =
        AutoPostScheduler(topics = listOf("Kafka", "Java"), postType = type, recentTitles = listOf("Kafka Consumer Groups"))

    @Test
    fun `text post goes topic, prompt, content, hashtags, LinkedIn`() = runTest {
        val outcome = run(scheduler())
        assertEquals(RunRecord(1000L, true, "Posted “Kafka Partitions”", "https://www.linkedin.com/feed/update/urn:li:share:7"), outcome.record)
        assertEquals("Kafka Partitions", outcome.title)
        assertTrue(topics.single().startsWith("Kafka: choose one specific"))
        assertTrue(topics.single().contains("Kafka Consumer Groups"))
        assertEquals(listOf("Act as a Kafka expert"), prompts)
        assertEquals(listOf("Kafka partitions explained\n\n#java" to emptyList<File>()), published)
    }

    @Test
    fun `image and video posts attach their media`() = runTest {
        run(scheduler(PostType.TEXT_WITH_IMAGE))
        assertEquals(listOf(File("a.png"), File("b.png")), published.last().second)
        run(scheduler(PostType.TEXT_WITH_VIDEO))
        assertEquals(listOf(File("v.mp4")), published.last().second)
    }

    @Test
    fun `media that fails means nothing is posted`() = runTest {
        images = Outcome.Failed(ApiError.InvalidKey, "Gemini")
        val outcome = run(scheduler(PostType.TEXT_WITH_IMAGE))
        assertFalse(outcome.record.ok)
        assertEquals("Couldn't make the images. Gemini rejected the API key. Check it in settings.", outcome.record.message)
        assertTrue(published.isEmpty())

        video = Outcome.NoAgent
        assertEquals(GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP, run(scheduler(PostType.TEXT_WITH_VIDEO)).record.message)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `a post over the limit with hashtags is not posted`() = runTest {
        text = Outcome.Ok("x".repeat(55))
        val outcome = run(scheduler())
        assertFalse(outcome.record.ok)
        assertTrue(outcome.record.message, outcome.record.message.startsWith("The post was 2 characters over LinkedIn's 60-character limit"))
        assertTrue(published.isEmpty())
        tagsOn = false
        assertTrue(run(scheduler()).record.ok)
    }

    @Test
    fun `missing setup is reported before calling any agent`() = runTest {
        configured = false
        assertEquals("Set up LinkedIn first: sign in or paste an access token in LinkedIn settings.", run(scheduler()).record.message)
        assertEquals("Choose at least one technical content type.", run(AutoPostScheduler()).record.message)
        assertTrue(topics.isEmpty())

        configured = true
        prepare = Outcome.NoAgent
        val outcome = run(scheduler())
        assertEquals(GenerateContentAndMediaUseCase.TEXT_NOT_SET_UP, outcome.record.message)
        assertNull(outcome.title)
    }

    @Test
    fun `network errors and rate limits are retried, key errors are not`() = runTest {
        text = Outcome.Failed(ApiError.Unreachable("Timed out"), "Claude")
        assertTrue(run(scheduler()).retry)
        text = Outcome.Failed(ApiError.InvalidKey, "Claude")
        assertFalse(run(scheduler()).retry)
        text = Outcome.Ok("Fine")
        reply = ApiResult.Failed(ApiError.RateLimited)
        val limited = run(scheduler())
        assertTrue(limited.retry)
        assertEquals("Kafka Partitions", limited.title)
        reply = ApiResult.Failed(ApiError.InvalidKey)
        val expired = run(scheduler())
        assertFalse(expired.retry)
        assertEquals("LinkedIn token expired or invalid. Sign in again in LinkedIn settings.", expired.record.message)
    }

    @Test
    fun `topic request without recent titles`() {
        assertEquals(
            "Angular: choose one specific, practical subject within Angular for a technical LinkedIn post, " +
                "such as a feature, pattern, pitfall or design problem an experienced developer would want explained.",
            AutoPostRun.topicRequest("Angular", emptyList()),
        )
    }
}

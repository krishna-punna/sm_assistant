package com.smassistant.app.scheduler

import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.ImageSize
import com.smassistant.app.generate.GenerateContentAndMediaUseCase
import com.smassistant.app.history.PostSource
import com.smassistant.app.history.PublishedPost
import com.smassistant.app.image.ImageRequest
import com.smassistant.app.linkedin.LinkedInPost
import com.smassistant.app.linkedin.PostToLinkedInUseCase
import com.smassistant.app.linkedin.SignInExpiry
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import com.smassistant.app.pipeline.Pipeline
import com.smassistant.app.pipeline.StageId
import com.smassistant.app.pipeline.StageStatus
import com.smassistant.app.prompt.PreparedPrompt
import com.smassistant.app.prompt.PromptSet
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.video.GeneratedVideo
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
    private val content = PreparedPrompt("Kafka Partitions In Depth", "Explain everything about partitions")
    private var text: Outcome<String> = Outcome.Ok("Kafka partitions explained")
    private var images: Outcome<List<File>> = Outcome.Ok(listOf(File("a.png"), File("b.png")))
    private var video: Outcome<GeneratedVideo> = Outcome.Ok(GeneratedVideo(File("v.mp4"), emptyList()))
    private var reply: ApiResult<LinkedInPost> = ApiResult.Ok(LinkedInPost("urn:li:share:7"))
    private val prompts = mutableListOf<String>()
    private val imageRequests = mutableListOf<ImageRequest>()
    private var expiry: SignInExpiry? = null

    private val linkedIn = PostToLinkedInUseCase(
        tags = { listOf("#java") },
        tagsEnabled = { tagsOn },
        configured = { configured },
        publish = { body, files -> published += body to files; reply },
        limit = 60,
    )

    private val run = AutoPostRun(
        preparePrompt = { topic, type ->
            topics += topic
            when (val post = prepare) {
                is Outcome.Ok -> Outcome.Ok(PromptSet(type, post.value.takeIf { type.post }, content.takeIf { type.media != null }))
                is Outcome.Failed -> post
                Outcome.NoAgent -> Outcome.NoAgent
            }
        },
        generateText = { prompts += it; text },
        generateImages = { imageRequests += it; prompts += "images: ${it.prompt}"; images },
        generateVideo = { prompts += "video: $it"; video },
        linkedIn = linkedIn,
        now = { 1000L },
        signInExpiry = { expiry },
    )

    private fun scheduler(type: PostType = PostType.TEXT) =
        AutoPostScheduler(topics = listOf("Kafka", "Java"), postType = type, recentTitles = listOf("Kafka Consumer Groups"))

    @Test
    fun `text post goes topic, prompt, content, hashtags, LinkedIn`() = runTest {
        val outcome = run(scheduler())
        assertEquals(RunRecord(1000L, true, "Posted “Kafka Partitions”", "https://www.linkedin.com/feed/update/urn:li:share:7"), outcome.record.copy(stages = null))
        assertEquals("Kafka Partitions", outcome.title)
        assertTrue(topics.single().startsWith("Kafka: choose one specific"))
        assertTrue(topics.single().contains("Kafka Consumer Groups"))
        assertEquals(listOf("Act as a Kafka expert"), prompts)
        assertEquals(listOf("Kafka partitions explained\n\n#java" to emptyList<File>()), published)
    }

    @Test
    fun `text and media posts write the post from the post prompt and the media from the content prompt`() = runTest {
        run(scheduler(PostType.TEXT_WITH_IMAGE))
        assertEquals("Kafka partitions explained\n\n#java" to listOf(File("a.png"), File("b.png")), published.last())
        run(scheduler(PostType.TEXT_WITH_VIDEO))
        assertEquals(listOf(File("v.mp4")), published.last().second)
        assertEquals(
            setOf("Act as a Kafka expert", "images: Explain everything about partitions", "video: Explain everything about partitions"),
            prompts.toSet(),
        )
    }

    @Test
    fun `image and video posts have the media with the title and hashtags as the caption`() = runTest {
        val outcome = run(scheduler(PostType.IMAGE))
        assertEquals("Posted “Kafka Partitions In Depth”", outcome.record.message)
        assertEquals("Kafka Partitions In Depth\n\n#java" to listOf(File("a.png"), File("b.png")), published.last())
        run(scheduler(PostType.VIDEO))
        assertEquals("Kafka Partitions In Depth\n\n#java" to listOf(File("v.mp4")), published.last())
        assertEquals(listOf("images: Explain everything about partitions", "video: Explain everything about partitions"), prompts)
    }

    @Test
    fun `every post type asks Prepare Prompt for its prompt type`() {
        assertEquals(
            listOf(PromptType.TEXT, PromptType.IMAGE, PromptType.VIDEO, PromptType.TEXT_AND_IMAGE, PromptType.TEXT_AND_VIDEO),
            PostType.entries.map { it.promptType },
        )
        assertEquals(listOf("Text", "Image", "Video", "Text & Image", "Text & Video"), PostType.entries.map { it.label })
    }

    @Test
    fun `an expired LinkedIn sign-in fails the run before anything is generated`() = runTest {
        expiry = SignInExpiry.Expired(500L)
        val outcome = run(scheduler())
        assertFalse(outcome.record.ok)
        assertFalse(outcome.retry)
        assertTrue(outcome.record.message.startsWith("Your LinkedIn sign-in expired"))
        assertTrue(topics.isEmpty())
        assertTrue(published.isEmpty())
    }

    @Test
    fun `a sign-in that expires soon still posts`() = runTest {
        expiry = SignInExpiry.Soon(5000L, daysLeft = 3)
        assertTrue(run(scheduler()).record.ok)
    }

    @Test
    fun `review first holds the finished post instead of publishing it`() = runTest {
        val outcome = run(scheduler(PostType.TEXT_WITH_IMAGE).copy(reviewFirst = true))
        assertTrue(published.isEmpty())
        assertTrue(outcome.record.ok)
        assertTrue(outcome.record.awaitingReview)
        assertEquals("Ready for your review: “Kafka Partitions”", outcome.record.message)
        assertEquals(PendingPost("Kafka Partitions", "Kafka partitions explained\n\n#java", listOf("a.png", "b.png"), 1000L), outcome.held)
    }

    @Test
    fun `image posts follow the scheduler's template and format and record the template used`() = runTest {
        images = Outcome.Ok(listOf(File("tpl-workflow-portrait-a.png"), File("tpl-concept-square-b.png")))

        val fixed = run(scheduler(PostType.IMAGE).copy(imageTemplate = "workflow", imageFormat = ImageSize.PORTRAIT))
        val held = run(scheduler(PostType.TEXT_WITH_IMAGE).copy(reviewFirst = true))

        assertEquals(
            listOf(
                ImageRequest("Explain everything about partitions", "workflow", ImageSize.PORTRAIT),
                ImageRequest("Explain everything about partitions"),
            ),
            imageRequests,
        )
        assertEquals("workflow", fixed.record.template)
        assertEquals("workflow", held.record.template)
        assertEquals("workflow", held.held!!.template)
        // Images without a template label (no text agent) record none.
        images = Outcome.Ok(listOf(File("a.png")))
        assertNull(run(scheduler(PostType.IMAGE)).record.template)
    }

    @Test
    fun `a carousel run records its page count`() = runTest {
        images = Outcome.Ok((1..5).map { File("carousel-3-p0$it-page.png") })
        assertEquals(5, run(scheduler(PostType.IMAGE)).record.carouselPages)
        images = Outcome.Ok(listOf(File("a.png"), File("b.png")))
        assertNull(run(scheduler(PostType.IMAGE)).record.carouselPages)
    }

    @Test
    fun `video posts record the template the video followed`() = runTest {
        video = Outcome.Ok(GeneratedVideo(File("vtpl-architecture-landscape-v.mp4"), emptyList()))
        assertEquals("architecture", run(scheduler(PostType.VIDEO)).record.template)
        video = Outcome.Ok(GeneratedVideo(File("v.mp4"), emptyList()))
        assertNull(run(scheduler(PostType.TEXT_WITH_VIDEO)).record.template)
    }

    @Test
    fun `a run is skipped while an earlier post waits for review`() = runTest {
        val waiting = PendingPost("Kafka Partitions", "text")
        val outcome = run(scheduler().copy(reviewFirst = true, pending = waiting))
        assertTrue(outcome.skipped)
        assertFalse(outcome.retry)
        assertEquals("Skipped: “Kafka Partitions” is still waiting for your review.", outcome.record.message)
        assertTrue(topics.isEmpty())
        assertTrue(published.isEmpty())
    }

    @Test
    fun `media that fails means nothing is posted`() = runTest {
        images = Outcome.Failed(ApiError.InvalidKey, "Gemini")
        val outcome = run(scheduler(PostType.TEXT_WITH_IMAGE))
        assertFalse(outcome.record.ok)
        assertEquals("Couldn't make the images. Gemini rejected the API key. Check it in settings.", outcome.record.message)
        assertTrue(published.isEmpty())

        assertFalse(run(scheduler(PostType.IMAGE)).record.ok)

        video = Outcome.NoAgent
        assertEquals(GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP, run(scheduler(PostType.TEXT_WITH_VIDEO)).record.message)
        assertEquals(GenerateContentAndMediaUseCase.VIDEO_NOT_SET_UP, run(scheduler(PostType.VIDEO)).record.message)
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
        text = Outcome.Failed(ApiError.Unreachable("Timed out"), "Gemini")
        assertTrue(run(scheduler()).retry)
        text = Outcome.Failed(ApiError.InvalidKey, "Gemini")
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

    @Test
    fun `scheduled posts go into the history and history titles are avoided`() = runTest {
        val history = mutableListOf<PublishedPost>()
        val recorded = PostToLinkedInUseCase(
            tags = { listOf("#java") },
            tagsEnabled = { tagsOn },
            configured = { configured },
            publish = { body, files -> published += body to files; reply },
            limit = 60,
            onPublished = { history += it },
        )
        val withHistory = AutoPostRun(
            preparePrompt = { topic, type -> topics += topic; Outcome.Ok(PromptSet(type, PreparedPrompt("Kafka Partitions", "p"), null)) },
            generateText = { text },
            generateImages = { images },
            generateVideo = { video },
            linkedIn = recorded,
            now = { 1000L },
            historyTitles = { listOf("Redis Eviction", "Kafka Consumer Groups") },
        )
        withHistory(scheduler())
        assertTrue(topics.single().contains("Kafka Consumer Groups; Redis Eviction"))
        assertEquals(PostSource.SCHEDULED, history.single().source)
        assertEquals("Kafka Partitions", history.single().title)
    }

    private fun statuses(p: Pipeline?) = p!!.stages.associate { it.id to it.status }

    @Test
    fun `a posted run records every stage done`() = runTest {
        val seen = mutableListOf<Pipeline>()
        val outcome = run(scheduler(PostType.TEXT_WITH_IMAGE)) { seen += it }
        assertEquals(
            listOf(StageId.TOPIC, StageId.PREPARE_PROMPT, StageId.WRITE_POST, StageId.MAKE_IMAGE, StageId.CHECK_LENGTH, StageId.POST),
            outcome.record.stages!!.stages.map { it.id },
        )
        assertTrue(statuses(outcome.record.stages).values.all { it == StageStatus.DONE })
        // The live updates went through the stages in order, ending with Post.
        assertEquals(StageStatus.RUNNING, seen.first()[StageId.TOPIC]?.status)
        assertEquals(StageStatus.DONE, seen.last()[StageId.POST]?.status)
    }

    @Test
    fun `a failed run fails the stage it stopped at`() = runTest {
        images = Outcome.Failed(ApiError.InvalidKey, "Gemini")
        val stages = statuses(run(scheduler(PostType.IMAGE)).record.stages)
        assertEquals(StageStatus.DONE, stages[StageId.PREPARE_PROMPT])
        assertEquals(StageStatus.FAILED, stages[StageId.MAKE_IMAGE])
        assertEquals(StageStatus.WAITING, stages[StageId.POST])
        assertNull(stages[StageId.WRITE_POST])
    }

    @Test
    fun `a LinkedIn refusal fails the Post stage`() = runTest {
        reply = ApiResult.Failed(ApiError.InvalidKey)
        val stages = run(scheduler()).record.stages!!
        assertEquals(StageStatus.FAILED, stages[StageId.POST]?.status)
        assertEquals("LinkedIn token expired or invalid. Sign in again in LinkedIn settings.", stages[StageId.POST]?.note)
    }

    @Test
    fun `a review-first run ends holding for review`() = runTest {
        val stages = run(scheduler().copy(reviewFirst = true)).record.stages!!
        assertEquals(StageStatus.RUNNING, stages[StageId.HOLD]?.status)
        assertEquals("Waiting for your review", stages.statusLine)
    }

    @Test
    fun `no LinkedIn fails the first stage`() = runTest {
        configured = false
        assertEquals(StageStatus.FAILED, run(scheduler()).record.stages!![StageId.TOPIC]?.status)
    }
}

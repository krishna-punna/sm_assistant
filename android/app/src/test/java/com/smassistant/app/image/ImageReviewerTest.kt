package com.smassistant.app.image

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.image.render.InfographicSpec
import com.smassistant.app.image.render.Section
import com.smassistant.app.image.render.SectionBody
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text agent that can see images and answers each look with the next of [replies]. */
class SeeingTextAgent(vararg replies: ApiResult<String>) : TextAgent {
    override val service = ApiService.GEMINI
    private val queue = ArrayDeque(replies.toList())
    val looks = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> = error("not used")

    override suspend fun see(system: String, user: String, jpeg: ByteArray): ApiResult<String> {
        synchronized(looks) { looks += user to jpeg }
        return synchronized(queue) { if (queue.size > 1) queue.removeFirst() else queue.first() }
    }
}

class ImageReviewerTest {
    private val spec = InfographicSpec(
        title = "Caching",
        subtitle = "Faster reads",
        sections = listOf(Section("Cache-aside", SectionBody.Bullets(listOf("a"))), Section("TTL", SectionBody.Bullets(listOf("b")))),
        takeaway = "Cache what you read often",
    )
    private val reviewer = ImageReviewer("Review.", { byteArrayOf(7) }, minScore = 7)

    @Test
    fun `sends the image with the topic and plan and reads the score`() = runTest {
        val agent = SeeingTextAgent(ApiResult.Ok("""```json
            {"score": 6, "problems": ["The TTL card's text is cut off", " "]}
            ```"""))

        val review = reviewer.review(agent, " Caching ", spec, File("a.png"))

        assertEquals(ImageReview(6, listOf("The TTL card's text is cut off")), review)
        assertFalse(reviewer.passes(review!!))
        val (user, jpeg) = agent.looks.single()
        assertEquals(
            "Topic: Caching\n\nPlanned title: Caching\nPlanned subtitle: Faster reads\nPlanned sections: Cache-aside; TTL\nPlanned takeaway: Cache what you read often",
            user,
        )
        assertEquals(listOf<Byte>(7), jpeg.toList())
    }

    @Test
    fun `no review when the model can't see, the call fails or the reply has no score`() = runTest {
        assertNull(reviewer.review(ScriptedTextAgent(ApiResult.Ok("x")), "t", spec, File("a.png")))
        assertNull(reviewer.review(SeeingTextAgent(ApiResult.Failed(ApiError.RateLimited)), "t", spec, File("a.png")))
        assertNull(reviewer.review(SeeingTextAgent(ApiResult.Ok("Looks great!")), "t", spec, File("a.png")))
        assertNull(ImageReviewer("Review.", { null }).review(SeeingTextAgent(ApiResult.Ok("""{"score": 9}""")), "t", spec, File("a.png")))
    }

    @Test
    fun `scores are read leniently and kept to 0 to 10`() {
        assertEquals(ImageReview(8, emptyList()), parseImageReview("""{"score": "8.5"}"""))
        assertEquals(10, parseImageReview("""{"score": 14, "problems": []}""")!!.score)
        assertNull(parseImageReview("""{"problems": ["x"]}"""))
        assertTrue(reviewer.passes(ImageReview(7, listOf("tiny nit"))))
    }

    @Test
    fun `review prompt asset asks for a score and problems as JSON`() {
        val asset = File("src/main/assets/prompts/image/image_review.md").readText()
        listOf("\"score\"", "\"problems\"", "garbled", "cut off").forEach { assertTrue("image_review.md is missing $it", asset.contains(it)) }
    }

    @Test
    fun `the request lists the template's rules when there is one`() {
        val spec = InfographicSpec("Caching", sections = listOf(Section("TTL", SectionBody.Bullets(listOf("expire")))))
        val template = ImageTemplate("data", "Data and Metrics", "", "Intro.\n\n- Use only supplied values.\n  - Do not imply causation.\nAspect ratio: {{format}}.")
        val request = reviewRequest("Caching", spec, template)
        assertTrue(request.endsWith("Image template: Data and Metrics. Also check these rules of the template (as problem 7):\n- Use only supplied values.\n- Do not imply causation."))
        assertFalse(reviewRequest("Caching", spec).contains("Image template"))
    }
}

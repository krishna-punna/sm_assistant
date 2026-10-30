package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ImageFormat
import com.smassistant.app.data.ImageStyle
import com.smassistant.app.infographic.InfographicFormat
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text agent that answers with [replies] in turn and records what it was sent. */
class ScriptedTextAgent(vararg replies: ApiResult<String>) : TextAgent {
    override val service = ApiService.GROQ
    private val queue = ArrayDeque(replies.toList())
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        synchronized(calls) { calls += system to user }
        return synchronized(queue) { if (queue.size > 1) queue.removeFirst() else queue.first() }
    }
}

class InfographicWriterTest {
    private val examples = listOf(
        ImageExample("SQL", listOf("sql"), "SQL tuning", "Before/after grid."),
        ImageExample("Kafka", listOf("kafka"), "Kafka patterns", "Five columns."),
    )
    private val writer = InfographicWriter("Plan it.\n{{examples}}", examples)

    @Test
    fun `sends the topic, layout idea and closest examples, and reads the definition`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""```json
            {"title": "Kafka Retries", "sections": [{"heading": "Why", "items": ["at least once"]},
              {"heading": "Backoff", "items": ["double the wait"]}, {"heading": "Dead letters", "items": ["park poison messages"]}]}
            ```"""))

        val result = writer.write(agent, "Kafka retries", "a square grid")

        assertEquals("Kafka Retries", (result as ApiResult.Ok).value.title)
        assertEquals("Plan it.\nExample 1: Kafka\nTopic: Kafka patterns\nImage: Five columns." to "Topic: Kafka retries\n\nLayout idea: a square grid", agent.calls.single())
    }

    @Test
    fun `content steps reject empty replies and pass errors through`() = runTest {
        val content = MediaContentWriter("Write it all.")
        assertEquals(ApiResult.Ok("Body"), content.write(ScriptedTextAgent(ApiResult.Ok("  Body \n")), "x"))
        assertTrue(content.write(ScriptedTextAgent(ApiResult.Ok("  ")), "x") is ApiResult.Failed)
        assertEquals(ApiResult.Failed(ApiError.RateLimited), content.write(ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited)), "x"))
    }

    @Test
    fun `asks once more when the reply isn't a definition`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("Sure! Here you go."), ApiResult.Ok("""{"title": "Second try"}"""))
        assertEquals("Second try", (writer.write(agent, "x", "y") as ApiResult.Ok).value.title)
        assertEquals(2, agent.calls.size)
    }

    @Test
    fun `fails after two bad replies, and passes API errors through without retrying`() = runTest {
        val bad = ScriptedTextAgent(ApiResult.Ok("""{"subtitle": "no title"}"""))
        assertEquals(ApiResult.Failed(ApiError.BadResponse("the infographic has no title")), writer.write(bad, "x", "y"))
        assertEquals(2, bad.calls.size)

        val limited = ScriptedTextAgent(ApiResult.Failed(ApiError.RateLimited))
        assertEquals(ApiResult.Failed(ApiError.RateLimited), writer.write(limited, "x", "y"))
        assertEquals(1, limited.calls.size)
    }

    @Test
    fun `a definition with problems is asked for again with the problems named`() = runTest {
        val thin = """{"title": "Kafka Retries", "sections": [{"heading": "Why", "items": ["a"]}]}"""
        val full = """{"title": "Kafka Retries", "sections": [{"heading": "A", "items": ["a"]}, {"heading": "B", "items": ["b"]}, {"heading": "C", "items": ["c"]}]}"""
        val agent = ScriptedTextAgent(ApiResult.Ok(thin), ApiResult.Ok(full))

        val result = writer.write(agent, "Kafka retries", "a grid")

        assertEquals(3, (result as ApiResult.Ok).value.sections.size)
        val retry = agent.calls[1].second
        assertTrue(retry, retry.startsWith("Topic: Kafka retries\n\nLayout idea: a grid\n\nYour last reply couldn't be used: it has 1 sections"))
        assertTrue(retry.endsWith("Reply with the corrected JSON only."))
    }

    @Test
    fun `a definition that still has problems on the second try is used rather than failing`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""{"title": "Docker"}"""), ApiResult.Ok("""{"title": "Kafka", "sections": [{"heading": "A", "items": ["a"]}]}"""))
        assertEquals("Kafka", (writer.write(agent, "Kafka retries", "y") as ApiResult.Ok).value.title)
        assertEquals(2, agent.calls.size)
    }

    @Test
    fun `review feedback is passed to the text agent`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""{"title": "x"}"""))
        writer.write(agent, "x", "y", feedback = listOf("The TTL card's text is cut off"))
        assertEquals(
            "Topic: x\n\nLayout idea: y\n\nA review rejected the last image drawn for this topic:\n- The TTL card's text is cut off\nWrite a new definition that fixes these problems.",
            agent.calls.first().second,
        )
    }

    @Test
    fun `infographic prompt asset keeps the examples placeholder and names every icon`() {
        val text = File("src/main/assets/prompts/infographic.md").readText()
        assertTrue(text.contains("{{examples}}"))
        com.smassistant.app.infographic.ICONS.keys.forEach { assertTrue(it, text.contains(it)) }
    }

    @Test
    fun `a style guide asks for the closest palette`() = runTest {
        val reply = ApiResult.Ok("""{"title": "Kafka Retries", "sections": [{"heading": "Why", "items": ["at least once"]},
              {"heading": "Backoff", "items": ["double the wait"]}, {"heading": "Dead letters", "items": ["park poison messages"]}]}""")
        val template = ImageTemplate("t", "T", "", "{{content}} {{style}}")
        val styled = ScriptedTextAgent(reply)
        val plain = ScriptedTextAgent(reply)

        writer.write(styled, "Kafka retries", "an idea", brief = ImageBrief(template, "", ImageFormat.SQUARE, ImageStyle(styleGuide = "navy and teal")))
        writer.write(plain, "Kafka retries", "an idea", brief = ImageBrief(template, "", ImageFormat.SQUARE, ImageStyle()))

        assertTrue(styled.calls.single().second.contains("Pick the palette closest to the style guide \"navy and teal\"."))
        assertFalse(plain.calls.single().second.contains("Pick the palette"))
    }

    @Test
    fun `a brief sets the format and the user's brand, whatever the model wrote`() = runTest {
        val agent = ScriptedTextAgent(ApiResult.Ok("""{"title": "Kafka Retries", "format": "portrait", "sections": [{"heading": "Why", "items": ["at least once"]},
              {"heading": "Backoff", "items": ["double the wait"]}, {"heading": "Dead letters", "items": ["park poison messages"]}]}"""))
        val brief = ImageBrief(ImageTemplate("t", "T", "", "{{content}} {{format}}"), "", ImageFormat.SQUARE, ImageStyle(brandName = "Acme"))

        val spec = (writer.write(agent, "Kafka retries", "an idea", brief = brief) as ApiResult.Ok).value

        assertEquals(InfographicFormat.SQUARE, spec.format)
        assertEquals("Acme", spec.brand)
    }
}

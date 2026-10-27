package com.smassistant.app.generate

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            {"title": "Kafka Retries", "sections": [{"heading": "Why", "items": ["at least once"]}]}
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
    fun `infographic prompt asset keeps the examples placeholder and names every icon`() {
        val text = File("src/main/assets/prompts/infographic.md").readText()
        assertTrue(text.contains("{{examples}}"))
        com.smassistant.app.infographic.ICONS.keys.forEach { assertTrue(it, text.contains(it)) }
    }
}

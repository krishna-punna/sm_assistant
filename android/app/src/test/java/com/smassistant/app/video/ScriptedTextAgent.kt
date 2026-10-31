package com.smassistant.app.video

import com.smassistant.app.agents.TextAgent
import com.smassistant.app.data.ApiService
import com.smassistant.app.net.ApiResult

/** A text agent that answers with [replies] in turn and records what it was sent (the video tests' own). */
class ScriptedTextAgent(vararg replies: ApiResult<String>) : TextAgent {
    override val service = ApiService.GROQ
    private val queue = ArrayDeque(replies.toList())
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun generate(system: String, user: String): ApiResult<String> {
        synchronized(calls) { calls += system to user }
        return synchronized(queue) { if (queue.size > 1) queue.removeFirst() else queue.first() }
    }
}

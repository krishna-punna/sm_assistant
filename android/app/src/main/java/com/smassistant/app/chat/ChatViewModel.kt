package com.smassistant.app.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What one generation produced. */
sealed interface Outcome<out R> {
    data class Ok<R>(val value: R) : Outcome<R>
    data class Failed(val error: ApiError, val serviceName: String) : Outcome<Nothing>
    /** No agent is set up for this feature (GEN-1). */
    data object NoAgent : Outcome<Nothing>
}

/** One entry in a feature screen's conversation. */
sealed interface ChatItem<out R> {
    val id: Long

    /** What the user submitted: a topic or a prepared prompt. */
    data class Input(override val id: Long, val text: String) : ChatItem<Nothing>
    data class Loading(override val id: Long, val forInput: Long) : ChatItem<Nothing>
    data class Failure(override val id: Long, val forInput: Long, val message: String) : ChatItem<Nothing>
    data class NoAgent(override val id: Long, val forInput: Long) : ChatItem<Nothing>
    data class Result<R>(override val id: Long, val forInput: Long, val value: R) : ChatItem<R>
}

data class ChatUiState<R>(
    val items: List<ChatItem<R>> = emptyList(),
    val input: String = "",
) {
    val busy get() = items.any { it is ChatItem.Loading }
}

/**
 * Shared chat-style flow for the six hub features (docs/requirements/06, GEN-1 to GEN-4):
 * submit → the input is shown and the box is cleared → a loading item → the result, an error
 * with Retry, or a "set up an agent" item. Earlier exchanges stay on screen (PP-6).
 *
 * The typed input and the conversation are kept in [handle], so they come back after the
 * process is killed in the background (NAV-4). Results are kept only when there's a [saver];
 * a generation that was running comes back as an error with Retry.
 */
class ChatViewModel<R>(
    private val handle: SavedStateHandle = SavedStateHandle(),
    private val saver: ResultSaver<R>? = null,
    private val generate: suspend (String) -> Outcome<R>,
) : ViewModel() {
    private val _state = MutableStateFlow(restore())
    val state: StateFlow<ChatUiState<R>> = _state.asStateFlow()

    private var nextId = (_state.value.items.maxOfOrNull { it.id } ?: -1) + 1
    private var job: Job? = null

    fun onInputChange(text: String) = edit { it.copy(input = text) }

    fun submit() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy) return
        val input = ChatItem.Input(nextId++, text)
        edit { it.copy(items = it.items + input, input = "") }
        run(input)
    }

    /** Runs the generation for the input behind [item] again, in place of [item]. */
    fun retry(item: ChatItem<R>) {
        if (_state.value.busy) return
        val forInput = when (item) {
            is ChatItem.Failure -> item.forInput
            is ChatItem.NoAgent -> item.forInput
            else -> return
        }
        val input = _state.value.items.filterIsInstance<ChatItem.Input>().firstOrNull { it.id == forInput } ?: return
        edit { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }
        run(input)
    }

    fun cancel() {
        job?.cancel()
        edit { s ->
            s.copy(items = s.items.map {
                if (it is ChatItem.Loading) ChatItem.Failure(it.id, it.forInput, "Cancelled") else it
            })
        }
    }

    /**
     * Reworks part of an existing result in place, e.g. retrying the half of a combined
     * result that failed (CI-I1). [pending] is shown at once; [work] gets the input text.
     */
    fun updateResult(id: Long, pending: (R) -> R = { it }, work: suspend (input: String, value: R) -> R) {
        val item = _state.value.items.firstOrNull { it.id == id } as? ChatItem.Result<R> ?: return
        val input = _state.value.items.filterIsInstance<ChatItem.Input>().firstOrNull { it.id == item.forInput } ?: return
        val started = pending(item.value)
        replaceResult(id, started)
        viewModelScope.launch { replaceResult(id, work(input.text, started)) }
    }

    private fun replaceResult(id: Long, value: R) = edit { s ->
        s.copy(items = s.items.map { if (it.id == id && it is ChatItem.Result) ChatItem.Result(it.id, it.forInput, value) else it })
    }

    private fun run(input: ChatItem.Input) {
        val loading = ChatItem.Loading(nextId++, input.id)
        edit { it.copy(items = it.items + loading) }
        job = viewModelScope.launch {
            val outcome = generate(input.text)
            val replacement: ChatItem<R> = when (outcome) {
                is Outcome.Ok -> ChatItem.Result(loading.id, input.id, outcome.value)
                is Outcome.Failed -> ChatItem.Failure(loading.id, input.id, outcome.error.userMessage(outcome.serviceName))
                Outcome.NoAgent -> ChatItem.NoAgent(loading.id, input.id)
            }
            edit { s -> s.copy(items = s.items.map { if (it.id == loading.id) replacement else it }) }
        }
    }

    private fun edit(change: (ChatUiState<R>) -> ChatUiState<R>) {
        val before = _state.value
        _state.update(change)
        val after = _state.value
        handle[INPUT] = after.input
        // Typing changes only the input, so the conversation is written again only when it changes.
        if (after.items !== before.items) saveItems(after)
    }

    private fun saveItems(state: ChatUiState<R>) {
        if (saver == null) return
        handle[ITEMS] = JsonArray(state.items.map { item ->
            buildJsonObject {
                put("id", item.id)
                when (item) {
                    is ChatItem.Input -> put("text", item.text)
                    is ChatItem.Loading -> { put("for", item.forInput); put("failed", STOPPED) }
                    is ChatItem.Failure -> { put("for", item.forInput); put("failed", item.message) }
                    is ChatItem.NoAgent -> { put("for", item.forInput); put("noAgent", true) }
                    is ChatItem.Result -> { put("for", item.forInput); put("result", saver.save(item.value)) }
                }
            }
        }).toString()
    }

    private fun restore(): ChatUiState<R> {
        val input = handle.get<String>(INPUT) ?: ""
        val saved = handle.get<String>(ITEMS)
        if (saver == null || saved == null) return ChatUiState(input = input)
        val items = runCatching { Json.parseToJsonElement(saved).jsonArray.map { restoreItem(it as JsonObject, saver) } }
        return ChatUiState(items.getOrDefault(emptyList()), input)
    }

    private fun restoreItem(json: JsonObject, saver: ResultSaver<R>): ChatItem<R> {
        val id = json.getValue("id").jsonPrimitive.long
        val text = json["text"]
        if (text != null) return ChatItem.Input(id, text.jsonPrimitive.content)
        val forInput = json.getValue("for").jsonPrimitive.long
        val result: JsonElement? = json["result"]
        return when {
            result != null -> saver.restore(result)?.let { ChatItem.Result(id, forInput, it) } ?: ChatItem.Failure(id, forInput, MEDIA_GONE)
            json["noAgent"] != null -> ChatItem.NoAgent(id, forInput)
            else -> ChatItem.Failure(id, forInput, json.getValue("failed").jsonPrimitive.content)
        }
    }

    private companion object {
        const val INPUT = "input"
        const val ITEMS = "items"
    }
}

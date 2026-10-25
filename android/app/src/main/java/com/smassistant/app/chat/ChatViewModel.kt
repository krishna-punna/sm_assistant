package com.smassistant.app.chat

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
 */
class ChatViewModel<R>(private val generate: suspend (String) -> Outcome<R>) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState<R>())
    val state: StateFlow<ChatUiState<R>> = _state.asStateFlow()

    private var nextId = 0L
    private var job: Job? = null

    fun onInputChange(text: String) = _state.update { it.copy(input = text) }

    fun submit() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy) return
        val input = ChatItem.Input(nextId++, text)
        _state.update { it.copy(items = it.items + input, input = "") }
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
        _state.update { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }
        run(input)
    }

    fun cancel() {
        job?.cancel()
        _state.update { s ->
            s.copy(items = s.items.map {
                if (it is ChatItem.Loading) ChatItem.Failure(it.id, it.forInput, "Cancelled") else it
            })
        }
    }

    private fun run(input: ChatItem.Input) {
        val loading = ChatItem.Loading(nextId++, input.id)
        _state.update { it.copy(items = it.items + loading) }
        job = viewModelScope.launch {
            val outcome = generate(input.text)
            val replacement: ChatItem<R> = when (outcome) {
                is Outcome.Ok -> ChatItem.Result(loading.id, input.id, outcome.value)
                is Outcome.Failed -> ChatItem.Failure(loading.id, input.id, outcome.error.userMessage(outcome.serviceName))
                Outcome.NoAgent -> ChatItem.NoAgent(loading.id, input.id)
            }
            _state.update { s -> s.copy(items = s.items.map { if (it.id == loading.id) replacement else it }) }
        }
    }
}

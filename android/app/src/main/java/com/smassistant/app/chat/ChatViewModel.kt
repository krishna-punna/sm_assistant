package com.smassistant.app.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.userMessage
import com.smassistant.app.pipeline.Pipeline
import com.smassistant.app.pipeline.StageId
import com.smassistant.app.pipeline.StageReporter
import com.smassistant.app.pipeline.StageStatus
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

/**
 * One feature's conversation, kept for as long as the app's process lives so it outlasts the
 * screen (and its ViewModel). [persist] saves a change to the current screen's saved state.
 */
class ChatSession<R> {
    val state = MutableStateFlow(ChatUiState<R>())
    internal var started = false
    internal var nextId = 0L
    internal var job: Job? = null
    /** The running generation's stages, so Cancel changes the same pipeline the generation reports to. */
    internal var tracking: StageReporter.Tracking? = null
    internal var persist: ((before: ChatUiState<R>, after: ChatUiState<R>) -> Unit)? = null
}

/**
 * The app's conversations by feature, and the scope their generations run in. Generations are
 * no longer tied to a screen: going back to Home while a video is being made doesn't cancel it.
 */
object ChatSessions {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Per Application object, so each Robolectric test (a new Application) starts empty. */
    private val sessions = WeakHashMap<Context, MutableMap<String, ChatSession<*>>>()

    @Suppress("UNCHECKED_CAST")
    fun <R> get(context: Context, key: String): ChatSession<R> =
        sessions.getOrPut(context.applicationContext) { mutableMapOf() }.getOrPut(key) { ChatSession<R>() } as ChatSession<R>
}

/** A feature screen's [ChatViewModel], on the app's [ChatSessions] so generations outlive the screen. */
fun <R> sessionChatViewModel(
    context: Context,
    key: String,
    handle: SavedStateHandle,
    saver: ResultSaver<R>,
    pipeline: Pipeline? = null,
    finished: (R, Pipeline) -> Pipeline = { _, p -> p.finishGeneration() },
    generate: suspend (String) -> Outcome<R>,
) = ChatViewModel(handle, saver, ChatSessions.get(context, key), ChatSessions.scope, pipeline, finished, generate)

/** The input an item belongs to: its own id for an input, otherwise the input it answers. */
private val ChatItem<*>.inputId: Long
    get() = when (this) {
        is ChatItem.Input -> id
        is ChatItem.Loading -> forInput
        is ChatItem.Failure -> forInput
        is ChatItem.NoAgent -> forInput
        is ChatItem.Result -> forInput
    }

data class ChatUiState<R>(
    val items: List<ChatItem<R>> = emptyList(),
    val input: String = "",
    /** Each input's stages from the prompt to LinkedIn, by the input's id (docs/requirements/20). */
    val pipelines: Map<Long, Pipeline> = emptyMap(),
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
 *
 * With a [session] and a [scope] that outlive the screen (the app's), leaving the screen doesn't
 * stop a generation: it finishes in the background and the screen shows it when opened again.
 */
class ChatViewModel<R>(
    private val handle: SavedStateHandle = SavedStateHandle(),
    private val saver: ResultSaver<R>? = null,
    private val session: ChatSession<R> = ChatSession(),
    private val scope: CoroutineScope? = null,
    /** The feature's stages; null shows none (docs/requirements/20, PS-1). */
    private val pipeline: Pipeline? = null,
    /** The stages once a generation came back with a result, e.g. a Content & Image half that failed. */
    private val finished: (R, Pipeline) -> Pipeline = { _, p -> p.finishGeneration() },
    private val generate: suspend (String) -> Outcome<R>,
) : ViewModel() {
    private val _state = session.state
    val state: StateFlow<ChatUiState<R>> = _state.asStateFlow()

    init {
        if (!session.started) {
            session.started = true
            _state.value = restore()
            session.nextId = (_state.value.items.maxOfOrNull { it.id } ?: -1) + 1
        }
        // The newest screen's saved state is the one to keep up to date, including when a
        // generation started on an earlier screen finishes.
        session.persist = ::persist
        persist(ChatUiState(), _state.value)
    }

    private val jobScope get() = scope ?: viewModelScope
    private var nextId: Long
        get() = session.nextId
        set(value) { session.nextId = value }

    fun onInputChange(text: String) = edit { it.copy(input = text) }

    fun submit() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.busy) return
        val input = ChatItem.Input(nextId++, text)
        edit { it.copy(items = it.items + input, input = "") }
        run(input)
    }

    /** Runs the generation for the input behind [item] (a failure, or a result to regenerate) again, in place of [item]. */
    fun retry(item: ChatItem<R>) {
        if (_state.value.busy) return
        val forInput = when (item) {
            is ChatItem.Failure -> item.forInput
            is ChatItem.NoAgent -> item.forInput
            is ChatItem.Result -> item.forInput
            else -> return
        }
        val input = _state.value.items.filterIsInstance<ChatItem.Input>().firstOrNull { it.id == forInput } ?: return
        edit { s -> s.copy(items = s.items.filterNot { it.id == item.id }) }
        run(input)
    }

    fun cancel() {
        session.job?.cancel()
        edit { s ->
            s.copy(items = s.items.map {
                if (it is ChatItem.Loading) ChatItem.Failure(it.id, it.forInput, "Cancelled") else it
            })
        }
        session.tracking?.update { it.failGeneration("Cancelled") }
        session.tracking = null
    }

    /** Sets stage [id] of [inputId]'s pipeline, e.g. the Post stage from the Post in LinkedIn flow (PS-5). */
    fun markStage(inputId: Long, id: StageId, status: StageStatus, note: String? = null) = edit { s ->
        val p = s.pipelines[inputId] ?: return@edit s
        s.copy(pipelines = s.pipelines + (inputId to p.set(id, status, note)))
    }

    private fun setPipeline(inputId: Long, p: Pipeline) = edit { s ->
        // A pipeline whose input was dropped (an old exchange trimmed away) isn't brought back.
        if (s.items.none { it is ChatItem.Input && it.id == inputId }) s else s.copy(pipelines = s.pipelines + (inputId to p))
    }

    /** Tracks [inputId]'s stages from [start], on the main thread whatever thread the generation reports from. */
    private fun tracking(inputId: Long, start: Pipeline): StageReporter.Tracking {
        lateinit var tracking: StageReporter.Tracking
        tracking = StageReporter.Tracking(start) { jobScope.launch { setPipeline(inputId, tracking.pipeline) } }
        setPipeline(inputId, start)
        return tracking
    }

    /**
     * Reworks part of an existing result in place, e.g. retrying the half of a combined
     * result that failed (CI-I1). [pending] is shown at once; [work] gets the input text.
     */
    fun updateResult(
        id: Long,
        pending: (R) -> R = { it },
        restart: Set<StageId> = emptySet(),
        work: suspend (input: String, value: R) -> R,
    ) {
        val item = _state.value.items.firstOrNull { it.id == id } as? ChatItem.Result<R> ?: return
        val input = _state.value.items.filterIsInstance<ChatItem.Input>().firstOrNull { it.id == item.forInput } ?: return
        val started = pending(item.value)
        replaceResult(id, started)
        // The stages being done again start over; the rest keep where they are.
        val tracking = _state.value.pipelines[input.id]?.let { p ->
            tracking(input.id, restart.fold(p) { acc, stage -> acc.reset(stage) }.let { acc -> restart.firstOrNull()?.let(acc::start) ?: acc })
        }
        jobScope.launch {
            val value = if (tracking == null) work(input.text, started) else withContext(tracking.reporter) { work(input.text, started) }
            tracking?.update { finished(value, it) }
            replaceResult(id, value)
        }
    }

    private fun replaceResult(id: Long, value: R) = edit { s ->
        s.copy(items = s.items.map { if (it.id == id && it is ChatItem.Result) ChatItem.Result(it.id, it.forInput, value) else it })
    }

    private fun run(input: ChatItem.Input) {
        val loading = ChatItem.Loading(nextId++, input.id)
        edit { it.copy(items = it.items + loading) }
        // A fresh row for every run (a Retry starts over): the prompt is in, the first step starts.
        val tracking = pipeline?.let { template ->
            val start = template.done(StageId.PROMPT)
            tracking(input.id, start.generation.firstOrNull { it.status == StageStatus.WAITING }?.let { start.start(it.id) } ?: start)
        }
        session.tracking = tracking
        session.job = jobScope.launch {
            val outcome = if (tracking == null) generate(input.text) else withContext(tracking.reporter) { generate(input.text) }
            tracking?.update { p ->
                when (outcome) {
                    is Outcome.Ok -> finished(outcome.value, p)
                    is Outcome.Failed -> p.failGeneration(outcome.error.userMessage(outcome.serviceName))
                    Outcome.NoAgent -> p.failGeneration(NO_AGENT)
                }
            }
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
        session.persist?.invoke(before, _state.value)
    }

    private fun persist(before: ChatUiState<R>, after: ChatUiState<R>) {
        handle[INPUT] = after.input
        // Typing changes only the input, so the conversation is written again only when it changes.
        if (after.items !== before.items || after.pipelines !== before.pipelines) saveItems(after)
    }

    /**
     * Saved state lives in a Bundle that Android limits to about 1 MB for the whole app, so a long
     * conversation is saved without its oldest exchanges once it passes [MAX_SAVED_CHARS]. The
     * screen still shows everything; only a restore after process death starts later.
     */
    private fun saveItems(state: ChatUiState<R>) {
        val saver = saver ?: return
        var items = state.items
        var json = encode(items, saver, state.pipelines)
        while (json.length > MAX_SAVED_CHARS && items.isNotEmpty()) {
            val oldest = items.first().inputId
            items = items.filterNot { it.inputId == oldest }
            json = encode(items, saver, state.pipelines)
        }
        handle[ITEMS] = json
    }

    private fun encode(items: List<ChatItem<R>>, saver: ResultSaver<R>, pipelines: Map<Long, Pipeline>): String =
        JsonArray(items.map { item ->
            buildJsonObject {
                put("id", item.id)
                when (item) {
                    is ChatItem.Input -> {
                        put("text", item.text)
                        pipelines[item.id]?.let { put("stages", it.toJson()) }
                    }
                    is ChatItem.Loading -> { put("for", item.forInput); put("failed", STOPPED) }
                    is ChatItem.Failure -> { put("for", item.forInput); put("failed", item.message) }
                    is ChatItem.NoAgent -> { put("for", item.forInput); put("noAgent", true) }
                    is ChatItem.Result -> { put("for", item.forInput); put("result", saver.save(item.value)) }
                }
            }
        }).toString()

    private fun restore(): ChatUiState<R> {
        val input = handle.get<String>(INPUT) ?: ""
        val saver = saver
        val saved = handle.get<String>(ITEMS)
        if (saver == null || saved == null) return ChatUiState(input = input)
        val json = runCatching { Json.parseToJsonElement(saved).jsonArray.map { it as JsonObject } }.getOrDefault(emptyList())
        val items = runCatching { json.map { restoreItem(it, saver) } }.getOrDefault(emptyList())
        // A stage that was running when the app was stopped didn't finish (PS-6).
        val pipelines = json.mapNotNull { o ->
            val stages = Pipeline.fromJson(o["stages"]) ?: return@mapNotNull null
            o.getValue("id").jsonPrimitive.long to stages.stopped()
        }.toMap()
        return ChatUiState(items, input, if (items.isEmpty()) emptyMap() else pipelines)
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

    companion object {
        private const val INPUT = "input"
        private const val ITEMS = "items"
        const val NO_AGENT = "No AI agent is set up"

        /** About 200 KB as saved; leaves room in the 1 MB limit for the other screens on the back stack. */
        const val MAX_SAVED_CHARS = 100_000
    }
}

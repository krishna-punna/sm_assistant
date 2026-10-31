package com.smassistant.app.scheduler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smassistant.app.data.ImageSize
import com.smassistant.app.pipeline.Pipeline
import com.smassistant.app.pipeline.StageId
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Tue 27 Oct, 09:00" for the list and the editor's preview. */
fun formatRunTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** [message] is a one-off note for a toast, e.g. after Run now. */
data class SchedulersUiState(
    val schedulers: List<AutoPostScheduler> = emptyList(),
    val message: String? = null,
    /** The live stages of the runs going now, by scheduler id. */
    val running: Map<String, Pipeline> = emptyMap(),
)

/**
 * Scheduled Posts list (SC-I5): every saved scheduler, its switch, Run now and Delete (SC-8, SC-10).
 * The [engine] is only created on the first change, so opening the screen doesn't need WorkManager.
 */
class SchedulersViewModel(
    private val store: SchedulerStore,
    private val now: () -> Long = System::currentTimeMillis,
    /** The stages of each scheduler's run in progress, by scheduler id (docs/requirements/20, PS-7). */
    running: Flow<Map<String, Pipeline>>? = null,
    engine: () -> SchedulerEngine,
) : ViewModel() {
    /** The scheduler whose waiting post is open in the review dialog. */
    private var reviewing: String? = null

    private val engine by lazy(engine)
    private val _state = MutableStateFlow(SchedulersUiState(store.all()))
    val state: StateFlow<SchedulersUiState> = _state.asStateFlow()

    init {
        if (running != null) viewModelScope.launch {
            var wasRunning = emptySet<String>()
            running.collect { live ->
                // A run that just ended has written its last run; read it so the card shows where it ended.
                val ended = wasRunning - live.keys
                wasRunning = live.keys
                _state.update { it.copy(running = live, schedulers = if (ended.isNotEmpty()) store.all() else it.schedulers) }
            }
        }
    }

    /** Re-reads the list, e.g. when the screen comes back after a background run. */
    fun refresh() = _state.update { it.copy(schedulers = store.all()) }

    fun setEnabled(id: String, enabled: Boolean) {
        engine.setEnabled(id, enabled)
        refresh()
    }

    fun runNow(id: String) {
        engine.runNow(id)
        _state.update { it.copy(message = "Running now. You'll get a notification when it's done.") }
    }

    fun delete(id: String) {
        engine.delete(id)
        refresh()
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Opens [id]'s waiting post for review: its text and media files, or null (with a message)
     * when it's gone or its media was cleared from the phone's cache.
     */
    fun review(id: String): Pair<String, List<File>>? {
        val post = store.get(id)?.pending
        if (post == null) {
            refresh()
            return null
        }
        val files = post.media.map(::File)
        if (files.any { !it.exists() }) {
            _state.update { it.copy(message = "This post's media is no longer on the phone. Discard it and use Run now to make a new one.") }
            return null
        }
        reviewing = id
        return post.text to files
    }

    /** The reviewed post went out: it's no longer waiting, and the list links to it. */
    fun reviewPosted(url: String) {
        val id = reviewing ?: return
        reviewing = null
        store.update(id) { scheduler ->
            val title = scheduler.pending?.title.orEmpty()
            val template = scheduler.pending?.template
            val stages = scheduler.lastRun?.stages?.done(StageId.HOLD, "Posted to LinkedIn")
            scheduler.copy(pending = null, lastRun = RunRecord(now(), ok = true, message = "Posted “$title”", postUrl = url, template = template, stages = stages))
        }
        refresh()
    }

    /** Throws away [id]'s waiting post, so its next run makes a new one. */
    fun discard(id: String) {
        store.update(id) { s -> s.copy(pending = null, lastRun = s.lastRun?.let { run -> run.copy(stages = run.stages?.fail(StageId.HOLD, "Discarded")) }) }
        _state.update { it.copy(schedulers = store.all(), message = "Post discarded") }
    }
}

/** The editor's draft, the topic search text and the reason Save was refused, if any. */
data class SchedulerEditorUiState(
    val draft: AutoPostScheduler,
    val query: String = "",
    val error: String? = null,
    val isNew: Boolean = true,
)

/** Add / edit one scheduler (SC-I6). [id] null makes a new one. */
class SchedulerEditorViewModel(
    private val store: SchedulerStore,
    engine: () -> SchedulerEngine,
    id: String?,
) : ViewModel() {
    private val engine by lazy(engine)
    private val existing = id?.let(store::get)
    private val _state = MutableStateFlow(SchedulerEditorUiState(existing ?: AutoPostScheduler(), isNew = existing == null))
    val state: StateFlow<SchedulerEditorUiState> = _state.asStateFlow()

    private fun draft(change: (AutoPostScheduler) -> AutoPostScheduler) =
        _state.update { it.copy(draft = change(it.draft), error = null) }

    private fun schedule(change: (Schedule) -> Schedule) = draft { it.copy(schedule = change(it.schedule)) }

    fun onName(name: String) = draft { it.copy(name = name) }
    fun onQuery(query: String) = _state.update { it.copy(query = query) }

    /** Adds or removes [topic]; topics keep the order they were picked in, which is the order they take turns. */
    fun onToggleTopic(topic: String) = draft {
        it.copy(topics = if (topic in it.topics) it.topics - topic else it.topics + topic, nextTopic = 0)
    }

    fun onPostType(type: PostType) = draft { it.copy(postType = type) }
    fun onImageTemplate(id: String?) = draft { it.copy(imageTemplate = id) }
    fun onImageFormat(format: ImageSize?) = draft { it.copy(imageFormat = format) }
    fun onFrequency(frequency: Frequency) = schedule { it.copy(frequency = frequency) }
    fun onTime(time: LocalTime) = schedule { it.copy(time = time) }
    fun onEveryHours(hours: Int) = schedule { it.copy(everyHours = hours) }
    fun onWindowStart(time: LocalTime) = schedule { it.copy(windowStart = time) }
    fun onWindowEnd(time: LocalTime) = schedule { it.copy(windowEnd = time) }
    fun onToggleDay(day: DayOfWeek) = schedule { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
    fun onDayOfMonth(day: Int) = schedule { it.copy(dayOfMonth = day) }
    fun onWeekdaysOnly(on: Boolean) = schedule { it.copy(weekdaysOnly = on) }
    fun onEnabled(on: Boolean) = draft { it.copy(enabled = on) }
    fun onReviewFirst(on: Boolean) = draft { it.copy(reviewFirst = on) }

    /** Saves and plans the next run; false, with the reason shown, when the settings can't run. */
    fun save(): Boolean {
        val draft = _state.value.draft
        draft.problem()?.let { problem ->
            _state.update { it.copy(error = problem) }
            return false
        }
        // A run may have finished while the editor was open: keep what it recorded.
        val latest = store.get(draft.id)
        engine.save(
            draft.copy(
                name = draft.name.trim(),
                lastRun = latest?.lastRun ?: draft.lastRun,
                recentTitles = latest?.recentTitles ?: draft.recentTitles,
                pending = latest?.pending,
            ),
        )
        return true
    }
}

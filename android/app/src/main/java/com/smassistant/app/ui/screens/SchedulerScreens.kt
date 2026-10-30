package com.smassistant.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.smassistant.app.generate.appSmartTrim
import com.smassistant.app.linkedin.PostState
import com.smassistant.app.linkedin.PostViewModel
import com.smassistant.app.linkedin.appPostToLinkedIn
import com.smassistant.app.scheduler.PendingPost
import com.smassistant.app.ui.components.PostDialogs
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smassistant.app.scheduler.AutoPostScheduler
import com.smassistant.app.scheduler.Frequency
import com.smassistant.app.scheduler.PostType
import com.smassistant.app.scheduler.Schedule
import com.smassistant.app.scheduler.SchedulerEditorUiState
import com.smassistant.app.scheduler.SchedulerEditorViewModel
import com.smassistant.app.scheduler.SchedulerEngine
import com.smassistant.app.scheduler.SchedulerStore
import com.smassistant.app.scheduler.SchedulersUiState
import com.smassistant.app.scheduler.SchedulersViewModel
import com.smassistant.app.scheduler.TechTopics
import com.smassistant.app.scheduler.formatRunTime
import com.smassistant.app.ui.components.PrimaryButton
import com.smassistant.app.ui.components.SelectDropdown
import com.smassistant.app.ui.components.SignInWarningCard
import com.smassistant.app.ui.components.SmScreen
import com.smassistant.app.ui.components.rememberSignInWarning
import java.time.DayOfWeek
import java.time.LocalTime

/** Scheduled Posts (docs/requirements/13-scheduled-posts.md, SC-I5): the user's schedulers. */
@Composable
fun SchedulersScreen(onAdd: () -> Unit, onEdit: (String) -> Unit, onSignIn: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel { SchedulersViewModel(SchedulerStore(appContext)) { SchedulerEngine(appContext) } }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Approving a post waiting for review uses the same confirm dialog as Post in LinkedIn.
    val postViewModel = viewModel { PostViewModel(appPostToLinkedIn(appContext), appSmartTrim(appContext)) }
    val postState by postViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(postState) {
        (postState as? PostState.Posted)?.let { viewModel.reviewPosted(it.post.url) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }

    // Android 13+ asks before an app may notify (SC-7); without it runs still happen, silently.
    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = it }

    SchedulersContent(
        state = state,
        signInWarning = rememberSignInWarning(),
        onSignIn = onSignIn,
        notificationsAllowed = notificationsAllowed,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onAdd = onAdd,
        onEdit = onEdit,
        onEnabledChange = viewModel::setEnabled,
        onRunNow = viewModel::runNow,
        onDelete = viewModel::delete,
        onReview = { id -> viewModel.review(id)?.let { (text, files) -> postViewModel.review(text, files) } },
        onDiscard = viewModel::discard,
        onBackToHome = onBackToHome,
    )
    PostDialogs(
        state = postState,
        onTextChange = postViewModel::onTextChange,
        onConfirm = postViewModel::confirm,
        onDismiss = postViewModel::dismiss,
        onOpenSettings = onSignIn,
        onTrimWithAi = postViewModel::trimWithAi,
        onCutToFit = postViewModel::cutToFit,
    )
}

@Composable
fun SchedulersContent(
    state: SchedulersUiState,
    signInWarning: String? = null,
    onSignIn: () -> Unit = {},
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onRunNow: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBackToHome: () -> Unit,
    onReview: (String) -> Unit = {},
    onDiscard: (String) -> Unit = {},
) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    SmScreen(sectionTitle = "Scheduled Posts", onBackToHome = onBackToHome) {
        Text(
            "Each scheduler picks its next topic, prepares a prompt, writes the post and publishes it to LinkedIn on its schedule, " +
                "with your default hashtags.",
            style = MaterialTheme.typography.bodyMedium,
        )
        signInWarning?.let { SignInWarningCard(it, onSignIn) }
        if (state.schedulers.isNotEmpty() && !notificationsAllowed) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow notifications to hear how each post went.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onAllowNotifications) { Text("Allow") }
                }
            }
        }
        if (state.schedulers.isEmpty()) {
            Text("No schedulers yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("noSchedulers"))
        }
        state.schedulers.forEach { scheduler ->
            SchedulerCard(
                scheduler = scheduler,
                onEnabledChange = { onEnabledChange(scheduler.id, it) },
                onEdit = { onEdit(scheduler.id) },
                onRunNow = { onRunNow(scheduler.id) },
                onDelete = { deleting = scheduler.id },
                onReview = { onReview(scheduler.id) },
                onDiscard = { onDiscard(scheduler.id) },
            )
        }
        PrimaryButton("Add scheduler", onAdd, Modifier.testTag("addScheduler"))
    }

    state.schedulers.firstOrNull { it.id == deleting }?.let { scheduler ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete scheduler?") },
            text = { Text("“${scheduler.displayName}” will stop posting. Posts already on LinkedIn stay there.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(scheduler.id)
                }, modifier = Modifier.testTag("confirmDelete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SchedulerCard(
    scheduler: AutoPostScheduler,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    onReview: () -> Unit,
    onDiscard: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().testTag("scheduler-${scheduler.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(scheduler.displayName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = scheduler.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("schedulerSwitch-${scheduler.id}"),
                )
            }
            Text("${scheduler.schedule.describe()} · ${scheduler.postType.label}", style = MaterialTheme.typography.bodyMedium)
            Text(topicSummary(scheduler.topics), style = MaterialTheme.typography.bodySmall)
            val next = scheduler.nextRunAt
            Text(
                when {
                    !scheduler.enabled -> "Off"
                    next != null -> "Next post: ${formatRunTime(next)}"
                    else -> scheduler.problem() ?: "Not planned"
                },
                style = MaterialTheme.typography.bodySmall,
            )
            scheduler.lastRun?.let { run ->
                Text(
                    (if (run.ok) "✓ " else "✗ ") + formatRunTime(run.at) + ": " + run.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (run.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
            scheduler.pending?.let { post -> PendingPostSection(scheduler.id, post, onReview, onDiscard) }
            Row {
                scheduler.lastRun?.postUrl?.let { url -> TextButton(onClick = { uriHandler.openUri(url) }) { Text("Open post") } }
                TextButton(onClick = onRunNow) { Text("Run now") }
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** A post made by a "review before posting" run: the start of its text, Review (opens the confirm dialog) and Discard. */
@Composable
private fun PendingPostSection(id: String, post: PendingPost, onReview: () -> Unit, onDiscard: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("pending-$id")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Waiting for your review", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(post.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(post.text, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (post.media.isNotEmpty()) {
                Text(
                    if (post.media.size == 1) "1 media file attached" else "${post.media.size} media files attached",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row {
                TextButton(onClick = onReview, modifier = Modifier.testTag("review-$id")) { Text("Review & post") }
                TextButton(onClick = onDiscard, modifier = Modifier.testTag("discard-$id")) { Text("Discard") }
            }
        }
    }
}

/** "Kafka, Java, Spring +4 more". */
internal fun topicSummary(topics: List<String>): String = when {
    topics.isEmpty() -> "No topics"
    topics.size <= 3 -> topics.joinToString(", ")
    else -> topics.take(3).joinToString(", ") + " +${topics.size - 3} more"
}

/** Add or edit one scheduler (SC-I6). [id] null adds a new one; [onSaved] goes back to the list. */
@Composable
fun SchedulerEditorScreen(id: String?, onSaved: () -> Unit, onBackToHome: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel(key = id ?: "new") { SchedulerEditorViewModel(SchedulerStore(appContext), { SchedulerEngine(appContext) }, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SchedulerEditorContent(
        state = state,
        actions = SchedulerEditorActions(
            onName = viewModel::onName,
            onEnabled = viewModel::onEnabled,
            onReviewFirst = viewModel::onReviewFirst,
            onQuery = viewModel::onQuery,
            onToggleTopic = viewModel::onToggleTopic,
            onPostType = viewModel::onPostType,
            onFrequency = viewModel::onFrequency,
            onTime = viewModel::onTime,
            onEveryHours = viewModel::onEveryHours,
            onWindowStart = viewModel::onWindowStart,
            onWindowEnd = viewModel::onWindowEnd,
            onToggleDay = viewModel::onToggleDay,
            onDayOfMonth = viewModel::onDayOfMonth,
            onWeekdaysOnly = viewModel::onWeekdaysOnly,
            onSave = { if (viewModel.save()) onSaved() },
        ),
        onBackToHome = onBackToHome,
    )
}

/** Everything the editor can change, so the content can be tested without a view model. */
data class SchedulerEditorActions(
    val onName: (String) -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onReviewFirst: (Boolean) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onToggleTopic: (String) -> Unit = {},
    val onPostType: (PostType) -> Unit = {},
    val onFrequency: (Frequency) -> Unit = {},
    val onTime: (LocalTime) -> Unit = {},
    val onEveryHours: (Int) -> Unit = {},
    val onWindowStart: (LocalTime) -> Unit = {},
    val onWindowEnd: (LocalTime) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onDayOfMonth: (Int) -> Unit = {},
    val onWeekdaysOnly: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulerEditorContent(state: SchedulerEditorUiState, actions: SchedulerEditorActions, onBackToHome: () -> Unit) {
    val draft = state.draft
    val schedule = draft.schedule
    SmScreen(sectionTitle = if (state.isNew) "New Scheduler" else "Edit Scheduler", onBackToHome = onBackToHome) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onName,
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("schedulerName"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On", modifier = Modifier.weight(1f))
            Switch(checked = draft.enabled, onCheckedChange = actions.onEnabled, modifier = Modifier.testTag("schedulerEnabled"))
        }

        SelectDropdown("Post type", draft.postType, PostType.entries, { it.label }, actions.onPostType, "postType")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Review before posting")
                Text(
                    "Each run makes the post and notifies you; it goes to LinkedIn only after you approve it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = draft.reviewFirst, onCheckedChange = actions.onReviewFirst, modifier = Modifier.testTag("reviewFirst"))
        }

        Text("Schedule", style = MaterialTheme.typography.titleSmall)
        SelectDropdown("Repeat", schedule.frequency, Frequency.entries, { it.label }, actions.onFrequency, "frequency")
        when (schedule.frequency) {
            Frequency.HOURLY -> {
                SelectDropdown("Every", schedule.everyHours, Schedule.HOUR_STEPS, { if (it == 1) "1 hour" else "$it hours" }, actions.onEveryHours, "everyHours")
                TimeDropdowns("From", schedule.windowStart, actions.onWindowStart, "windowStart")
                TimeDropdowns("Until", schedule.windowEnd, actions.onWindowEnd, "windowEnd")
                WeekdaysOnly(schedule.weekdaysOnly, actions.onWeekdaysOnly)
            }
            Frequency.DAILY -> {
                TimeDropdowns("At", schedule.time, actions.onTime, "time")
                WeekdaysOnly(schedule.weekdaysOnly, actions.onWeekdaysOnly)
            }
            Frequency.WEEKLY -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in schedule.days,
                            onClick = { actions.onToggleDay(day) },
                            label = { Text(Schedule.shortName(day)) },
                            modifier = Modifier.testTag("day-${day.name}"),
                        )
                    }
                }
                TimeDropdowns("At", schedule.time, actions.onTime, "time")
            }
            Frequency.MONTHLY -> {
                SelectDropdown(
                    "On the",
                    schedule.dayOfMonth,
                    Schedule.MONTH_DAYS,
                    { if (it == Schedule.LAST_DAY) "Last day" else Schedule.ordinal(it) },
                    actions.onDayOfMonth,
                    "dayOfMonth",
                )
                TimeDropdowns("At", schedule.time, actions.onTime, "time")
            }
        }
        Text(
            "Android picks the exact moment: when the phone is idle or in battery saver a post can go out a few minutes late, " +
                "and a run missed while the phone was off happens once it's back online.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Technical content types", style = MaterialTheme.typography.titleSmall)
        Text(
            if (draft.topics.isEmpty()) "Choose one or more. Each post uses the next one in turn." else "${draft.topics.size} selected, used in turn",
            style = MaterialTheme.typography.bodySmall,
        )
        if (draft.topics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.topics.forEach { topic ->
                    FilterChip(selected = true, onClick = { actions.onToggleTopic(topic) }, label = { Text("$topic ✕") })
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            placeholder = { Text("Search ${TechTopics.all.size} topics, e.g. Kafka") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("topicSearch"),
        )
        val groups = TechTopics.search(state.query)
        // Its own scroll, so the Save button isn't 200 chips away.
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .height(280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
                    .testTag("topicList"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (groups.isEmpty()) Text("No topics match “${state.query.trim()}”.", style = MaterialTheme.typography.bodySmall)
                groups.forEach { group ->
                    Text(group.name, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.topics.forEach { topic ->
                            FilterChip(
                                selected = topic in draft.topics,
                                onClick = { actions.onToggleTopic(topic) },
                                label = { Text(topic) },
                                modifier = Modifier.testTag("topic-$topic"),
                            )
                        }
                    }
                }
            }
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("schedulerError")) }
        PrimaryButton("Save", actions.onSave, Modifier.testTag("saveScheduler"))
    }
}

@Composable
private fun WeekdaysOnly(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Weekdays only", modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("weekdaysOnly"))
    }
}

/** "[label] [09 ▾] : [00 ▾]": hour and quarter-hour pickers. */
@Composable
private fun TimeDropdowns(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, testTag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectDropdown(label, time.hour, (0..23).toList(), ::twoDigits, { onChange(time.withHour(it)) }, "$testTag-hour")
        SelectDropdown(":", time.minute, (MINUTES + time.minute).distinct().sorted(), ::twoDigits, { onChange(time.withMinute(it)) }, "$testTag-minute")
    }
}

private val MINUTES = listOf(0, 15, 30, 45)

private fun twoDigits(n: Int) = n.toString().padStart(2, '0')

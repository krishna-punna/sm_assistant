package com.smassistant.app.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.smassistant.app.scheduler.AutoPostScheduler
import com.smassistant.app.scheduler.Frequency
import com.smassistant.app.scheduler.PostType
import com.smassistant.app.scheduler.RunRecord
import com.smassistant.app.scheduler.Schedule
import com.smassistant.app.scheduler.SchedulerEditorUiState
import com.smassistant.app.scheduler.SchedulersUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SC-T6: the Scheduled Posts list and the scheduler editor. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SchedulerScreensTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val kafka = AutoPostScheduler(
        id = "k",
        name = "Kafka mornings",
        topics = listOf("Kafka", "Kafka Streams", "Spring Kafka", "Schema Registry"),
        postType = PostType.TEXT_WITH_IMAGE,
        schedule = Schedule(Frequency.WEEKLY),
        lastRun = RunRecord(0, ok = false, message = "LinkedIn token expired or invalid."),
    )
    private val java = AutoPostScheduler(id = "j", topics = listOf("Java"), enabled = false)

    private fun showList(state: SchedulersUiState, notificationsAllowed: Boolean = true) = compose.setContent {
        SchedulersContent(
            state = state,
            notificationsAllowed = notificationsAllowed,
            onAllowNotifications = { calls += "allow" },
            onAdd = { calls += "add" },
            onEdit = { calls += "edit:$it" },
            onEnabledChange = { id, on -> calls += "enabled:$id:$on" },
            onRunNow = { calls += "run:$it" },
            onDelete = { calls += "delete:$it" },
            onBackToHome = { calls += "home" },
        )
    }

    @Test
    fun emptyListOffersAdd() {
        showList(SchedulersUiState())
        compose.onNodeWithTag("noSchedulers").assertExists()
        compose.onNodeWithText("Allow").assertDoesNotExist()
        compose.onNodeWithTag("addScheduler").performClick()
        assertEquals(listOf("add"), calls)
    }

    @Test
    fun listShowsEachSchedulerWithItsSwitchAndActions() {
        showList(SchedulersUiState(listOf(kafka, java)), notificationsAllowed = false)
        compose.onNodeWithText("Kafka mornings").assertExists()
        compose.onNodeWithText("Tue, Thu at 09:00 · Text & Image").assertExists()
        compose.onNodeWithText("Kafka, Kafka Streams, Spring Kafka +1 more").assertExists()
        compose.onNodeWithText("LinkedIn token expired or invalid.", substring = true).assertExists()
        compose.onNodeWithTag("schedulerSwitch-k").assertIsOn()
        compose.onNodeWithTag("schedulerSwitch-j").assertIsOff()
        compose.onNodeWithText("Off").assertExists()

        compose.onNodeWithTag("schedulerSwitch-j").performClick()
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf("enabled:j:true", "allow"), calls)
    }

    @Test
    fun deleteAsksFirst() {
        showList(SchedulersUiState(listOf(java)))
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete scheduler?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete scheduler?").assertExists()
        compose.onNodeWithTag("confirmDelete").performClick()
        assertEquals(listOf("delete:j"), calls)
    }

    private fun showEditor(state: SchedulerEditorUiState) = compose.setContent {
        SchedulerEditorContent(
            state = state,
            actions = SchedulerEditorActions(
                onToggleTopic = { calls += "topic:$it" },
                onToggleDay = { calls += "day:$it" },
                onSave = { calls += "save" },
            ),
            onBackToHome = { calls += "home" },
        )
    }

    @Test
    fun editorSearchesTopicsAndTogglesThem() {
        showEditor(SchedulerEditorUiState(AutoPostScheduler(topics = listOf("Kafka")), query = "spring sec"))
        compose.onNodeWithText("New Scheduler").assertExists()
        compose.onNodeWithText("1 selected, used in turn").assertExists()
        compose.onNodeWithTag("topic-Spring Security").assertExists()
        compose.onNodeWithTag("topic-Angular").assertDoesNotExist()
        // The topic list scrolls inside the screen's scroll, so its chips are clicked by their semantics action, not a touch.
        compose.onNodeWithTag("topic-Spring Security").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("topic:Spring Security"), calls)
        compose.onNodeWithText("Kafka ✕").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("topic:Spring Security", "topic:Kafka"), calls)
    }

    @Test
    fun editorShowsTheOptionsForTheFrequency() {
        showEditor(SchedulerEditorUiState(AutoPostScheduler(schedule = Schedule(Frequency.WEEKLY)), isNew = false))
        compose.onNodeWithText("Edit Scheduler").assertExists()
        compose.onNodeWithTag("day-MONDAY").performClick()
        compose.onNodeWithTag("weekdaysOnly").assertDoesNotExist()
        assertEquals(listOf("day:MONDAY"), calls)
    }

    @Test
    fun editorShowsWhySaveWasRefused() {
        showEditor(SchedulerEditorUiState(AutoPostScheduler(schedule = Schedule(Frequency.HOURLY)), error = "Choose at least one technical content type."))
        compose.onNodeWithTag("everyHours").assertExists()
        compose.onNodeWithTag("weekdaysOnly").assertExists()
        compose.onNodeWithTag("schedulerError").performScrollTo().assertExists()
        compose.onNodeWithTag("saveScheduler").performScrollTo().performClick()
        assertEquals(listOf("save"), calls)
    }
}

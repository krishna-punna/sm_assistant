package com.smassistant.app.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SchedulerViewModelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store get() = SchedulerStore(context)

    @Before
    fun setUp() {
        context.getSharedPreferences("schedulers", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun editor(id: String? = null) = SchedulerEditorViewModel(store, { SchedulerEngine(context) }, id)

    @Test
    fun `a new scheduler needs a topic before it saves`() {
        val vm = editor()
        assertTrue(vm.state.value.isNew)
        assertFalse(vm.save())
        assertEquals("Choose at least one technical content type.", vm.state.value.error)

        vm.onToggleTopic("Kafka")
        vm.onToggleTopic("Java")
        vm.onToggleTopic("Kafka")
        vm.onName("  Java posts  ")
        vm.onFrequency(Frequency.WEEKLY)
        vm.onToggleDay(DayOfWeek.MONDAY)
        assertEquals(null, vm.state.value.error)
        assertTrue(vm.save())

        val saved = store.all().single()
        assertEquals(listOf("Java"), saved.topics)
        assertEquals("Java posts", saved.name)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), saved.schedule.days)
        assertTrue(saved.nextRunAt != null)
    }

    @Test
    fun `editing loads the saved scheduler`() {
        store.save(AutoPostScheduler(id = "a", name = "Angular", topics = listOf("Angular")))
        val vm = editor("a")
        assertFalse(vm.state.value.isNew)
        assertEquals("Angular", vm.state.value.draft.name)
    }

    @Test
    fun `list switches, deletes and refreshes`() {
        store.save(AutoPostScheduler(id = "a", topics = listOf("Angular")))
        val vm = SchedulersViewModel(store) { SchedulerEngine(context) }
        assertEquals(1, vm.state.value.schedulers.size)

        vm.setEnabled("a", false)
        assertFalse(vm.state.value.schedulers.single().enabled)

        store.save(AutoPostScheduler(id = "b", topics = listOf("Java")))
        vm.refresh()
        assertEquals(2, vm.state.value.schedulers.size)

        vm.runNow("b")
        assertTrue(vm.state.value.message!!.startsWith("Running now"))
        vm.delete("a")
        assertEquals(listOf("b"), vm.state.value.schedulers.map { it.id })
    }

    @Test
    fun `a post waiting for review opens for review, then records the post`() {
        val image = java.io.File(context.cacheDir, "review.png").apply { writeBytes(byteArrayOf(1)) }
        store.save(AutoPostScheduler(id = "a", topics = listOf("Kafka"), reviewFirst = true, pending = PendingPost("Kafka", "Text #java", listOf(image.path), template = "workflow")))
        val vm = SchedulersViewModel(store, now = { 42L }) { SchedulerEngine(context) }

        assertEquals("Text #java" to listOf(image), vm.review("a"))
        vm.reviewPosted("https://www.linkedin.com/feed/update/urn:li:share:9")
        val saved = store.get("a")!!
        assertEquals(null, saved.pending)
        assertEquals(RunRecord(42L, true, "Posted “Kafka”", "https://www.linkedin.com/feed/update/urn:li:share:9", template = "workflow"), saved.lastRun)
        assertEquals(null, vm.state.value.schedulers.single().pending)
    }

    @Test
    fun `a waiting post whose media is gone can't be reviewed, only discarded`() {
        store.save(AutoPostScheduler(id = "a", topics = listOf("Kafka"), pending = PendingPost("Kafka", "Text", listOf("/nowhere/gone.png"))))
        val vm = SchedulersViewModel(store) { SchedulerEngine(context) }
        assertEquals(null, vm.review("a"))
        assertTrue(vm.state.value.message!!.contains("no longer on the phone"))

        vm.discard("a")
        assertEquals(null, store.get("a")!!.pending)
        assertEquals("Post discarded", vm.state.value.message)
    }

    @Test
    fun `saving the editor keeps a post that arrived while it was open`() {
        store.save(AutoPostScheduler(id = "a", topics = listOf("Kafka")))
        val vm = editor("a")
        store.update("a") { it.copy(pending = PendingPost("Kafka", "Text")) }
        vm.onReviewFirst(true)
        assertTrue(vm.save())
        val saved = store.get("a")!!
        assertTrue(saved.reviewFirst)
        assertEquals(PendingPost("Kafka", "Text"), saved.pending)
    }
}

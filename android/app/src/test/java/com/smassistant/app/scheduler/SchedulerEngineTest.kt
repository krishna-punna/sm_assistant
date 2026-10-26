package com.smassistant.app.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SchedulerEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 23, 17, 30, 0, 0, zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        context.getSharedPreferences("schedulers", Context.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun engine() = SchedulerEngine(context, now = { now }, zone = { zone })
    private fun work(id: String): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SchedulerEngine.workName(id)).get()
    private val daily = AutoPostScheduler(id = "a", topics = listOf("Java"), schedule = Schedule(Frequency.DAILY, time = LocalTime.of(18, 0)))

    @Test
    fun `planned gives the next run, or none when off or without topics`() {
        val next = ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(next, SchedulerEngine.planned(daily, now, zone).nextRunAt)
        assertNull(SchedulerEngine.planned(daily.copy(enabled = false, nextRunAt = 5), now, zone).nextRunAt)
        assertNull(SchedulerEngine.planned(daily.copy(topics = emptyList()), now, zone).nextRunAt)
    }

    @Test
    fun `saving plans one run, switching off cancels it, deleting removes the scheduler`() {
        val engine = engine()
        val saved = engine.save(daily)
        assertEquals(saved, SchedulerStore(context).get("a"))
        assertEquals(WorkInfo.State.ENQUEUED, work("a").single().state)

        engine.save(saved)
        assertEquals(1, work("a").count { it.state == WorkInfo.State.ENQUEUED })

        engine.setEnabled("a", false)
        assertNull(SchedulerStore(context).get("a")?.nextRunAt)
        assertTrue(work("a").none { it.state == WorkInfo.State.ENQUEUED })

        engine.delete("a")
        assertNull(SchedulerStore(context).get("a"))
    }

    @Test
    fun `app start plans schedulers that are on but have no run`() {
        SchedulerStore(context).save(daily)
        engine().ensurePlanned()
        assertTrue(SchedulerStore(context).get("a")?.nextRunAt != null)
        assertEquals(WorkInfo.State.ENQUEUED, work("a").single().state)
    }
}

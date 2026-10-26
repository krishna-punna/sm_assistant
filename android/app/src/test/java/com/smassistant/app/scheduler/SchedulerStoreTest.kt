package com.smassistant.app.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SchedulerStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("schedulers", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `saves several schedulers, replaces by id and deletes`() {
        val store = SchedulerStore(context)
        val kafka = AutoPostScheduler(id = "a", topics = listOf("Kafka"))
        val angular = AutoPostScheduler(id = "b", topics = listOf("Angular"), postType = PostType.TEXT_WITH_IMAGE)
        store.save(kafka)
        store.save(angular)
        store.save(kafka.copy(enabled = false))

        assertEquals(listOf(kafka.copy(enabled = false), angular), SchedulerStore(context).all())
        store.delete("a")
        assertEquals(listOf(angular), store.all())
    }

    @Test
    fun `update changes one scheduler and skips a deleted one`() {
        val store = SchedulerStore(context)
        store.save(AutoPostScheduler(id = "a", topics = listOf("Java", "Spring")))
        val updated = store.update("a") { it.advanced("Java Records") }
        assertEquals("Spring", updated?.currentTopic())
        assertEquals(listOf("Java Records"), store.get("a")?.recentTitles)
        assertNull(store.update("gone") { it })
    }
}

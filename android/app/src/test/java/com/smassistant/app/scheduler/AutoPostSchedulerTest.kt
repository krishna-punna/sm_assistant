package com.smassistant.app.scheduler

import com.smassistant.app.data.ImageFormat
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPostSchedulerTest {
    @Test
    fun `topics take turns and recent titles are kept newest first`() {
        var s = AutoPostScheduler(topics = listOf("Kafka", "Java", "Angular"))
        val seen = mutableListOf<String>()
        repeat(4) {
            seen += s.currentTopic()!!
            s = s.advanced("Post $it")
        }
        assertEquals(listOf("Kafka", "Java", "Angular", "Kafka"), seen)
        assertEquals(listOf("Post 3", "Post 2", "Post 1", "Post 0"), s.recentTitles)
    }

    @Test
    fun `only the latest titles are remembered and a failed run keeps them`() {
        var s = AutoPostScheduler(topics = listOf("Java"))
        repeat(15) { s = s.advanced("T$it") }
        assertEquals(AutoPostScheduler.MAX_RECENT_TITLES, s.recentTitles.size)
        assertEquals("T14", s.recentTitles.first())
        assertEquals(s.recentTitles, s.advanced(null).recentTitles)
    }

    @Test
    fun `needs a topic, and a day for weekly`() {
        assertEquals("Choose at least one technical content type.", AutoPostScheduler().problem())
        assertNull(AutoPostScheduler().currentTopic())
        val weekly = AutoPostScheduler(topics = listOf("Java"), schedule = Schedule(Frequency.WEEKLY, days = emptySet()))
        assertEquals("Choose at least one day of the week.", weekly.problem())
        assertNull(weekly.copy(schedule = Schedule(Frequency.WEEKLY)).problem())
    }

    @Test
    fun `display name falls back to the topics`() {
        assertEquals("Kafka, Java", AutoPostScheduler(topics = listOf("Kafka", "Java", "Spring")).displayName)
        assertEquals("Morning Java", AutoPostScheduler(name = "Morning Java").displayName)
    }

    @Test
    fun `json keeps every field`() {
        val s = AutoPostScheduler(
            name = "Weekly Kafka",
            enabled = false,
            topics = listOf("Kafka", "Spring Kafka"),
            postType = PostType.TEXT_WITH_VIDEO,
            schedule = Schedule(Frequency.WEEKLY, LocalTime.of(7, 45), 6, LocalTime.of(8, 0), LocalTime.of(20, 0), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 15, true),
            nextTopic = 1,
            recentTitles = listOf("Kafka Partitions Explained"),
            lastRun = RunRecord(1_700_000_000_000, true, "Posted", "https://www.linkedin.com/feed/update/urn:li:share:1", awaitingReview = true, template = "workflow"),
            reviewFirst = true,
            pending = PendingPost("Kafka Partitions", "Kafka partitions explained\n\n#java", listOf("/cache/generated/a.png"), 1_700_000_000_000, "workflow"),
            nextRunAt = 1_700_000_360_000,
            imageTemplate = "comparison",
            imageFormat = ImageFormat.LANDSCAPE,
        )
        assertEquals(listOf(s, AutoPostScheduler(id = "b")), SchedulerJson.decode(SchedulerJson.encode(listOf(s, AutoPostScheduler(id = "b")))))
    }

    @Test
    fun `bad or old json falls back to defaults`() {
        assertEquals(emptyList<AutoPostScheduler>(), SchedulerJson.decode("not json"))
        assertEquals(emptyList<AutoPostScheduler>(), SchedulerJson.decode(null))
        val loaded = SchedulerJson.decode("""[{"id":"x","topics":["Java"],"postType":"HOLOGRAM","schedule":{"frequency":"DAILY","time":"25:99"}}]""").single()
        assertEquals("x", loaded.id)
        assertEquals(PostType.TEXT, loaded.postType)
        assertEquals(Schedule().time, loaded.schedule.time)
        assertTrue(loaded.enabled)
        // Older saves have no template or format: both Auto.
        assertNull(loaded.imageTemplate)
        assertNull(loaded.imageFormat)
    }
}

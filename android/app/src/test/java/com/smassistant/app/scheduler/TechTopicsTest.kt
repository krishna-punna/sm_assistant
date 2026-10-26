package com.smassistant.app.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechTopicsTest {
    @Test
    fun `200 distinct topics, including the samples`() {
        assertEquals(200, TechTopics.all.size)
        assertEquals(200, TechTopics.all.map { it.lowercase() }.toSet().size)
        listOf("Java", "Spring", "Angular", "Distributed Systems", "Kafka").forEach {
            assertTrue("missing $it", it in TechTopics.all)
        }
    }

    @Test
    fun `search matches every word, case-insensitively, by topic or group`() {
        assertEquals(listOf("Spring Security", "Spring Security OAuth2"), TechTopics.search("spring SEC").flatMap { it.topics })
        assertEquals(TechTopics.groups.first { it.name == "Frontend" }.topics, TechTopics.search("frontend").flatMap { it.topics })
        assertTrue(TechTopics.search("kafka").flatMap { it.topics }.containsAll(listOf("Kafka", "Kafka Streams", "Spring Kafka")))
        assertEquals(TechTopics.groups, TechTopics.search("  "))
        assertEquals(emptyList<TopicGroup>(), TechTopics.search("cobol"))
    }
}

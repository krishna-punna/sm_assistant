package com.smassistant.app.image.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfographicCheckTest {
    private val good = InfographicSpec(
        title = "Kafka Retries",
        hero = "a parcel bouncing between two mailboxes, no text",
        sections = listOf(
            Section("Why retry", SectionBody.Bullets(listOf("Brokers fail", "Networks drop"))),
            Section("Backoff", SectionBody.Steps(listOf("Wait", "Double", "Give up"))),
            Section("Dead letters", SectionBody.Bullets(listOf("Park poison messages"))),
        ),
    )

    @Test
    fun `a complete definition on the topic has no problems`() {
        assertEquals(emptyList<String>(), infographicProblems(good, "Kafka retry strategies"))
    }

    @Test
    fun `finds too few sections, an off-topic title, placeholders and repeated headings`() {
        val bad = good.copy(
            title = "Docker Volumes",
            sections = listOf(
                Section("Why retry", SectionBody.Bullets(listOf("Lorem ipsum dolor"))),
                Section("why retry", SectionBody.Bullets(listOf("TODO"))),
            ),
        )
        val problems = infographicProblems(bad, "Kafka retry strategies")
        assertEquals(4, problems.size)
        assertTrue(problems[0], problems[0].startsWith("it has 2 sections"))
        assertTrue(problems[1], problems[1].contains("doesn't name the topic"))
        assertTrue(problems.any { it.contains("more than one section is headed \"why retry\"") })
        assertTrue(problems.any { it.contains("placeholder text") })
    }

    @Test
    fun `illustrations that ask for text or diagrams are flagged, ones that forbid text are not`() {
        assertTrue(asksForText("a server rack with labels on each shelf"))
        assertTrue(asksForText("an architecture diagram of microservices"))
        assertFalse(asksForText("a rocket over a city skyline, no text or logos"))
        assertFalse(asksForText("a coffee cup on boxes, without any words"))
        val problems = infographicProblems(good.copy(hero = "a whiteboard with the word CACHE written on it"), "Kafka")
        assertTrue(problems.single(), problems.single().contains("garble text"))
    }

    @Test
    fun `the title only needs one meaningful word from the topic`() {
        assertTrue(sharesAWord("Inside Java HashMap", "How does a HashMap work?"))
        assertTrue(sharesAWord("Caching Explained", "caches and CDNs"))
        assertTrue(sharesAWord("The Complete Guide", "x"))
        assertFalse(sharesAWord("Docker Volumes", "Kafka retries"))
    }

    @Test
    fun `blank, transparent, tiny and unreadable images are rejected`() {
        val white = IntArray(100) { 0xFFFFFFFF.toInt() }
        assertEquals("it is blank", imageProblem(imageFacts(1024, 1024, white), 256))
        val clear = IntArray(100) { 0 }
        assertEquals("it is transparent", imageProblem(imageFacts(1024, 1024, clear), 256))
        val picture = IntArray(100) { i -> if (i % 3 == 0) 0xFF102040.toInt() else if (i % 3 == 1) 0xFFF0C030.toInt() else 0xFFFFFFFF.toInt() }
        assertNull(imageProblem(imageFacts(1024, 1536, picture), 1024))
        assertEquals("it is 200x300, smaller than 256 px", imageProblem(imageFacts(200, 300, picture), 256))
        assertEquals("the file isn't an image", imageProblem(null, 256))
    }

    @Test
    fun `a mostly white illustration with a small subject still passes`() {
        val pixels = IntArray(10_000) { i -> if (i % 10 == 0) 0xFF2050A0.toInt() else 0xFFFDFDFD.toInt() }
        assertNull(imageProblem(imageFacts(1024, 1024, pixels), 256))
    }
}

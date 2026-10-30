package com.smassistant.app.infographic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCheckTest {
    private fun spec(vararg bodies: SectionBody) = InfographicSpec("Caching", sections = bodies.mapIndexed { i, body -> Section("Card $i", body) })

    private val bullets = SectionBody.Bullets(listOf("a point"))

    @Test
    fun `templates need their kind of card`() {
        assertTrue(templateProblems(spec(bullets), "comparison", "").single().contains("Comparison"))
        assertEquals(emptyList<String>(), templateProblems(spec(bullets, SectionBody.ProsCons(listOf("fast"), listOf("stale"))), "comparison", ""))
        assertTrue(templateProblems(spec(bullets), "workflow", "").single().contains("Workflow"))
        assertEquals(emptyList<String>(), templateProblems(spec(SectionBody.Steps(listOf("read", "write"))), "workflow", ""))
        assertTrue(templateProblems(spec(bullets), "architecture", "").single().contains("diagram"))
        assertTrue(templateProblems(spec(bullets), "code", "").single().contains("code card"))
        // The universal template and no template add nothing.
        assertEquals(emptyList<String>(), templateProblems(spec(bullets), "universal", "x"))
        assertEquals(emptyList<String>(), templateProblems(spec(bullets), null, "x"))
    }

    @Test
    fun `the data template only shows numbers from the content`() {
        val content = "Redis answers in 0.5 ms and Postgres in 4 ms; S3 takes 1,200 ms."
        val good = spec(SectionBody.Chart(listOf(SectionBody.Chart.Bar("Redis", "0.5 ms", 0.5), SectionBody.Chart.Bar("S3", "1,200 ms", 1200.0))))
        assertEquals(emptyList<String>(), templateProblems(good, "data", content))
        val invented = spec(SectionBody.Stats(listOf(SectionBody.Stats.Stat("99.9%", "hit rate"))))
        assertTrue(templateProblems(invented, "data", content).single().contains("\"99.9%\" isn't in the content"))
        // Without content there is nothing to compare with.
        assertEquals(emptyList<String>(), templateProblems(invented, "data", ""))
    }

    @Test
    fun `the code template only shows code from the content, however it is spaced`() {
        val content = "```java\nList<String> names = new ArrayList<>(100);\nnames.add(\"Ada\");\n```"
        val same = spec(SectionBody.Code("List<String>  names = new ArrayList<>(100);\n// grows when full\n  names.add(\"Ada\");"))
        assertEquals(emptyList<String>(), templateProblems(same, "code", content))
        val made = spec(SectionBody.Code("names.removeIf(String::isEmpty);"))
        assertTrue(templateProblems(made, "code", content).single().contains("isn't in the content"))
    }
}

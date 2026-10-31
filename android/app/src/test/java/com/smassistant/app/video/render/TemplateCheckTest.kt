package com.smassistant.app.video.render

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

    @Test
    fun `storyboard checks run the template across every slide`() {
        val deck = SlideDeck(listOf(spec(bullets), spec(SectionBody.Steps(listOf("read", "write")))))
        // The steps are on the second slide: across the slides the workflow has them.
        assertEquals(emptyList<String>(), storyboardProblems(deck, "workflow", ""))
        assertEquals(listOf("across the slides, the Workflow template needs a steps, flow or diagram card with the steps in order"),
            storyboardProblems(SlideDeck(listOf(spec(bullets), spec(bullets))), "workflow", ""))
    }

    @Test
    fun `storyboard data numbers must come from the content and invented ones are found`() {
        val content = "p99 latency fell from 120 ms to 45 ms."
        val invented = SlideDeck(listOf(spec(bullets), spec(SectionBody.Stats(listOf(SectionBody.Stats.Stat("60%", "faster"))))))
        assertTrue(storyboardProblems(invented, "data", content).single().contains("\"60%\""))
    }

    @Test
    fun `scenes need their own purpose and seconds near the length`() {
        val slides = listOf(spec(bullets), spec(bullets), spec(bullets))
        val repeated = SlideDeck(slides, scenes = listOf(Scene("Hook", 5), Scene("hook ", 5), Scene("Takeaway", 5)))
        assertEquals(
            listOf(
                "more than one slide has the purpose \"hook\"; give each slide its own idea",
                "the slides' seconds add up to 15, but the video is 60 seconds long; make them add up to about 60",
            ),
            storyboardProblems(repeated, null, "", seconds = 60),
        )
        val fine = SlideDeck(slides, scenes = listOf(Scene("Hook", 20), Scene("How", 25), Scene("Takeaway", 10)))
        assertEquals(emptyList<String>(), storyboardProblems(fine, null, "", seconds = 60))
        // Seconds are only checked when every scene gives them.
        assertEquals(emptyList<String>(), storyboardProblems(SlideDeck(slides, scenes = listOf(Scene(seconds = 1), Scene(), Scene())), null, "", 60))
    }
}

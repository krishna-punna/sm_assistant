package com.smassistant.app.infographic

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfographicPipelineTest {
    private val spec = InfographicSpec(
        title = "Caching",
        palette = Palette.TEAL,
        hero = "a server with a lightning bolt.",
        sections = listOf(
            Section("Picture 1", SectionBody.Illustration("a box of cards")),
            Section("Picture 2", SectionBody.Illustration("a clock")),
        ),
    )
    private var composed: Pair<RenderedInfographic, List<File?>>? = null
    private val composer = InfographicComposer { rendered, illustrations -> composed = rendered to illustrations; File("out.png") }
    private val pipeline = InfographicPipeline(InfographicRenderer(), "Draw {{prompt}} in {{colors}}. No text.", composer, maxIllustrations = 2)
    private val prompts = mutableListOf<String>()

    @Test
    fun `paints the first slots with the house style and composes every slot`() = runTest {
        val agent = ImageAgent { prompt, count ->
            assertEquals(1, count)
            synchronized(prompts) { prompts += prompt }
            if (prompt.contains("box")) ApiResult.Failed(ApiError.RateLimited) else ApiResult.Ok(listOf(File("${prompts.size}.png")))
        }

        assertEquals(File("out.png"), pipeline.draw(spec, agent))

        val (rendered, illustrations) = composed!!
        assertEquals(3, rendered.slots.size)
        assertEquals(
            setOf("Draw a server with a lightning bolt in ${Palette.TEAL.colorWords}. No text.", "Draw a box of cards in ${Palette.TEAL.colorWords}. No text."),
            prompts.toSet(),
        )
        // The hero painted, the failed one and the one over the limit keep their placeholders.
        assertTrue(illustrations[0] != null)
        assertEquals(listOf(null, null), illustrations.drop(1))
    }

    @Test
    fun `without an illustrator the definition is drawn alone`() = runTest {
        assertEquals(File("out.png"), pipeline.draw(spec, null))
        assertEquals(listOf(null, null, null), composed!!.second)
    }

    @Test
    fun `an unusable illustration is painted once more, then left as the placeholder`() = runTest {
        val blank = ImageFacts(1024, 1024, contrast = 0f, dominantShare = 1f, opaqueShare = 1f)
        val fine = ImageFacts(1024, 1024, contrast = 40f, dominantShare = 0.5f, opaqueShare = 1f)
        val checked = InfographicPipeline(
            InfographicRenderer(), "{{prompt}}", composer, maxIllustrations = 2,
            inspector = { file -> if (file.name.startsWith("blank") || file.name == "tiny.png") blank else fine },
        )
        val calls = mutableMapOf<String, Int>()
        val agent = ImageAgent { prompt, _ ->
            val n = synchronized(calls) { calls.merge(prompt, 1, Int::plus)!! }
            // The hero is blank the first time only; the box is blank every time.
            val name = if (prompt.contains("box") || n == 1) "blank-$n.png" else "hero-$n.png"
            ApiResult.Ok(listOf(File(name)))
        }

        checked.draw(spec, agent)

        assertEquals(mapOf("a server with a lightning bolt" to 2, "a box of cards" to 2), calls)
        assertEquals(listOf(File("hero-2.png"), null, null), composed!!.second)
    }

    @Test
    fun `a finished image that is blank or too small isn't returned`() = runTest {
        var facts = ImageFacts(2160, 3240, contrast = 0f, dominantShare = 1f, opaqueShare = 1f)
        val checked = InfographicPipeline(InfographicRenderer(), "{{prompt}}", composer, inspector = { facts })
        assertEquals(null, checked.draw(spec, null))
        facts = ImageFacts(800, 1200, contrast = 40f, dominantShare = 0.5f, opaqueShare = 1f)
        assertEquals(null, checked.draw(spec, null))
        facts = facts.copy(width = 2160, height = 3240)
        assertEquals(File("out.png"), checked.draw(spec, null))
    }

    @Test
    fun `illustration assets keep their placeholders and forbid text`() {
        val style = File("src/main/assets/prompts/illustration.md").readText()
        listOf("{{prompt}}", "{{colors}}").forEach { assertTrue(it, style.contains(it)) }
        assertTrue(style.contains("Do not include any text"))
    }
}

package com.smassistant.app.generate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Image and video prompts are apart (docs/requirements/19, SP-5, SP-7, SP-11, SP-14): each
 * pipeline has its own folder with its own content, chooser, illustration, review and examples
 * files, and neither folder names the other medium, so a change to one never changes the other.
 */
class PromptIsolationTest {
    private val prompts = File("src/main/assets/prompts")
    private fun names(folder: String) = File(prompts, folder).list()!!.sorted()

    @Test
    fun `each pipeline has its own prompt files`() {
        assertEquals(
            listOf(
                "carousel.md", "choose_image_template.md", "condense_image_prompt.md", "illustration.md", "image.md",
                "image_content.md", "image_examples.md", "image_review.md", "image_templates.md", "infographic.md",
            ),
            names("image"),
        )
        assertEquals(
            listOf(
                "choose_video_template.md", "illustration.md", "video.md", "video_content.md", "video_deck.md",
                "video_examples.md", "video_review.md", "video_templates.md",
            ),
            names("video"),
        )
    }

    @Test
    fun `only the text post prompts are shared at the top`() {
        assertEquals(
            listOf("image", "prepare_content_prompt.md", "prepare_prompt.md", "technical_content.md", "video"),
            prompts.list()!!.sorted(),
        )
    }

    @Test
    fun `image prompts never mention video and video prompts never mention images`() {
        names("image").forEach { name ->
            assertTrue(name, !File(prompts, "image/$name").readText().contains("video", ignoreCase = true))
        }
        names("video").forEach { name ->
            assertTrue(name, !Regex("""\bimages?\b|image_|infographic\.md""", RegexOption.IGNORE_CASE).containsMatchIn(File(prompts, "video/$name").readText()))
        }
    }

    @Test
    fun `the video examples are slide decks for the video templates`() {
        val examples = parseImageExamples(File(prompts, "video/video_examples.md").readText())
        val ids = parseImageTemplates(File(prompts, "video/video_templates.md").readText()).map { it.id }.toSet()
        assertEquals(6, examples.size)
        assertTrue(examples.all { it.template in ids && it.imagePrompt.startsWith("1 ") })
    }
}

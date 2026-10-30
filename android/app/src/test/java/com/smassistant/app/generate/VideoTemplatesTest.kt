package com.smassistant.app.generate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTemplatesTest {
    private val asset by lazy { File("src/main/assets/prompts/video_templates.md").readText() }
    private val templates by lazy { parseImageTemplates(asset) }
    private val imageIds by lazy {
        parseImageTemplates(File("src/main/assets/prompts/image_templates.md").readText()).map { it.id }.toSet()
    }

    @Test
    fun `the asset has the ten templates with the image template ids`() {
        assertEquals(
            listOf("universal", "concept", "architecture", "workflow", "comparison", "code", "checklist", "data", "learning", "series"),
            templates.map { it.id },
        )
        assertEquals(imageIds, templates.map { it.id }.toSet())
        assertTrue(templates.all { it.name.isNotBlank() && it.useWhen.isNotBlank() })
        assertTrue(templates.all { "{{content}}" in it.body && "{{format}}" in it.body && "{{duration}}" in it.body })
    }

    @Test
    fun `filled templates have no slots or source placeholders left`() {
        for (template in templates) {
            val filled = template.fill("content", "vertical 9:16", "developers", "navy", "Acme", duration = "45 seconds")
            assertFalse(template.id, filled.contains("{{"))
            assertFalse(template.id, filled.contains("[PASTE") || filled.contains("[OPTIONAL") || filled.contains("[DURATION"))
            assertTrue(template.id, filled.contains("45 seconds"))
        }
    }

    @Test
    fun `a duration left empty reads not specified and is never added to image templates`() {
        val video = ImageTemplate("t", "T", "", "Length: {{duration}}")
        assertEquals("Length: $NOT_SPECIFIED", video.fill("c", "square 1:1"))
        val image = ImageTemplate("t", "T", "", "Content: {{content}}")
        assertEquals("Content: c", image.fill("c", "square 1:1"))
    }

    @Test
    fun `only the video sizes are mentioned`() {
        for (size in listOf("2:3", "3:2", "4:5")) assertFalse(size, asset.contains(size))
    }

    @Test
    fun `there is no sound and no video model`() {
        assertFalse(asset.contains("voice-over", ignoreCase = true))
        assertFalse(asset.contains("subtitles", ignoreCase = true))
        assertFalse(asset.contains("post-production", ignoreCase = true))
    }
}

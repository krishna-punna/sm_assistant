package com.smassistant.app.video

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTemplatesTest {
    private val asset by lazy { File("src/main/assets/prompts/video/video_templates.md").readText() }
    private val templates by lazy { parseVideoTemplates(asset) }
    private val imageIds by lazy {
        parseVideoTemplates(File("src/main/assets/prompts/image/image_templates.md").readText()).map { it.id }.toSet()
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
        val video = VideoTemplate("t", "T", "", "Length: {{duration}}")
        assertEquals("Length: $NOT_SPECIFIED", video.fill("c", "square 1:1"))
        val image = VideoTemplate("t", "T", "", "Content: {{content}}")
        assertEquals("Content: c", image.fill("c", "square 1:1"))
    }

    @Test
    fun `diagram, comparison and data videos are landscape 16-9, the rest vertical 9-16`() {
        val formats = templates.associate { it.id to videoSizeOf(it) }
        assertEquals(setOf("architecture", "comparison", "data"), formats.filterValues { it == VideoSize.LANDSCAPE }.keys)
        assertTrue(formats.filterKeys { it !in setOf("architecture", "comparison", "data") }.values.all { it == VideoSize.VERTICAL })
        assertEquals(listOf(1080 to 1920, 1080 to 1080, 1920 to 1080), VideoSize.entries.map { it.width to it.height })
        assertEquals(listOf("9:16", "1:1", "16:9"), VideoSize.entries.map { it.ratio })
    }

    @Test
    fun `a video's file name carries its template and size`() {
        val labelled = labelVideo(File("cache/abc.mp4"), "workflow", VideoSize.VERTICAL)
        assertEquals(File("cache/vtpl-workflow-vertical-abc.mp4"), labelled)
        val label = videoLabel(labelled)!!
        assertEquals(VideoLabel("workflow", VideoSize.VERTICAL), label)
        assertEquals("Workflow or Lifecycle · 9:16", label.text(templates))
        assertEquals("Mystery · 16:9", VideoLabel("mystery", VideoSize.LANDSCAPE).text(templates))
        assertEquals(null, videoLabel(File("abc.mp4")))
        // An image's label isn't a video's.
        assertEquals(null, videoLabel(File("tpl-workflow-portrait-a.png")))
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

package com.smassistant.app.image

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTemplatesTest {
    private val asset by lazy { File("src/main/assets/prompts/image/image_templates.md").readText() }
    private val templates by lazy { parseImageTemplates(asset) }

    @Test
    fun `parses blocks after the intro and skips incomplete ones`() {
        val text = """
            Intro with Id: not a template
            ---
            Id: Workflow
            Name: Workflow
            Use when: steps: in order
            Template:
            Steps for {{content}}.

            Aspect ratio: {{format}}.
            ---
            Id: broken
            Name: No template line
        """.trimIndent()
        assertEquals(
            listOf(ImageTemplate("workflow", "Workflow", "steps: in order", "Steps for {{content}}.\n\nAspect ratio: {{format}}.")),
            parseImageTemplates(text),
        )
    }

    @Test
    fun `fills slots, marks empty optional ones and adds inputs a template has no slot for`() {
        val template = ImageTemplate("t", "T", "", "Content: {{content}}\nStyle: {{style}}\nFormat: {{format}}")
        assertEquals(
            "Content: Kafka\nStyle: not specified\nFormat: portrait 2:3",
            template.fill(" Kafka ", "portrait 2:3"),
        )
        assertEquals(
            "Content: Kafka\nStyle: navy\nFormat: square 1:1\n\nTarget audience: architects\nBrand name: Acme",
            template.fill("Kafka", "square 1:1", audience = "architects", style = "navy", brand = "Acme"),
        )
    }

    @Test
    fun `byId falls back to the universal template`() {
        assertEquals("comparison", templates.byId(" Comparison ").id)
        assertEquals(UNIVERSAL_TEMPLATE, templates.byId("nope").id)
        assertEquals(UNIVERSAL_TEMPLATE, templates.byId(null).id)
    }

    @Test
    fun `the asset has the ten templates, each with content and format slots`() {
        assertEquals(
            listOf("universal", "concept", "architecture", "comparison", "workflow", "code", "checklist", "data", "learning", "series"),
            templates.map { it.id },
        )
        assertTrue(templates.all { it.name.isNotBlank() && it.useWhen.isNotBlank() })
        assertTrue(templates.all { "{{content}}" in it.body && "{{format}}" in it.body })
    }

    @Test
    fun `filled templates have no slots or source placeholders left`() {
        for (template in templates) {
            val filled = template.fill("content", "portrait 2:3", "developers", "navy", "Acme")
            assertFalse(template.id, filled.contains("{{"))
            assertFalse(template.id, filled.contains("[PASTE") || filled.contains("[OPTIONAL"))
        }
    }

    @Test
    fun `only the allowed sizes are mentioned`() {
        for (size in listOf("4:5", "16:9", "9:16")) assertFalse(size, asset.contains(size))
    }
}

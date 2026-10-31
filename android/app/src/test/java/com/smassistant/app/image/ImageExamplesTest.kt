package com.smassistant.app.image

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageExamplesTest {
    private fun example(title: String, vararg keywords: String, template: String? = null) =
        ImageExample(title, keywords.toList(), "$title topic", "$title image", template)

    private val examples = listOf(
        example("ArrayList", "java", "arraylist", "collections", template = "learning"),
        example("Retry", "retry", "spring boot", "microservices", template = "architecture"),
        example("SQL", "sql", "database", template = "code"),
        example("HashMap", "java", "hashmap", "collections", "red-black tree", template = "learning"),
    )

    @Test
    fun `parses blocks after the intro and skips incomplete ones`() {
        val text = """
            Intro with Title: not an example
            ---
            Title: One
            Keywords: Java, HashMap ,
            Template: Learning
            Topic: Topic one
            Image prompt: A poster: with a colon
            ---
            Title: Missing prompt
            Topic: nothing else
        """.trimIndent()
        assertEquals(listOf(ImageExample("One", listOf("java", "hashmap"), "Topic one", "A poster: with a colon", "learning")), parseImageExamples(text))
    }

    @Test
    fun `picks the best keyword matches first`() {
        val picked = pickImageExamples(examples, "How Java HashMap collections use a red-black tree", limit = 2)
        assertEquals(listOf("HashMap", "ArrayList"), picked.map { it.title })
        assertEquals(listOf("Retry"), pickImageExamples(examples, "Retry with Spring Boot.").map { it.title })
    }

    @Test
    fun `examples of the chosen template come first, then keyword matches`() {
        assertEquals(
            listOf("HashMap", "ArrayList", "SQL"),
            pickImageExamples(examples, "SQL tuning for a HashMap cache", template = "learning").map { it.title },
        )
        assertEquals(listOf("Retry"), pickImageExamples(examples, "Kubernetes pods", template = "architecture").map { it.title })
        // A template no example has changes nothing.
        assertEquals(listOf("ArrayList", "Retry", "SQL"), pickImageExamples(examples, "Kubernetes pods", template = "data").map { it.title })
    }

    @Test
    fun `no match falls back to the first examples`() {
        assertEquals(listOf("ArrayList", "Retry", "SQL"), pickImageExamples(examples, "Kubernetes pods").map { it.title })
    }

    @Test
    fun `keywords match whole words only`() {
        assertEquals(listOf("ArrayList", "Retry", "SQL"), pickImageExamples(examples, "javascript sqlite").map { it.title })
    }

    @Test
    fun `formats numbered examples`() {
        assertEquals(
            "Example 1: SQL\nTopic: SQL topic\nImage: SQL image",
            formatImageExamples(listOf(examples[2])),
        )
    }

    @Test
    fun `the asset has an example for every sample image and the template uses them`() {
        val parsed = parseImageExamples(File("src/main/assets/prompts/image/image_examples.md").readText())
        assertEquals(27, parsed.size)
        assertTrue(parsed.all { it.keywords.isNotEmpty() })
        // Every example is tagged with a template the app has (never the Series template, which the chooser never picks).
        val ids = parseImageTemplates(File("src/main/assets/prompts/image/image_templates.md").readText()).map { it.id }.toSet() - SERIES_TEMPLATE
        parsed.forEach { assertTrue("${it.title}: ${it.template}", it.template in ids) }
        assertTrue(File("src/main/assets/prompts/image/image.md").readText().contains("{{examples}}"))
    }
}

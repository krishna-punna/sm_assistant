package com.smassistant.app.generate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageExamplesTest {
    private fun example(title: String, vararg keywords: String) =
        ImageExample(title, keywords.toList(), "$title topic", "$title image")

    private val examples = listOf(
        example("ArrayList", "java", "arraylist", "collections"),
        example("Retry", "retry", "spring boot", "microservices"),
        example("SQL", "sql", "database"),
        example("HashMap", "java", "hashmap", "collections", "red-black tree"),
    )

    @Test
    fun `parses blocks after the intro and skips incomplete ones`() {
        val text = """
            Intro with Title: not an example
            ---
            Title: One
            Keywords: Java, HashMap ,
            Topic: Topic one
            Image prompt: A poster: with a colon
            ---
            Title: Missing prompt
            Topic: nothing else
        """.trimIndent()
        assertEquals(listOf(ImageExample("One", listOf("java", "hashmap"), "Topic one", "A poster: with a colon")), parseImageExamples(text))
    }

    @Test
    fun `picks the best keyword matches first`() {
        val picked = pickImageExamples(examples, "How Java HashMap collections use a red-black tree", limit = 2)
        assertEquals(listOf("HashMap", "ArrayList"), picked.map { it.title })
        assertEquals(listOf("Retry"), pickImageExamples(examples, "Retry with Spring Boot.").map { it.title })
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
        val parsed = parseImageExamples(File("src/main/assets/prompts/image_examples.md").readText())
        assertEquals(21, parsed.size)
        assertTrue(parsed.all { it.keywords.isNotEmpty() })
        assertTrue(File("src/main/assets/prompts/image.md").readText().contains("{{examples}}"))
    }
}

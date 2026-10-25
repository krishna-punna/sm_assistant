package com.smassistant.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    private fun parse(yaml: String) = parseAppConfig(yaml.byteInputStream())

    @Test
    fun `reads api, key and model for every service`() {
        val config = parse(
            """
            apis:
              linkedin:
                api: https://api.linkedin.com/rest/posts
                key: li-token
              claude:
                api: https://api.anthropic.com/v1/messages
                model: claude-sonnet-5-5
                key: ""
              gemini:
                api: https://generativelanguage.googleapis.com/v1beta
                model: gemini-2.5-flash
              groq:
                api: https://api.groq.com/openai/v1/chat/completions
                model: llama-3.3-70b-versatile
                key: gsk
            """.trimIndent()
        )

        assertEquals(ApiConfig("https://api.linkedin.com/rest/posts", "li-token"), config[ApiService.LINKEDIN])
        assertEquals(
            ApiConfig("https://api.anthropic.com/v1/messages", "", "claude-sonnet-5-5"),
            config[ApiService.CLAUDE],
        )
        assertEquals("", config.getValue(ApiService.GEMINI).key)
        assertEquals("gsk", config.getValue(ApiService.GROQ).key)
    }

    @Test
    fun `missing service gets empty values`() {
        val config = parse("apis:\n  claude:\n    api: https://x\n")

        assertEquals(ApiConfig("", ""), config[ApiService.GROQ])
        assertEquals(ApiService.entries.toSet(), config.keys)
    }

    @Test
    fun `empty or malformed file gives empty values for all services`() {
        listOf("", "just text", "apis: 3").forEach { yaml ->
            val config = parse(yaml)
            ApiService.entries.forEach { assertEquals(yaml, ApiConfig("", ""), config[it]) }
        }
    }

    @Test
    fun `bundled app yml ships without keys`() {
        val file = java.io.File("src/main/assets/app.yml")
        val config = file.inputStream().use(::parseAppConfig)

        ApiService.entries.forEach { service ->
            assertEquals("${service.configKey} key must be empty in app.yml", "", config.getValue(service).key)
            assertTrue("${service.configKey} api", config.getValue(service).api.startsWith("https://"))
        }
    }
}

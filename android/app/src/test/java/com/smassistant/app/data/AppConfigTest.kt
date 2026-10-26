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
                image_model: gemini-2.5-flash-image
                video_model: gemini-3-pro-image
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
        assertEquals("gemini-2.5-flash-image", config.getValue(ApiService.GEMINI).imageModel)
        assertEquals("gemini-3-pro-image", config.getValue(ApiService.GEMINI).videoModel)
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
            assertEquals("${service.configKey} account_id must be empty in app.yml", "", config.getValue(service).accountId)
        }
    }

    @Test
    fun `reads cloudflare's account id and fills it into the api url`() {
        val config = parse(
            """
            apis:
              cloudflare:
                api: https://api.cloudflare.com/client/v4/accounts/{account_id}/ai
                account_id: abc123
                model: "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
            """.trimIndent()
        ).getValue(ApiService.CLOUDFLARE)

        assertEquals("abc123", config.accountId)
        assertEquals("https://api.cloudflare.com/client/v4/accounts/abc123/ai", config.apiUrl)
        assertEquals(false, config.copy(accountId = "").isConfigured())
        assertEquals(false, config.isConfigured())
        assertEquals(true, config.copy(key = "token").isConfigured())
    }

    @Test
    fun `reads the LinkedIn post rules, with defaults for missing or bad values`() {
        assertEquals(
            PostRules(maxCharacters = 2_500, shortenAttempts = 3, aiHashtags = 5),
            parsePostRules("linkedin_post:\n  max_characters: 2500\n  shorten_attempts: 3\n  ai_hashtags: 5".byteInputStream()),
        )
        assertEquals(3, parsePostRules("linkedin_post:\n  ai_hashtags: 0".byteInputStream()).aiHashtags)
        assertEquals(PostRules(3_000, 2), parsePostRules("apis: {}".byteInputStream()))
        assertEquals(PostRules(3_000, 2), parsePostRules("linkedin_post:\n  max_characters: lots\n  shorten_attempts: -1".byteInputStream()))
    }

    @Test
    fun `bundled app yml sets the 3000 character limit`() {
        val rules = java.io.File("src/main/assets/app.yml").inputStream().use(::parsePostRules)
        assertEquals(3_000, rules.maxCharacters)
        assertEquals(3, rules.aiHashtags)
    }

    @Test
    fun `the video model falls back to the image model, and Claude to its text model`() {
        val gemini = ApiConfig("https://x", "k", "text", imageModel = "img")
        assertEquals("img", gemini.forMedia(MediaPurpose.VIDEO).imageModel)
        assertEquals("vid", gemini.copy(videoModel = "vid").forMedia(MediaPurpose.VIDEO).imageModel)
        assertEquals("img", gemini.copy(videoModel = "vid").forMedia(MediaPurpose.IMAGE).imageModel)

        val claude = ApiConfig("https://x", "k", "sonnet")
        assertEquals("sonnet", claude.forMedia(MediaPurpose.IMAGE).model)
        assertEquals("opus", claude.copy(imageModel = "opus").forMedia(MediaPurpose.VIDEO).model)
        assertEquals("haiku", claude.copy(imageModel = "opus", videoModel = "haiku").forMedia(MediaPurpose.VIDEO).model)
    }
}

package com.smassistant.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            ApiConfig("https://api.groq.com/openai/v1/chat/completions", "gsk", "llama-3.3-70b-versatile"),
            config[ApiService.GROQ],
        )
        assertEquals("", config.getValue(ApiService.GEMINI).key)
        assertEquals("gemini-2.5-flash-image", config.getValue(ApiService.GEMINI).imageModel)
        assertEquals("gemini-3-pro-image", config.getValue(ApiService.GEMINI).videoModel)
        assertEquals("gsk", config.getValue(ApiService.GROQ).key)
    }

    @Test
    fun `missing service gets empty values`() {
        val config = parse("apis:\n  gemini:\n    api: https://x\n")

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
        assertEquals(
            listOf("#Java", "#AIEngineer"),
            parsePostRules("linkedin_post:\n  default_hashtags: \"Java, #AIEngineer #C++ #java\"".byteInputStream()).defaultHashtags,
        )
        assertEquals(PostRules(3_000, 3), parsePostRules("apis: {}".byteInputStream()))
        assertEquals(PostRules(3_000, 3), parsePostRules("linkedin_post:\n  max_characters: lots\n  shorten_attempts: -1".byteInputStream()))
    }

    @Test
    fun `bundled app yml sets the 3000 character limit`() {
        val rules = java.io.File("src/main/assets/app.yml").inputStream().use(::parsePostRules)
        assertEquals(3_000, rules.maxCharacters)
        assertEquals(3, rules.aiHashtags)
        assertEquals(
            listOf("#JavaDeveloper", "#FullStackDeveloper", "#AIAssistedDeveloper", "#AIEngineer", "#OpenToWork"),
            rules.defaultHashtags,
        )
    }

    @Test
    fun `the video model falls back to the image model, and a blank media model keeps the text model`() {
        val gemini = ApiConfig("https://x", "k", "text", imageModel = "img")
        assertEquals("img", gemini.forMedia(MediaPurpose.VIDEO).imageModel)
        assertEquals("vid", gemini.copy(videoModel = "vid").forMedia(MediaPurpose.VIDEO).imageModel)
        assertEquals("img", gemini.copy(videoModel = "vid").forMedia(MediaPurpose.IMAGE).imageModel)

        val textOnly = ApiConfig("https://x", "k", "text")
        assertEquals("text", textOnly.forMedia(MediaPurpose.IMAGE).model)
        assertEquals("img", textOnly.copy(imageModel = "img").forMedia(MediaPurpose.VIDEO).model)
        assertEquals("vid", textOnly.copy(imageModel = "img", videoModel = "vid").forMedia(MediaPurpose.VIDEO).model)
    }

    @Test
    fun `image checks come from app yml with defaults for missing or invalid values`() {
        assertEquals(
            ImageCheckRules(visionReview = false, minScore = 8, retries = 2),
            parseImageCheckRules("image_checks:\n  vision_review: false\n  min_score: 8\n  retries: 2".byteInputStream()),
        )
        assertEquals(ImageCheckRules(), parseImageCheckRules("apis: {}".byteInputStream()))
        assertEquals(ImageCheckRules(), parseImageCheckRules("image_checks:\n  vision_review: maybe\n  min_score: 11\n  retries: 9".byteInputStream()))
        assertEquals(ImageCheckRules(), File("src/main/assets/app.yml").inputStream().use(::parseImageCheckRules))
    }

    @Test
    fun `image prompt budgets come from app yml, 0 meaning no limit (AC-6)`() {
        val shipped = File("src/main/assets/app.yml").inputStream().use(::parseImagePromptBudgets)
        assertNull(shipped.of(ApiService.GEMINI))
        assertEquals(2000, shipped.of(ApiService.CLOUDFLARE))
        assertEquals(1500, shipped.of(ApiService.POLLINATIONS))
        val custom = parseImagePromptBudgets("image_prompt_budgets:\n  pollinations: 900\n  cloudflare: nope".byteInputStream())
        assertEquals(900, custom.of(ApiService.POLLINATIONS))
        assertEquals(2000, custom.of(ApiService.CLOUDFLARE))
        assertEquals(ImagePromptBudgets(), parseImagePromptBudgets("other: 1".byteInputStream()))
    }

    @Test
    fun `image style comes from app yml with defaults for missing or invalid values`() {
        assertEquals(
            ImageStyle("students", "Acme", "navy cards", true, ImageSize.LANDSCAPE, ImageMode.DRAWN, ImageLayout.AUTO),
            parseImageStyle(
                "image_style:\n  audience: students\n  brand_name: Acme\n  style_guide: navy cards\n  series: true\n  format: Landscape\n  mode: drawn\n  layout: carousel"
                    .byteInputStream(),
            ),
        )
        assertEquals(ImageStyle(), parseImageStyle("apis: {}".byteInputStream()))
        assertEquals(ImageStyle(), parseImageStyle("image_style:\n  series: maybe\n  format: 4:5\n  mode: video\n  layout: tall".byteInputStream()))
        assertEquals(ImageStyle(), File("src/main/assets/app.yml").inputStream().use(::parseImageStyle))
    }
}

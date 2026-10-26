package com.smassistant.app.data

import java.io.InputStream
import org.yaml.snakeyaml.Yaml

/** External services the app calls. [configKey] is the service's entry under `apis:` in app.yml. */
enum class ApiService(val displayName: String, val configKey: String) {
    LINKEDIN("LinkedIn", "linkedin"),
    CLAUDE("Claude", "claude"),
    GEMINI("Gemini", "gemini"),
    GROQ("Groq", "groq"),
}

/** [imageModel] is only used by Gemini, for image generation (AGC-2). */
data class ApiConfig(val api: String, val key: String, val model: String = "", val imageModel: String = "")

/**
 * Parses app.yml, in this shape:
 *
 * ```yaml
 * apis:
 *   claude:
 *     api: https://api.anthropic.com/v1/messages
 *     model: claude-sonnet-5-5
 *     key: ""
 *   gemini:
 *     model: gemini-2.5-flash
 *     image_model: gemini-2.5-flash-image
 * ```
 *
 * Every [ApiService] gets an entry; missing services or fields become empty strings.
 */
fun parseAppConfig(input: InputStream): Map<ApiService, ApiConfig> {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val apis = root["apis"] as? Map<*, *> ?: emptyMap<String, Any?>()
    return ApiService.entries.associateWith { service ->
        val entry = apis[service.configKey] as? Map<*, *> ?: emptyMap<String, Any?>()
        ApiConfig(
            api = entry["api"]?.toString().orEmpty(),
            key = entry["key"]?.toString().orEmpty(),
            model = entry["model"]?.toString().orEmpty(),
            imageModel = entry["image_model"]?.toString().orEmpty(),
        )
    }
}

/**
 * LinkedIn post length rules (docs/requirements/12-post-length.md), from app.yml:
 *
 * ```yaml
 * linkedin_post:
 *   max_characters: 3000
 *   shorten_attempts: 3
 *   ai_hashtags: 3
 * ```
 *
 * [shortenAttempts] is how many times the agent is asked to shorten a post that's over the limit.
 * [aiHashtags] is the most hashtags the agent adds when AI-suggested hashtags are on (1 to 10, HT-21).
 * Missing or invalid values fall back to the defaults.
 */
data class PostRules(val maxCharacters: Int = 3_000, val shortenAttempts: Int = 3, val aiHashtags: Int = 3)

fun parsePostRules(input: InputStream): PostRules {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val entry = root["linkedin_post"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = PostRules()
    return PostRules(
        maxCharacters = entry["max_characters"]?.toString()?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.maxCharacters,
        shortenAttempts = entry["shorten_attempts"]?.toString()?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.shortenAttempts,
        aiHashtags = entry["ai_hashtags"]?.toString()?.toIntOrNull()?.takeIf { it in 1..10 } ?: defaults.aiHashtags,
    )
}

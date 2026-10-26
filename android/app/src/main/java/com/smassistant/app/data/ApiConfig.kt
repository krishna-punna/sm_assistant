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

/**
 * [imageModel] and [videoModel] are used by the image agents: [imageModel] for Generate Image,
 * [videoModel] for the video slides. A blank one falls back as [forMedia] describes.
 */
data class ApiConfig(
    val api: String,
    val key: String,
    val model: String = "",
    val imageModel: String = "",
    val videoModel: String = "",
)

/** What an image agent is drawing: images for a post, or the slides of a video. */
enum class MediaPurpose { IMAGE, VIDEO }

/** The model chosen for [purpose]: the video model falls back to the image model. Blank when neither is set. */
fun ApiConfig.mediaModel(purpose: MediaPurpose): String =
    if (purpose == MediaPurpose.VIDEO) videoModel.ifBlank { imageModel } else imageModel

/**
 * This config with [imageModel] and [model] set to the model for [purpose], so an image agent
 * can read whichever it uses. Claude draws with its text [model] when no media model is chosen.
 */
fun ApiConfig.forMedia(purpose: MediaPurpose): ApiConfig {
    val chosen = mediaModel(purpose)
    return copy(imageModel = chosen, model = chosen.ifBlank { model })
}

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
 *     video_model: ""
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
            videoModel = entry["video_model"]?.toString().orEmpty(),
        )
    }
}

/**
 * LinkedIn post length rules (docs/requirements/12-post-length.md), from app.yml:
 *
 * ```yaml
 * linkedin_post:
 *   max_characters: 3000
 *   shorten_attempts: 2
 *   ai_hashtags: 3
 * ```
 *
 * [shortenAttempts] is how many times the agent is asked to shorten a post that's over the limit.
 * [aiHashtags] is the most hashtags the agent adds when AI-suggested hashtags are on (1 to 10, HT-21).
 * Missing or invalid values fall back to the defaults.
 */
data class PostRules(val maxCharacters: Int = 3_000, val shortenAttempts: Int = 2, val aiHashtags: Int = 3)

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

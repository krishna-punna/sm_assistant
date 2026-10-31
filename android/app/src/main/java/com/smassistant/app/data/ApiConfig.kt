package com.smassistant.app.data

import com.smassistant.app.hashtags.Hashtags
import java.io.InputStream
import org.yaml.snakeyaml.Yaml

/** External services the app calls. [configKey] is the service's entry under `apis:` in app.yml. */
enum class ApiService(val displayName: String, val configKey: String) {
    LINKEDIN("LinkedIn", "linkedin"),
    GEMINI("Gemini", "gemini"),
    GROQ("Groq", "groq"),
    CLOUDFLARE("Cloudflare", "cloudflare"),
    POLLINATIONS("Pollinations.AI", "pollinations"),
}

/**
 * [imageModel] and [videoModel] are used by the image agents: [imageModel] for Generate Image,
 * [videoModel] for the video slides. A blank one falls back as [forMedia] describes.
 * [accountId] fills the `{account_id}` placeholder in [api], which only Cloudflare's URL has.
 */
data class ApiConfig(
    val api: String,
    val key: String,
    val model: String = "",
    val imageModel: String = "",
    val videoModel: String = "",
    val accountId: String = "",
) {
    /** [api] with the account id filled in. */
    val apiUrl: String get() = api.trim().replace(ACCOUNT_ID_PLACEHOLDER, accountId.trim())

    /** True when the URL needs an account id that hasn't been entered. */
    val missingAccountId: Boolean get() = ACCOUNT_ID_PLACEHOLDER in api && accountId.isBlank()

    /** True when there's an API URL, a key and (where the URL needs one) an account id. */
    fun isConfigured() = api.isNotBlank() && key.isNotBlank() && !missingAccountId

    companion object {
        const val ACCOUNT_ID_PLACEHOLDER = "{account_id}"
    }
}

/** What an image agent is drawing: images for a post, or the slides of a video. */
enum class MediaPurpose { IMAGE, VIDEO }

/** The model chosen for [purpose]: the video model falls back to the image model. Blank when neither is set. */
fun ApiConfig.mediaModel(purpose: MediaPurpose): String =
    if (purpose == MediaPurpose.VIDEO) videoModel.ifBlank { imageModel } else imageModel

/**
 * This config with [imageModel] and [model] set to the model for [purpose], so an image agent
 * can read whichever it uses.
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
 *   groq:
 *     api: https://api.groq.com/openai/v1/chat/completions
 *     model: llama-3.3-70b-versatile
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
            accountId = entry["account_id"]?.toString().orEmpty(),
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
 *   shorten_attempts: 3
 *   ai_hashtags: 3
 *   default_hashtags: "#JavaDeveloper #AIEngineer"
 * ```
 *
 * [shortenAttempts] is how many times the agent is asked to shorten a post that's over the limit.
 * [aiHashtags] is the most hashtags the agent adds when AI-suggested hashtags are on (1 to 10, HT-21).
 * [defaultHashtags] is the Default Hash Tags list before the user saves their own; invalid tags are dropped.
 * Missing or invalid values fall back to the defaults.
 */
data class PostRules(
    val maxCharacters: Int = 3_000,
    val shortenAttempts: Int = 3,
    val aiHashtags: Int = 3,
    val defaultHashtags: List<String> = emptyList(),
)

fun parsePostRules(input: InputStream): PostRules {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val entry = root["linkedin_post"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = PostRules()
    return PostRules(
        maxCharacters = entry["max_characters"]?.toString()?.toIntOrNull()?.takeIf { it > 0 } ?: defaults.maxCharacters,
        shortenAttempts = entry["shorten_attempts"]?.toString()?.toIntOrNull()?.takeIf { it >= 0 } ?: defaults.shortenAttempts,
        aiHashtags = entry["ai_hashtags"]?.toString()?.toIntOrNull()?.takeIf { it in 1..10 } ?: defaults.aiHashtags,
        defaultHashtags = entry["default_hashtags"]?.toString()?.let { Hashtags.parse(it).tags } ?: defaults.defaultHashtags,
    )
}

/**
 * The checks before a generated image is finished (docs/design/image-video-pipeline.md), from app.yml:
 *
 * ```yaml
 * image_checks:
 *   vision_review: true
 *   min_score: 7
 *   retries: 1
 * ```
 *
 * With [visionReview] on, the text agent looks at each finished infographic and scores it out of
 * 10; one under [minScore] is written and drawn again, up to [retries] times, and the best one is
 * kept. Missing or invalid values fall back to the defaults.
 */
data class ImageCheckRules(val visionReview: Boolean = true, val minScore: Int = 7, val retries: Int = 1)

fun parseImageCheckRules(input: InputStream): ImageCheckRules {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val entry = root["image_checks"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = ImageCheckRules()
    return ImageCheckRules(
        visionReview = entry["vision_review"]?.toString()?.toBooleanStrictOrNull() ?: defaults.visionReview,
        minScore = entry["min_score"]?.toString()?.toIntOrNull()?.takeIf { it in 0..10 } ?: defaults.minScore,
        retries = entry["retries"]?.toString()?.toIntOrNull()?.takeIf { it in 0..3 } ?: defaults.retries,
    )
}

/** An image shape the app allows (docs/design/image-templates.md): never 4:5, 9:16 or 16:9. */
enum class ImageSize(val key: String, val words: String) {
    PORTRAIT("portrait", "portrait 2:3"),
    SQUARE("square", "square 1:1"),
    LANDSCAPE("landscape", "landscape 3:2"),
    ;

    companion object {
        /** The format with [key], or null for "auto" and anything unknown. */
        fun of(key: String?): ImageSize? = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/** How an image is made from its filled template: drawn by the app, painted by the image agent, or both side by side. */
enum class ImageMode(val key: String) {
    DRAWN("drawn"),
    PAINTED("painted"),
    BOTH("both"),
    ;

    companion object {
        fun of(key: String?): ImageMode? = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/**
 * Whether a request's content is made into one image or a carousel (docs/requirements/18, SV-5):
 * Auto makes one poster and has content that doesn't fit written shorter, Single always makes one
 * image (smaller text when it is long), Carousel always makes a carousel.
 */
enum class ImageLayout(val key: String) {
    AUTO("auto"),
    SINGLE("single"),
    CAROUSEL("carousel"),
    ;

    companion object {
        fun of(key: String?): ImageLayout? = entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }
    }
}

/**
 * The inputs the image templates take besides the content (docs/design/image-templates.md), from app.yml:
 *
 * ```yaml
 * image_style:
 *   audience: "developers, senior engineers and architects"
 *   brand_name: ""
 *   style_guide: ""
 *   series: false
 *   format: auto
 *   mode: both
 *   layout: auto
 * ```
 *
 * A null [format] is Auto: the app picks one per image. Missing or invalid values fall back to the defaults.
 */
data class ImageStyle(
    val audience: String = "developers, senior engineers and architects",
    val brandName: String = "",
    val styleGuide: String = "",
    val series: Boolean = false,
    val format: ImageSize? = null,
    val mode: ImageMode = ImageMode.BOTH,
    val layout: ImageLayout = ImageLayout.AUTO,
)

/**
 * The inputs the video templates take besides the content (docs/requirements/19, SP-15), from
 * app.yml's own `video_style`, apart from the images' `image_style`:
 *
 * ```yaml
 * video_style:
 *   audience: "developers, senior engineers and architects"
 *   brand_name: ""
 *   style_guide: ""
 * ```
 */
data class VideoStyle(
    val audience: String = "developers, senior engineers and architects",
    val brandName: String = "",
    val styleGuide: String = "",
)

fun parseVideoStyle(input: InputStream): VideoStyle {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val entry = root["video_style"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = VideoStyle()
    return VideoStyle(
        audience = entry["audience"]?.toString()?.trim() ?: defaults.audience,
        brandName = entry["brand_name"]?.toString()?.trim() ?: defaults.brandName,
        styleGuide = entry["style_guide"]?.toString()?.trim() ?: defaults.styleGuide,
    )
}

fun parseImageStyle(input: InputStream): ImageStyle {
    val root = Yaml().load<Any?>(input) as? Map<*, *> ?: emptyMap<String, Any?>()
    val entry = root["image_style"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = ImageStyle()
    return ImageStyle(
        audience = entry["audience"]?.toString()?.trim() ?: defaults.audience,
        brandName = entry["brand_name"]?.toString()?.trim() ?: defaults.brandName,
        styleGuide = entry["style_guide"]?.toString()?.trim() ?: defaults.styleGuide,
        series = entry["series"]?.toString()?.toBooleanStrictOrNull() ?: defaults.series,
        format = ImageSize.of(entry["format"]?.toString()),
        mode = ImageMode.of(entry["mode"]?.toString()) ?: defaults.mode,
        layout = ImageLayout.of(entry["layout"]?.toString()) ?: defaults.layout,
    )
}

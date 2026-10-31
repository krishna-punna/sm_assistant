package com.smassistant.app.agents

import android.content.Context
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.data.forMedia
import com.smassistant.app.media.MediaCache
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.video.DEFAULT_VIDEO_SECONDS
import com.smassistant.app.video.VIDEO_LENGTHS

/** Agents that can generate text, in fallback order. */
val TEXT_AGENTS = listOf(
    ApiService.GEMINI,
    ApiService.GROQ,
    ApiService.CLOUDFLARE,
    ApiService.POLLINATIONS,
)

/**
 * Agents that can make images, in fallback order: Gemini paints them, and Cloudflare and
 * Pollinations.AI run diffusion models such as FLUX.
 */
val IMAGE_AGENTS = listOf(
    ApiService.GEMINI,
    ApiService.CLOUDFLARE,
    ApiService.POLLINATIONS,
)

/** True when [service] has what it needs to make images: a key and an image model. */
fun ApiConfig.isUsableImageAgent(service: ApiService) = when (service) {
    ApiService.GEMINI -> key.isNotBlank() && imageModel.isNotBlank()
    ApiService.CLOUDFLARE, ApiService.POLLINATIONS -> isConfigured() && imageModel.isNotBlank()
    else -> false
}

/** The user's choice of default text, image and video agents (interim answer to Q-05). */
class AgentPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)

    var defaultTextAgent: ApiService?
        get() = prefs.getString(KEY_DEFAULT_TEXT, null)?.let { name -> TEXT_AGENTS.firstOrNull { it.name == name } }
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_TEXT, value?.name).apply()
        }

    var defaultImageAgent: ApiService?
        get() = prefs.getString(KEY_DEFAULT_IMAGE, null)?.let { name -> IMAGE_AGENTS.firstOrNull { it.name == name } }
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_IMAGE, value?.name).apply()
        }

    /** The agent for video slides. Until one is chosen, the video follows the image agent. */
    var defaultVideoAgent: ApiService?
        get() = prefs.getString(KEY_DEFAULT_VIDEO, null)?.let { name -> IMAGE_AGENTS.firstOrNull { it.name == name } }
            ?: defaultImageAgent
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_VIDEO, value?.name).apply()
        }

    fun defaultMediaAgent(purpose: MediaPurpose): ApiService? =
        if (purpose == MediaPurpose.VIDEO) defaultVideoAgent else defaultImageAgent

    fun setDefaultMediaAgent(purpose: MediaPurpose, service: ApiService) {
        if (purpose == MediaPurpose.VIDEO) defaultVideoAgent = service else defaultImageAgent = service
    }

    /** How long Generate Video makes videos, in seconds, one of [VIDEO_LENGTHS] (Requirement 9). */
    var videoSeconds: Int
        get() = prefs.getInt(KEY_VIDEO_SECONDS, DEFAULT_VIDEO_SECONDS).takeIf { it in VIDEO_LENGTHS } ?: DEFAULT_VIDEO_SECONDS
        set(value) {
            prefs.edit().putInt(KEY_VIDEO_SECONDS, value).apply()
        }

    /** The Prompt Type last picked on Prepare Prompt (docs/requirements/14); Text until one is picked. */
    var promptType: PromptType
        get() = prefs.getString(KEY_PROMPT_TYPE, null)?.let { name -> PromptType.entries.firstOrNull { it.name == name } } ?: PromptType.TEXT
        set(value) {
            prefs.edit().putString(KEY_PROMPT_TYPE, value.name).apply()
        }

    /** The image template id last picked on the image screens (docs/design/image-templates.md); null is Auto. */
    var imageTemplate: String?
        get() = prefs.getString(KEY_IMAGE_TEMPLATE, null)
        set(value) {
            prefs.edit().putString(KEY_IMAGE_TEMPLATE, value).apply()
        }

    /** The image format last picked on the image screens; null is Auto (the app picks per image). */
    var imageFormat: ImageSize?
        get() = ImageSize.of(prefs.getString(KEY_IMAGE_FORMAT, null))
        set(value) {
            prefs.edit().putString(KEY_IMAGE_FORMAT, value?.key).apply()
        }

    private companion object {
        const val KEY_IMAGE_TEMPLATE = "image_template"
        const val KEY_IMAGE_FORMAT = "image_format"
        const val KEY_PROMPT_TYPE = "prompt_type"
        const val KEY_VIDEO_SECONDS = "video_seconds"
        const val KEY_DEFAULT_TEXT = "default_text_agent"
        const val KEY_DEFAULT_IMAGE = "default_image_agent"
        const val KEY_DEFAULT_VIDEO = "default_video_agent"
    }
}

/**
 * Picks the agent for text features (AG-5, GEN-1): the user's default if it's set up,
 * otherwise the first set-up agent in [TEXT_AGENTS] order. Null when none is set up.
 */
class AgentSelector(
    private val configOf: (ApiService) -> ApiConfig,
    private val preferred: () -> ApiService?,
    private val agentFor: (ApiService) -> TextAgent,
) {
    fun textAgent(): TextAgent? =
        (listOfNotNull(preferred()) + TEXT_AGENTS).firstOrNull { configOf(it).isUsableAgent() }?.let(agentFor)
}

/**
 * Picks the agent for image features: the user's default image agent if it's set up, otherwise
 * the first set-up agent in [IMAGE_AGENTS] order (so Gemini stays the default). Null when none is.
 */
class ImageAgentSelector(
    private val configOf: (ApiService) -> ApiConfig,
    private val preferred: () -> ApiService?,
    private val agentFor: (ApiService) -> ImageAgent,
) {
    fun imageAgent(): ImageAgent? =
        (listOfNotNull(preferred()) + IMAGE_AGENTS).firstOrNull { configOf(it).isUsableImageAgent(it) }?.let(agentFor)
}

/** The selector the screens use: saved settings, the user's default, and the real HTTP clients. */
fun appAgentSelector(context: Context): AgentSelector {
    val store = ApiSettingsStore(context)
    val preferences = AgentPreferences(context)
    return AgentSelector(
        configOf = store::load,
        preferred = { preferences.defaultTextAgent },
        agentFor = { service -> textAgentFor(service, { store.load(service) }) },
    )
}

/**
 * The image selector the screens use: Gemini's image model, or the image model of Cloudflare or
 * Pollinations.AI. [purpose] picks the default agent and model: images and video slides each have their own.
 */
fun appImageAgentSelector(
    context: Context,
    cache: MediaCache = MediaCache(context),
    purpose: MediaPurpose = MediaPurpose.IMAGE,
): ImageAgentSelector {
    val store = ApiSettingsStore(context)
    val preferences = AgentPreferences(context)
    val configOf = { service: ApiService -> store.load(service).forMedia(purpose) }
    val gemini = GeminiImageAgent({ configOf(ApiService.GEMINI) }, cache)
    return ImageAgentSelector(
        configOf = configOf,
        preferred = { preferences.defaultMediaAgent(purpose) },
        agentFor = { service ->
            when (service) {
                ApiService.CLOUDFLARE -> CloudflareImageAgent({ configOf(service) }, cache)
                ApiService.POLLINATIONS -> PollinationsImageAgent({ configOf(service) }, cache)
                else -> gemini
            }
        },
    )
}

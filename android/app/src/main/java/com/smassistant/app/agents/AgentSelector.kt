package com.smassistant.app.agents

import android.content.Context
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.data.forMedia
import com.smassistant.app.media.AndroidSvgRenderer
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.Http
import com.smassistant.app.prompt.loadPromptAsset
import java.util.concurrent.TimeUnit

/** Agents that can generate text, in fallback order. */
val TEXT_AGENTS = listOf(
    ApiService.CLAUDE,
    ApiService.GEMINI,
    ApiService.GROQ,
    ApiService.HUGGINGFACE,
    ApiService.CLOUDFLARE,
    ApiService.POLLINATIONS,
)

/**
 * Agents that can make images, in fallback order: Gemini paints them, Claude writes them as SVG,
 * and Hugging Face, Cloudflare and Pollinations.AI run diffusion models such as FLUX.
 */
val IMAGE_AGENTS = listOf(
    ApiService.GEMINI,
    ApiService.CLAUDE,
    ApiService.HUGGINGFACE,
    ApiService.CLOUDFLARE,
    ApiService.POLLINATIONS,
)

/** True when [service] has what it needs to make images: Claude a text model, the others an image model. */
fun ApiConfig.isUsableImageAgent(service: ApiService) = when (service) {
    ApiService.GEMINI -> key.isNotBlank() && imageModel.isNotBlank()
    ApiService.CLAUDE -> isUsableAgent()
    ApiService.HUGGINGFACE, ApiService.CLOUDFLARE, ApiService.POLLINATIONS -> isConfigured() && imageModel.isNotBlank()
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

    private companion object {
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
 * The image selector the screens use: Gemini's image model, Claude drawing SVG (`prompts/image_svg.md`)
 * rendered on the device, or the image model of Hugging Face, Cloudflare or Pollinations.AI.
 * [purpose] picks the default agent and model: images and video slides each have their own.
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
    // A long SVG can take a few minutes to write.
    val svgClient = Http.generationClient.newBuilder().readTimeout(300, TimeUnit.SECONDS).build()
    val claude = ClaudeSvgImageAgent(
        text = claudeAgent({ configOf(ApiService.CLAUDE) }, svgClient, SVG_MAX_TOKENS),
        systemPrompt = loadPromptAsset(context, "image_svg"),
        cache = cache,
        renderer = AndroidSvgRenderer(),
    )
    return ImageAgentSelector(
        configOf = configOf,
        preferred = { preferences.defaultMediaAgent(purpose) },
        agentFor = { service ->
            when (service) {
                ApiService.CLAUDE -> claude
                ApiService.HUGGINGFACE -> HuggingFaceImageAgent({ configOf(service) }, cache)
                ApiService.CLOUDFLARE -> CloudflareImageAgent({ configOf(service) }, cache)
                ApiService.POLLINATIONS -> PollinationsImageAgent({ configOf(service) }, cache)
                else -> gemini
            }
        },
    )
}

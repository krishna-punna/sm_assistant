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

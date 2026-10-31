package com.smassistant.app.chat

import com.smassistant.app.generate.ContentAndMedia
import com.smassistant.app.net.path
import com.smassistant.app.net.textOrNull
import com.smassistant.app.prompt.PreparedPrompt
import com.smassistant.app.prompt.PromptSet
import com.smassistant.app.prompt.PromptType
import com.smassistant.app.video.GeneratedVideo
import com.smassistant.app.video.VideoSize
import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Turns a feature's result into JSON and back, so the conversation survives process death (NAV-4). */
interface ResultSaver<R> {
    fun save(value: R): JsonElement

    /** Null when the result can't come back, e.g. its media was cleared from the cache. */
    fun restore(json: JsonElement): R?
}

const val MEDIA_GONE = "The media for this result is no longer on the phone. Tap Retry to make it again."
const val STOPPED = "Stopped when the app was closed. Tap Retry."

object TextSaver : ResultSaver<String> {
    override fun save(value: String) = JsonPrimitive(value)
    override fun restore(json: JsonElement) = json.textOrNull
}

object PreparedPromptSaver : ResultSaver<PreparedPrompt> {
    override fun save(value: PreparedPrompt) = buildJsonObject {
        put("title", value.title)
        put("prompt", value.prompt)
    }

    override fun restore(json: JsonElement): PreparedPrompt? {
        val title = json.path("title")?.textOrNull ?: return null
        val prompt = json.path("prompt")?.textOrNull ?: return null
        return PreparedPrompt(title, prompt)
    }
}

/** Saves a Prepare Prompt result. Results saved before Prompt Type come back as Text. */
object PromptSetSaver : ResultSaver<PromptSet> {
    override fun save(value: PromptSet) = buildJsonObject {
        put("type", value.type.name)
        put("post", value.post?.let(PreparedPromptSaver::save) ?: JsonNull)
        put("content", value.content?.let(PreparedPromptSaver::save) ?: JsonNull)
    }

    override fun restore(json: JsonElement): PromptSet? {
        if (json !is JsonObject) return null
        val type = json.path("type")?.textOrNull?.let { name -> PromptType.entries.firstOrNull { it.name == name } }
            ?: return PreparedPromptSaver.restore(json)?.let { PromptSet(PromptType.TEXT, post = it) }
        val post = json["post"]?.takeIf { it !is JsonNull }?.let(PreparedPromptSaver::restore)
        val content = json["content"]?.takeIf { it !is JsonNull }?.let(PreparedPromptSaver::restore)
        if (post == null && content == null) return null
        return PromptSet(type, post, content)
    }
}

private fun files(json: JsonElement?): List<File>? =
    (json as? JsonArray)?.map { File(it.textOrNull ?: return null) }?.takeIf { list -> list.all { it.exists() } }

private fun paths(files: List<File>) = JsonArray(files.map { JsonPrimitive(it.path) })

/** Images are files in the media cache; the result is gone once any of them is. */
object ImagesSaver : ResultSaver<List<File>> {
    override fun save(value: List<File>) = paths(value)
    override fun restore(json: JsonElement) = files(json)?.takeIf { it.isNotEmpty() }
}

object VideoSaver : ResultSaver<GeneratedVideo> {
    override fun save(value: GeneratedVideo) = buildJsonObject {
        put("file", value.file.path)
        put("slides", paths(value.slides))
        put("seconds", value.seconds)
        put("format", value.format.key)
    }

    override fun restore(json: JsonElement): GeneratedVideo? {
        val file = File(json.path("file")?.textOrNull ?: return null).takeIf { it.exists() } ?: return null
        val slides = files(json.path("slides")) ?: emptyList()
        // Videos saved before the length option had 4-second slides.
        val seconds = json.path("seconds")?.textOrNull?.toIntOrNull() ?: (slides.size * 4)
        // Videos saved before the video sizes were all square.
        val format = VideoSize.of(json.path("format")?.textOrNull) ?: VideoSize.SQUARE
        return GeneratedVideo(file, slides, seconds, format)
    }
}

/**
 * Saves both halves of a Content & Image / Content & Video result. A half that was being
 * retried, or whose media has gone, comes back as an error with its own Retry.
 */
class ContentAndMediaSaver<M>(private val media: ResultSaver<M>) : ResultSaver<ContentAndMedia<M>> {
    override fun save(value: ContentAndMedia<M>) = buildJsonObject {
        put("markdown", value.markdown)
        put("textError", if (value.retryingText) STOPPED else value.textError)
        put("media", value.media?.let(media::save) ?: JsonNull)
        put("mediaError", if (value.retryingMedia) STOPPED else value.mediaError)
    }

    override fun restore(json: JsonElement): ContentAndMedia<M>? {
        if (json !is JsonObject) return null
        val savedMedia = json["media"]?.takeIf { it !is JsonNull }
        val restoredMedia = savedMedia?.let(media::restore)
        return ContentAndMedia(
            markdown = json.path("markdown")?.textOrNull,
            textError = json.path("textError")?.textOrNull,
            media = restoredMedia,
            mediaError = json.path("mediaError")?.textOrNull ?: if (savedMedia != null && restoredMedia == null) MEDIA_GONE else null,
        )
    }
}

package com.smassistant.app.history

import android.content.Context
import com.smassistant.app.media.isVideo
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Who made a post: the person from a Post in LinkedIn button, or a scheduler (with or without review). */
enum class PostSource(val label: String) { MANUAL("Manual"), SCHEDULED("Scheduled") }

/** What a post carried besides its text. */
enum class PostMedia { TEXT, IMAGES, VIDEO }

/** One post published to LinkedIn (improvement #7). [title] is the scheduler's prompt title, or the text's first line. */
data class PublishedPost(
    val id: String = UUID.randomUUID().toString(),
    val postedAt: Long,
    val title: String,
    val text: String,
    val media: PostMedia = PostMedia.TEXT,
    val mediaCount: Int = 0,
    val url: String,
    val source: PostSource = PostSource.MANUAL,
) {
    /** "Text", "3 images", "1 image" or "Video", for the history list. */
    val mediaLabel get() = when (media) {
        PostMedia.TEXT -> "Text"
        PostMedia.VIDEO -> "Video"
        PostMedia.IMAGES -> if (mediaCount == 1) "1 image" else "$mediaCount images"
    }

    companion object {
        /** The history entry for [text] posted with [files], titled [title] or by the text's first line. */
        fun of(text: String, files: List<File>, url: String, source: PostSource, postedAt: Long, title: String? = null) = PublishedPost(
            postedAt = postedAt,
            title = title?.takeIf { it.isNotBlank() } ?: titleOf(text),
            text = text,
            media = when {
                files.isEmpty() -> PostMedia.TEXT
                files.any(::isVideo) -> PostMedia.VIDEO
                else -> PostMedia.IMAGES
            },
            mediaCount = files.size,
            url = url,
            source = source,
        )

        /** A post's first non-blank line that isn't only hashtags, cut to [max] characters. */
        fun titleOf(text: String, max: Int = 80): String {
            val line = text.lineSequence().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.split(Regex("\\s+")).all { word -> word.startsWith("#") } }
                ?: return "Untitled post"
            return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
        }
    }
}

/** JSON for [PostHistoryStore]; entries that don't parse are skipped, so a bad one never hides the rest. */
object PostHistoryJson {
    fun encode(list: List<PublishedPost>): String = buildJsonArray {
        list.forEach { p ->
            add(
                buildJsonObject {
                    put("id", p.id)
                    put("postedAt", p.postedAt)
                    put("title", p.title)
                    put("text", p.text)
                    put("media", p.media.name)
                    put("mediaCount", p.mediaCount)
                    put("url", p.url)
                    put("source", p.source.name)
                },
            )
        }
    }.toString()

    fun decode(text: String?): List<PublishedPost> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { runCatching { fromJson(it.jsonObject) }.getOrNull() }
    }

    private fun fromJson(json: JsonObject) = PublishedPost(
        id = json.getValue("id").jsonPrimitive.content,
        postedAt = json.getValue("postedAt").jsonPrimitive.long,
        title = json.getValue("title").jsonPrimitive.content,
        text = json.getValue("text").jsonPrimitive.content,
        media = runCatching { PostMedia.valueOf(json.getValue("media").jsonPrimitive.content) }.getOrDefault(PostMedia.TEXT),
        mediaCount = json["mediaCount"]?.jsonPrimitive?.int ?: 0,
        url = json.getValue("url").jsonPrimitive.content,
        source = runCatching { PostSource.valueOf(json.getValue("source").jsonPrimitive.content) }.getOrDefault(PostSource.MANUAL),
    )
}

/**
 * Every published post, newest first, as one JSON list in SharedPreferences (not secret, no backup,
 * like the schedulers). The screens and the scheduler's worker both add to it, under one lock.
 * Keeps the latest [MAX_POSTS].
 */
class PostHistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("post_history", Context.MODE_PRIVATE)

    fun all(): List<PublishedPost> = synchronized(LOCK) { PostHistoryJson.decode(prefs.getString(KEY_LIST, null)) }

    fun add(post: PublishedPost) = edit { (listOf(post) + it).take(MAX_POSTS) }

    fun delete(id: String) = edit { list -> list.filterNot { it.id == id } }

    fun clear() = edit { emptyList() }

    /** Titles of the latest scheduled posts, newest first, so new scheduled prompts can avoid them. */
    fun recentScheduledTitles(limit: Int = RECENT_TITLES): List<String> =
        all().filter { it.source == PostSource.SCHEDULED }.map { it.title }.distinct().take(limit)

    private fun edit(change: (List<PublishedPost>) -> List<PublishedPost>) {
        synchronized(LOCK) {
            val list = PostHistoryJson.decode(prefs.getString(KEY_LIST, null))
            prefs.edit().putString(KEY_LIST, PostHistoryJson.encode(change(list))).commit()
        }
    }

    companion object {
        const val MAX_POSTS = 200
        const val RECENT_TITLES = 25
        private const val KEY_LIST = "list"
        private val LOCK = Any()
    }
}

package com.smassistant.app.drafts

import android.content.Context
import com.smassistant.app.history.PublishedPost
import com.smassistant.app.media.isVideo
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A post saved to finish later (improvement #8): the final [text] as it would post (hashtags
 * included) and the paths of its [media], copied out of the cache so they don't expire.
 */
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val savedAt: Long,
    val title: String,
    val text: String,
    val media: List<String> = emptyList(),
) {
    val files get() = media.map(::File)

    /** "Text", "2 images" or "Video", for the Drafts list. */
    val mediaLabel get() = when {
        media.isEmpty() -> "Text"
        files.any(::isVideo) -> "Video"
        media.size == 1 -> "1 image"
        else -> "${media.size} images"
    }
}

/** JSON for [DraftStore]; entries that don't parse are skipped. */
object DraftJson {
    fun encode(list: List<Draft>): String = buildJsonArray {
        list.forEach { d ->
            add(
                buildJsonObject {
                    put("id", d.id)
                    put("savedAt", d.savedAt)
                    put("title", d.title)
                    put("text", d.text)
                    put("media", buildJsonArray { d.media.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
    }.toString()

    fun decode(text: String?): List<Draft> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { runCatching { fromJson(it.jsonObject) }.getOrNull() }
    }

    private fun fromJson(json: JsonObject) = Draft(
        id = json.getValue("id").jsonPrimitive.content,
        savedAt = json.getValue("savedAt").jsonPrimitive.long,
        title = json.getValue("title").jsonPrimitive.content,
        text = json.getValue("text").jsonPrimitive.content,
        media = json["media"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
    )
}

/** Where the posting flow saves drafts; [DraftStore] in the app, a fake in tests. */
interface DraftSaver {
    fun save(text: String, files: List<File>, id: String? = null, title: String? = null): Draft
    fun delete(id: String)
}

/**
 * Saved drafts, newest first, as one JSON list in SharedPreferences, with their media in
 * `filesDir/drafts/` (not the cache, which is cleared after a week). Deleting a draft deletes its media.
 */
class DraftStore(
    private val prefs: android.content.SharedPreferences,
    private val mediaDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : DraftSaver {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("drafts", Context.MODE_PRIVATE),
        File(context.applicationContext.filesDir, "drafts"),
    )

    fun all(): List<Draft> = synchronized(LOCK) { DraftJson.decode(prefs.getString(KEY_LIST, null)) }

    fun get(id: String): Draft? = all().firstOrNull { it.id == id }

    /**
     * Saves [text] with [files] as a new draft, or over draft [id] when it still exists; returns it.
     * Files from the cache are copied in; ones the draft already holds stay; ones it no longer has are deleted.
     */
    override fun save(text: String, files: List<File>, id: String?, title: String?): Draft = synchronized(LOCK) {
        val list = all()
        val old = list.firstOrNull { it.id == id }
        val draftId = old?.id ?: UUID.randomUUID().toString()
        val dir = File(mediaDir, draftId).apply { mkdirs() }
        val kept = files.map { file ->
            if (file.parentFile?.canonicalPath == dir.canonicalPath) {
                file
            } else {
                File(dir, "${UUID.randomUUID()}.${file.extension}").also { file.copyTo(it) }
            }
        }
        old?.files?.filter { it.path !in kept.map(File::getPath) }?.forEach { it.delete() }
        val draft = Draft(
            id = draftId,
            savedAt = now(),
            title = title?.takeIf { it.isNotBlank() } ?: when {
                text.isNotBlank() -> PublishedPost.titleOf(text)
                kept.any(::isVideo) -> "Video draft"
                else -> "Image draft"
            },
            text = text,
            media = kept.map { it.path },
        )
        write(listOf(draft) + list.filterNot { it.id == draftId })
        draft
    }

    override fun delete(id: String): Unit = synchronized(LOCK) {
        File(mediaDir, id).deleteRecursively()
        write(all().filterNot { it.id == id })
    }

    private fun write(list: List<Draft>) {
        prefs.edit().putString(KEY_LIST, DraftJson.encode(list)).commit()
    }

    private companion object {
        const val KEY_LIST = "list"
        val LOCK = Any()
    }
}

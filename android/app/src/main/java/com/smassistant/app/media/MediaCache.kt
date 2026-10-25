package com.smassistant.app.media

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Generated images (and later videos) live in `cacheDir/generated/` (docs/tasks GEN-I4,
 * interim answer to Q-13): the system may clear them, and files older than a week are
 * removed when the app starts.
 */
class MediaCache(private val dir: File) {
    constructor(context: Context) : this(File(context.cacheDir, "generated"))

    /** Writes [bytes] to a new file with [extension] and returns it. */
    fun save(bytes: ByteArray, extension: String): File {
        dir.mkdirs()
        return File(dir, "${UUID.randomUUID()}.$extension").apply { writeBytes(bytes) }
    }

    fun clearOlderThan(maxAgeMillis: Long = TimeUnit.DAYS.toMillis(7), now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.filter { now - it.lastModified() > maxAgeMillis }?.forEach { it.delete() }
    }
}

/** File extension for an image MIME type, defaulting to png. */
fun extensionFor(mimeType: String) = when (mimeType.lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/webp" -> "webp"
    else -> "png"
}

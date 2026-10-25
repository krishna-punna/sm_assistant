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

    /** A new, empty path in the cache for a file the caller writes, e.g. an encoded video. */
    fun newFile(extension: String): File {
        dir.mkdirs()
        return File(dir, "${UUID.randomUUID()}.$extension")
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

/** MIME type of a cached image, from the extension [MediaCache] gave it. */
fun imageMimeType(file: File) = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    else -> "image/png"
}

/** Generated videos are the `.mp4` files the slideshow maker writes; everything else is an image. */
fun isVideo(file: File) = file.extension.equals("mp4", ignoreCase = true)

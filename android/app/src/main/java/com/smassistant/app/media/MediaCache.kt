package com.smassistant.app.media

import android.content.Context
import com.smassistant.app.data.MediaPurpose
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Generated files live in `cacheDir/generated/` (docs/tasks GEN-I4, interim answer to Q-13): images
 * in `generated/images/` and videos with their slides in `generated/videos/` (SP-16). The system may
 * clear them, and files older than a week are removed when the app starts.
 */
class MediaCache(private val dir: File) {
    /** The whole cache, for clearing old files; [purpose] picks the images or the videos folder. */
    constructor(context: Context, purpose: MediaPurpose? = null) : this(cacheFolder(context.cacheDir, purpose))

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
        dir.walkBottomUp().filter { it.isFile && now - it.lastModified() > maxAgeMillis }.forEach { it.delete() }
    }
}

/** `generated/` under [cacheDir], or its `images/` or `videos/` folder for [purpose]. */
fun cacheFolder(cacheDir: File, purpose: MediaPurpose?): File {
    val base = File(cacheDir, "generated")
    return when (purpose) {
        null -> base
        MediaPurpose.IMAGE -> File(base, "images")
        MediaPurpose.VIDEO -> File(base, "videos")
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

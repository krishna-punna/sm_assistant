package com.smassistant.app.media

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Folder generated media is saved under, inside Pictures/ or Movies/. */
const val GALLERY_FOLDER = "SM Assistant"

/** Where Save to Gallery puts [file]: its name, MIME type and folder under shared storage. */
data class GalleryEntry(val displayName: String, val mimeType: String, val directory: String) {
    val relativePath get() = "$directory/$GALLERY_FOLDER"
}

/**
 * Names a generated file for the gallery ("SM Assistant 2026-10-31 0915 2.png"): the cache
 * names are random UUIDs, which mean nothing in a photo app. [index] tells files saved together apart.
 */
fun galleryEntry(file: File, savedAt: Date, index: Int = 0): GalleryEntry {
    val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(savedAt)
    val suffix = if (index > 0) " ${index + 1}" else ""
    val extension = file.extension.lowercase().ifEmpty { "png" }
    return if (isVideo(file)) {
        GalleryEntry("$GALLERY_FOLDER $stamp$suffix.$extension", "video/mp4", Environment.DIRECTORY_MOVIES)
    } else {
        GalleryEntry("$GALLERY_FOLDER $stamp$suffix.$extension", imageMimeType(file), Environment.DIRECTORY_PICTURES)
    }
}

/** MIME type for a share of [files]: the exact type when they match, else image wildcard or any. */
fun shareMimeType(files: List<File>): String {
    val types = files.map { if (isVideo(it)) "video/mp4" else imageMimeType(it) }.distinct()
    return when {
        types.size == 1 -> types.single()
        types.all { it.startsWith("image/") } -> "image/*"
        else -> "*/*"
    }
}

/** Save to Gallery needs the storage permission only before Android 10 (scoped storage). */
fun gallerySaveNeedsPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

/** Share (system sheet) and Save to Gallery for generated images and videos, which otherwise expire from the cache. */
object MediaExport {
    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** A chooser that sends [files] to any app that takes them (messaging, Drive, other networks). */
    fun shareIntent(context: Context, files: List<File>): Intent {
        val uris = files.map { uriFor(context, it) }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = shareMimeType(files)
        // ClipData carries the read grant to the app the person picks.
        send.clipData = ClipData.newUri(context.contentResolver, "media", uris.first()).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, if (files.size == 1) "Share" else "Share ${files.size} files")
    }

    /** Copies [files] into Pictures/ or Movies/SM Assistant; returns how many were saved. Call off the main thread. */
    fun saveToGallery(context: Context, files: List<File>, now: Date = Date()): Int =
        files.withIndex().count { (index, file) ->
            runCatching { save(context, file, galleryEntry(file, now, index)) }.isSuccess
        }

    private fun save(context: Context, file: File, entry: GalleryEntry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo(file)) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, entry.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, entry.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, entry.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = checkNotNull(resolver.insert(collection, values)) { "MediaStore refused ${entry.displayName}" }
            try {
                resolver.openOutputStream(uri).use { out -> file.inputStream().use { it.copyTo(checkNotNull(out)) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(entry.directory), GALLERY_FOLDER).apply { mkdirs() }
            val target = File(dir, entry.displayName)
            file.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(entry.mimeType), null)
        }
    }
}

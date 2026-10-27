package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.smassistant.app.infographic.ImageFacts
import com.smassistant.app.infographic.ImageInspector
import com.smassistant.app.infographic.imageFacts
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Longest side of the JPEG a vision model is shown; Claude and Gemini scale larger images down to about this. */
private const val REVIEW_SIDE = 1568

/** About how many pixels across the inspector reads, which is enough to tell a blank image. */
private const val SAMPLE_SIDE = 96

/** Reads [ImageFacts] from a small sample of the image, so even a 2160x8000 PNG is cheap to check. */
object AndroidImageInspector : ImageInspector {
    override suspend fun facts(file: File): ImageFacts? = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SAMPLE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            imageFacts(bounds.outWidth, bounds.outHeight, pixels)
        } finally {
            bitmap.recycle()
        }
    }
}

/** The image as a JPEG no larger than [REVIEW_SIDE] on its long side, to send to a vision model. Null if it can't be read. */
suspend fun reviewJpeg(file: File): ByteArray? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= REVIEW_SIDE) sample *= 2
    val decoded = try {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: OutOfMemoryError) {
        null
    } ?: return@withContext null
    val scale = REVIEW_SIDE.toFloat() / max(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
            .also { if (it !== decoded) decoded.recycle() }
    } else {
        decoded
    }
    try {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

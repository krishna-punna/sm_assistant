package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import com.smassistant.app.data.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/** How far a result's ratio may be from its size before it is padded (docs/requirements/18, SV-9). */
const val SIZE_TOLERANCE = 0.02f

/** The ratio of each image size, width to height. */
val ImageFormat.ratio: Pair<Int, Int>
    get() = when (this) {
        ImageFormat.PORTRAIT -> 2 to 3
        ImageFormat.SQUARE -> 1 to 1
        ImageFormat.LANDSCAPE -> 3 to 2
    }

/** The image size closest in shape to [width] x [height]: what an illustration slot or a video frame is painted at. */
fun nearestImageFormat(width: Float, height: Float): ImageFormat {
    if (width <= 0f || height <= 0f) return ImageFormat.SQUARE
    val shape = ln(width / height)
    return ImageFormat.entries.minBy { abs(ln(it.ratio.first.toFloat() / it.ratio.second) - shape) }
}

/**
 * The canvas [width] x [height] is padded to so it has the closest of [ratios], keeping every
 * pixel; null when it is already within [SIZE_TOLERANCE] of one of them.
 */
fun paddedCanvas(width: Int, height: Int, ratios: List<Pair<Int, Int>>): Pair<Int, Int>? {
    if (width <= 0 || height <= 0 || ratios.isEmpty()) return null
    val shape = width.toFloat() / height
    if (ratios.any { (w, h) -> abs(shape / (w.toFloat() / h) - 1f) <= SIZE_TOLERANCE }) return null
    val (w, h) = ratios.minBy { (w, h) -> abs(ln(shape / (w.toFloat() / h))) }
    val target = w.toFloat() / h
    return if (shape > target) width to (width / target).roundToInt() else (height * target).roundToInt() to height
}

/** Pads a finished image or slide to one of the allowed ratios, in place; returns the file. */
fun interface SizeGuard {
    suspend fun fit(file: File, ratios: List<Pair<Int, Int>>): File
}

/** [SizeGuard] on the device: centres the picture on a canvas filled with its corner colour and logs each fix. */
object AndroidSizeGuard : SizeGuard {
    override suspend fun fit(file: File, ratios: List<Pair<Int, Int>>): File = withContext(Dispatchers.Default) {
        val (width, height) = imagePixels(file) ?: return@withContext file
        val (canvasWidth, canvasHeight) = paddedCanvas(width, height, ratios) ?: return@withContext file
        try {
            val picture = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext file
            val padded = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            Canvas(padded).apply {
                drawColor(picture.getPixel(0, 0))
                drawBitmap(picture, (canvasWidth - width) / 2f, (canvasHeight - height) / 2f, null)
            }
            picture.recycle()
            val jpeg = file.extension.lowercase() in setOf("jpg", "jpeg")
            try {
                file.outputStream().use { padded.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (jpeg) 92 else 100, it) }
            } finally {
                padded.recycle()
            }
            Log.i("SizeGuard", "${file.name}: ${sizeText(width, height)} padded to ${sizeText(canvasWidth, canvasHeight)}")
            file
        } catch (e: OutOfMemoryError) {
            file
        }
    }
}

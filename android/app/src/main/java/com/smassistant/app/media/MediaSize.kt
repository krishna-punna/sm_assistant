package com.smassistant.app.media

import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs

/** The ratios a result may have: the image sizes (2:3, 1:1, 3:2) and the video sizes (9:16, 16:9). */
private val NAMED_RATIOS = listOf(2 to 3, 1 to 1, 3 to 2, 9 to 16, 16 to 9)

/**
 * "2:3 · 2160×3240": the named ratio within 2% of [width] x [height], then the pixels; only the
 * pixels for any other shape, so a wrong size shows on the result (docs/requirements/18, SV-10).
 */
fun sizeText(width: Int, height: Int): String {
    val pixels = "$width×$height"
    if (width <= 0 || height <= 0) return pixels
    val ratio = width.toFloat() / height
    val named = NAMED_RATIOS.firstOrNull { (w, h) -> abs(ratio / (w.toFloat() / h) - 1f) <= 0.02f }
    return named?.let { (w, h) -> "$w:$h · $pixels" } ?: pixels
}

/** The width and height of the image in [file], read without decoding its pixels; null when it isn't an image. */
fun imagePixels(file: File): Pair<Int, Int>? = if (!file.isFile) null else runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    (bounds.outWidth to bounds.outHeight).takeIf { (w, h) -> w > 0 && h > 0 }
}.getOrNull()

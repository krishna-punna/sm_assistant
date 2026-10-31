package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Makes one frame of a fade between two slides (docs/tasks VT-I6): [from] with [to] laid over it
 * at [amount] (0 is all [from], 1 all [to]), both fitted into a [width] x [height] frame as the
 * video shows them. Null when a slide can't be read.
 */
fun interface FrameBlender {
    suspend fun blend(from: File, to: File, amount: Float, width: Int, height: Int): File?
}

/** Blends on the device with a Canvas and saves the frame as a PNG in [cache]. */
class AndroidFrameBlender(private val cache: MediaCache) : FrameBlender {
    override suspend fun blend(from: File, to: File, amount: Float, width: Int, height: Int): File? = withContext(Dispatchers.Default) {
        val first = BitmapFactory.decodeFile(from.absolutePath) ?: return@withContext null
        val second = BitmapFactory.decodeFile(to.absolutePath) ?: run {
            first.recycle()
            return@withContext null
        }
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(frame)
            // Black like the bars Media3 adds around a slide that doesn't fill the frame.
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(first, null, fit(first, width, height), paint)
            paint.alpha = (amount.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawBitmap(second, null, fit(second, width, height), paint)
            val bytes = ByteArrayOutputStream().also { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            cache.save(bytes, "png")
        } finally {
            first.recycle()
            second.recycle()
            frame.recycle()
        }
    }

    private fun fit(bitmap: Bitmap, width: Int, height: Int): RectF {
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        return RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }
}

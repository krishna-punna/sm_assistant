package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.smassistant.app.infographic.FontFamily
import com.smassistant.app.infographic.IllustrationSlot
import com.smassistant.app.infographic.InfographicComposer
import com.smassistant.app.infographic.RenderedInfographic
import com.smassistant.app.infographic.TextMeasurer
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Infographics are drawn at twice their layout size: 2160 px wide for portrait and square. */
const val INFOGRAPHIC_SCALE = 2f

/** Tallest infographic PNG, so a very long one can't exhaust memory. */
private const val INFOGRAPHIC_MAX_HEIGHT = 8000

/**
 * Merges the two branches of the pipeline into the final PNG: AndroidSVG draws the rendered SVG
 * at [scale], then each illustration is centre-cropped into its slot, clipped to the slot's
 * rounded corners. Saved in [cache]. Falls back to 1x if the device runs short of memory.
 */
class AndroidInfographicComposer(
    private val cache: MediaCache,
    private val scale: Float = INFOGRAPHIC_SCALE,
) : InfographicComposer {
    override suspend fun compose(rendered: RenderedInfographic, illustrations: List<File?>): File? = withContext(Dispatchers.Default) {
        val document = try {
            SVG.getFromString(rendered.svg)
        } catch (e: SVGParseException) {
            return@withContext null
        }
        val fitted = min(scale, INFOGRAPHIC_MAX_HEIGHT.toFloat() / rendered.height)
        val png = try {
            draw(document, rendered, illustrations, fitted)
        } catch (e: OutOfMemoryError) {
            if (fitted > 1f) draw(document, rendered, illustrations, 1f) else null
        }
        png?.let { cache.save(it, "png") }
    }

    private fun draw(document: SVG, rendered: RenderedInfographic, illustrations: List<File?>, scale: Float): ByteArray? {
        val width = (rendered.width * scale).roundToInt()
        val height = (rendered.height * scale).roundToInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            document.setDocumentViewBox(0f, 0f, rendered.width.toFloat(), rendered.height.toFloat())
            fillCanvas(document, width, height)
            document.renderToCanvas(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
            rendered.slots.zip(illustrations).forEach { (slot, file) -> file?.let { paint(canvas, slot, it, scale) } }
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun paint(canvas: Canvas, slot: IllustrationSlot, file: File, scale: Float) {
        val target = RectF(slot.x * scale, slot.y * scale, (slot.x + slot.width) * scale, (slot.y + slot.height) * scale)
        val image = decodeAtLeast(file, target.width().roundToInt(), target.height().roundToInt()) ?: return
        try {
            // Centre-crop: the largest part of the image with the slot's aspect ratio.
            val ratio = target.width() / target.height()
            val cropWidth = min(image.width.toFloat(), image.height * ratio)
            val cropHeight = cropWidth / ratio
            val left = ((image.width - cropWidth) / 2).roundToInt()
            val top = ((image.height - cropHeight) / 2).roundToInt()
            val source = Rect(left, top, left + cropWidth.roundToInt(), top + cropHeight.roundToInt())
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(target, slot.radius * scale, slot.radius * scale, Path.Direction.CW) })
            canvas.drawBitmap(image, source, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } finally {
            image.recycle()
        }
    }

    /** Decodes [file], sampled down only as far as still covers [width] x [height]. Null if it isn't an image. */
    private fun decodeAtLeast(file: File, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/**
 * Sizes the SVG's root to the whole [width] x [height] bitmap. AndroidSVG draws the root at its
 * own width and height attributes, not the viewport passed to renderToCanvas, so a 1080 px wide
 * document drawn into a 2160 px bitmap filled only its top-left quarter.
 */
internal fun fillCanvas(document: SVG, width: Int, height: Int) {
    document.setDocumentWidth(width.toFloat())
    document.setDocumentHeight(height.toFloat())
}

/**
 * Measures with the fonts AndroidSVG draws with (the system sans-serif and monospace), plus a 3%
 * margin, so wrapped lines fit on the device exactly.
 */
object PaintTextMeasurer : TextMeasurer {
    private val paints = mutableMapOf<Pair<Boolean, FontFamily>, Paint>()

    @Synchronized
    override fun width(text: String, size: Float, bold: Boolean, family: FontFamily): Float {
        val paint = paints.getOrPut(bold to family) {
            val base = if (family == FontFamily.MONO) Typeface.MONOSPACE else Typeface.SANS_SERIF
            Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL) }
        }
        paint.textSize = size
        return paint.measureText(text) * 1.03f
    }
}

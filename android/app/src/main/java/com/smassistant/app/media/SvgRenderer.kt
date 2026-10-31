package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import com.smassistant.app.image.render.fillCanvas
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Draws SVG markup to PNG bytes, or null when the markup can't be drawn. */
fun interface SvgRenderer {
    fun render(svg: String): ByteArray?
}

/** Width of PNGs drawn from SVG: LinkedIn's recommended image width. */
const val SVG_PNG_WIDTH = 1080

/** Tallest PNG we draw, so a runaway viewBox can't exhaust memory. */
private const val SVG_PNG_MAX_HEIGHT = 4000

/**
 * Draws SVG with AndroidSVG at [width] px wide on white, keeping the drawing's aspect ratio
 * (from its viewBox, else its width and height, else 4:5).
 */
class AndroidSvgRenderer(private val width: Int = SVG_PNG_WIDTH) : SvgRenderer {
    override fun render(svg: String): ByteArray? = try {
        val document = SVG.getFromString(svg)
        val box = document.documentViewBox
        val (svgWidth, svgHeight) = when {
            box != null && box.width() > 0 && box.height() > 0 -> box.width() to box.height()
            document.documentWidth > 0 && document.documentHeight > 0 -> document.documentWidth to document.documentHeight
            else -> 1080f to 1350f
        }
        if (box == null) document.setDocumentViewBox(0f, 0f, svgWidth, svgHeight)
        val height = (width * svgHeight / svgWidth).roundToInt().coerceIn(1, SVG_PNG_MAX_HEIGHT)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            fillCanvas(document, width, height)
            document.renderToCanvas(this, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    } catch (e: SVGParseException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

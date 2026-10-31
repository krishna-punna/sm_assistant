package com.smassistant.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes a carousel's pages, in order, into one PDF named [title] (docs/design/single-image-carousel.md, SC-I6). */
fun interface CarouselPdfMaker {
    suspend fun make(pages: List<File>, title: String): File?
}

/** PDF page side in points: LinkedIn shows a square document page at this size, and the 2x pages keep their pixels. */
private const val PDF_PAGE_POINTS = 1080

/**
 * Android's [PdfDocument]: one square page per image, each drawn to fill its page. The file is
 * named after the title, since LinkedIn needs a document title, in a folder of its own in the
 * cache so two carousels with one title don't clash. Null when no page could be read or written.
 */
class AndroidCarouselPdfMaker(private val dir: File) : CarouselPdfMaker {
    constructor(context: Context) : this(File(context.cacheDir, "generated"))

    override suspend fun make(pages: List<File>, title: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val document = PdfDocument()
            try {
                var drawn = 0
                pages.forEach { file ->
                    val bitmap = BitmapFactory.decodeFile(file.path) ?: return@forEach
                    val page = document.startPage(PdfDocument.PageInfo.Builder(PDF_PAGE_POINTS, PDF_PAGE_POINTS, drawn + 1).create())
                    val side = PDF_PAGE_POINTS.toFloat()
                    page.canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), RectF(0f, 0f, side, side), null)
                    document.finishPage(page)
                    bitmap.recycle()
                    drawn++
                }
                if (drawn == 0) return@runCatching null
                val folder = File(dir, "pdf-${System.currentTimeMillis()}").apply { mkdirs() }
                File(folder, "${pdfName(title)}.pdf").also { out -> out.outputStream().use(document::writeTo) }
            } finally {
                document.close()
            }
        }.getOrNull()
    }
}

/** [title] as a file name: no path or reserved characters, at most 80 characters, "Carousel" when nothing is left. */
fun pdfName(title: String): String =
    title.replace(Regex("""[\\/:*?"<>|\n\r\t]+"""), " ").replace(Regex("""\s+"""), " ").trim().take(80).trim().ifEmpty { "Carousel" }

/** Generated documents are the `.pdf` files [CarouselPdfMaker] writes; LinkedIn posts them as documents. */
fun isDocument(file: File) = file.extension.equals("pdf", ignoreCase = true)

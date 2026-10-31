package com.smassistant.app.media

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SC-I6: a carousel's PDF is named after its document title, and posted as a document. */
class CarouselPdfTest {
    @Test
    fun `the PDF name is the title without path or reserved characters`() {
        assertEquals("Caching Read path Write path", pdfName("Caching: Read/path\\nWrite path".replace("\\n", "\n")))
        assertEquals("Carousel", pdfName("  /?*  "))
        assertEquals(80, pdfName("x".repeat(200)).length)
    }

    @Test
    fun `only PDFs are documents`() {
        assertTrue(isDocument(File("Caching.pdf")))
        assertFalse(isDocument(File("page.png")))
        assertFalse(isDocument(File("video.mp4")))
    }
}

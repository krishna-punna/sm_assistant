package com.smassistant.app.media

import com.smassistant.app.data.ImageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The final size guard (docs/requirements/18, SV-9) and the size an illustration or video frame is painted at (SV-8). */
class SizeGuardTest {
    private val imageRatios = ImageSize.entries.map { it.ratio }

    @Test
    fun `a result within 2 percent of an allowed ratio is left alone`() {
        assertNull(paddedCanvas(2160, 3240, imageRatios))
        assertNull(paddedCanvas(832, 1248, imageRatios))
        assertNull(paddedCanvas(1080, 1920, listOf(1080 to 1920)))
        assertNull(paddedCanvas(1000, 1010, imageRatios))
    }

    @Test
    fun `a 9 by 16 picture through the image guard comes out 2 by 3`() {
        assertEquals(1280 to 1920, paddedCanvas(1080, 1920, imageRatios))
    }

    @Test
    fun `a 2 by 3 picture through the video guard comes out 9 by 16`() {
        assertEquals(1024 to 1820, paddedCanvas(1024, 1536, listOf(1080 to 1920)))
        // A square painted slide is padded top and bottom, never stretched.
        assertEquals(1024 to 1820, paddedCanvas(1024, 1024, listOf(1080 to 1920)))
    }

    @Test
    fun `a wide picture is padded to 3 by 2`() {
        assertEquals(1920 to 1280, paddedCanvas(1920, 1080, imageRatios))
    }

    @Test
    fun `each shape is painted at the closest image size`() {
        assertEquals(ImageSize.PORTRAIT, nearestImageFormat(1080f, 1920f))
        assertEquals(ImageSize.LANDSCAPE, nearestImageFormat(1920f, 1080f))
        assertEquals(ImageSize.SQUARE, nearestImageFormat(1080f, 1080f))
        assertEquals(ImageSize.SQUARE, nearestImageFormat(300f, 280f))
        assertEquals(ImageSize.LANDSCAPE, nearestImageFormat(600f, 300f))
        assertEquals(ImageSize.SQUARE, nearestImageFormat(0f, 100f))
    }
}

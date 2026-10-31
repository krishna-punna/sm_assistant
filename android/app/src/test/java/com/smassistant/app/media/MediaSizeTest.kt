package com.smassistant.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSizeTest {
    @Test
    fun `names the image and video ratios with their pixels`() {
        assertEquals("2:3 · 2160×3240", sizeText(2160, 3240))
        assertEquals("1:1 · 2160×2160", sizeText(2160, 2160))
        assertEquals("3:2 · 3240×2160", sizeText(3240, 2160))
        assertEquals("9:16 · 1080×1920", sizeText(1080, 1920))
        assertEquals("16:9 · 1920×1080", sizeText(1920, 1080))
        // Gemini's 2:3 is not exact.
        assertEquals("2:3 · 832×1248", sizeText(832, 1248))
    }

    @Test
    fun `any other shape shows only its pixels`() {
        assertEquals("1080×1350", sizeText(1080, 1350))
        assertEquals("0×0", sizeText(0, 0))
    }
}

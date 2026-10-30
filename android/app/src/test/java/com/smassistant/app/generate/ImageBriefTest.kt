package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
import com.smassistant.app.data.ImageStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageBriefTest {
    @Test
    fun `fills the template with the content, format words and style inputs`() {
        val template = ImageTemplate("t", "T", "", "{{content}} | {{format}} | {{audience}} | {{style}} | {{brand}}")
        val brief = ImageBrief(template, "Kafka", ImageFormat.LANDSCAPE, ImageStyle(audience = "students", brandName = "Acme"))
        assertEquals("Kafka | landscape 3:2 | students | not specified | Acme", brief.filled())
    }

    @Test
    fun `format keys read any case and auto is null`() {
        assertEquals(ImageFormat.SQUARE, ImageFormat.of(" Square "))
        assertEquals(null, ImageFormat.of("auto"))
        assertEquals(listOf("portrait 2:3", "square 1:1", "landscape 3:2"), ImageFormat.entries.map { it.words })
    }
}

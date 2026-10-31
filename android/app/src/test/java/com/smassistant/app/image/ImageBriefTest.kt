package com.smassistant.app.image

import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageBriefTest {
    @Test
    fun `fills the template with the content, format words and style inputs`() {
        val template = ImageTemplate("t", "T", "", "{{content}} | {{format}} | {{audience}} | {{style}} | {{brand}}")
        val brief = ImageBrief(template, "Kafka", ImageSize.LANDSCAPE, ImageStyle(audience = "students", brandName = "Acme"))
        assertEquals("Kafka | landscape 3:2 | students | not specified | Acme", brief.filled())
    }

    @Test
    fun `format keys read any case and auto is null`() {
        assertEquals(ImageSize.SQUARE, ImageSize.of(" Square "))
        assertEquals(null, ImageSize.of("auto"))
        assertEquals(listOf("portrait 2:3", "square 1:1", "landscape 3:2"), ImageSize.entries.map { it.words })
    }
}

package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
import com.smassistant.app.infographic.InfographicFormat
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.Section
import com.smassistant.app.infographic.SectionBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One image or more (docs/design/single-image-carousel.md): the fit check and the landscape retry. */
class ImageLayoutTest {
    private val template = ImageTemplate("workflow", "Workflow", "use", "{{content}}")
    private fun brief(fixed: Boolean = false) = ImageBrief(template, "content", ImageFormat.PORTRAIT, fixedFormat = fixed)
    private val portrait = InfographicSpec("Caching", format = InfographicFormat.PORTRAIT)

    @Test
    fun `a definition that fits keeps its format`() {
        assertEquals(portrait, fitFormat(portrait, brief()) { true })
    }

    @Test
    fun `a portrait too long for its format is drawn as landscape when that fits`() {
        val fitted = fitFormat(portrait, brief()) { it.format == InfographicFormat.LANDSCAPE }
        assertEquals(InfographicFormat.LANDSCAPE, fitted.format)
        assertEquals(ImageFormat.LANDSCAPE, fitted.format.image)
    }

    @Test
    fun `too long for landscape too keeps the planned format`() {
        assertEquals(portrait, fitFormat(portrait, brief()) { false })
    }

    @Test
    fun `a picked format is never switched, nor a definition without a brief`() {
        assertEquals(portrait, fitFormat(portrait, brief(fixed = true)) { it.format == InfographicFormat.LANDSCAPE })
        assertEquals(portrait, fitFormat(portrait, null) { it.format == InfographicFormat.LANDSCAPE })
    }

    @Test
    fun `the renderer says whether a definition fits at full text size`() {
        val renderer = InfographicRenderer()
        fun spec(sections: Int) = portrait.copy(
            sections = List(sections) { Section("Part $it", SectionBody.Bullets(List(5) { j -> "point $j about caching entries and their expiry" })) },
        )
        assertTrue(renderer.fits(spec(3)))
        assertFalse(renderer.fits(spec(12)))
    }
}

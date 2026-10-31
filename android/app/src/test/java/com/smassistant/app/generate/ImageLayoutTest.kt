package com.smassistant.app.generate

import com.smassistant.app.infographic.InfographicFormat
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.Section
import com.smassistant.app.infographic.SectionBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fit check that decides when a poster is condensed (docs/requirements/18, SV-5). */
class ImageLayoutTest {
    private val portrait = InfographicSpec("Caching", format = InfographicFormat.PORTRAIT)

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

package com.smassistant.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.smassistant.app.infographic.ApproxTextMeasurer
import com.smassistant.app.infographic.InfographicFormat
import com.smassistant.app.infographic.InfographicRenderer
import com.smassistant.app.infographic.InfographicSpec
import com.smassistant.app.infographic.Section
import com.smassistant.app.infographic.SectionBody
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Draws real pixels (native graphics) to check the drawing covers the whole PNG. Posted images
 * used to show the infographic in the top-left quarter with the rest blank.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfographicFillTest {
    private val cache = MediaCache(Files.createTempDirectory("fill").toFile())

    private val spec = InfographicSpec(
        title = "ConcurrentHashMap Internals",
        highlight = "Internals",
        subtitle = "How Java scales a map across threads",
        hero = null,
        sections = listOf(
            Section("What it is", SectionBody.Bullets(listOf("Thread-safe map with lock striping", "Lock-free reads"))),
            Section("Put flow", SectionBody.Steps(listOf("Hash the key", "CAS into an empty bin", "Else lock the bin"))),
            Section("Code", SectionBody.Code("map.computeIfAbsent(key, k -> load(k));")),
        ),
        takeaway = "Size it up front and use computeIfAbsent.",
    )

    @Test
    fun `infographic drawn at 2x fills the whole PNG`() = runBlocking {
        for (format in InfographicFormat.entries) {
            val rendered = InfographicRenderer(ApproxTextMeasurer).render(spec.copy(format = format))
            val file = AndroidInfographicComposer(cache, scale = 2f).compose(rendered, emptyList())
            assertNotNull("$format: no PNG", file)
            val png = BitmapFactory.decodeFile(file!!.absolutePath)

            assertEquals(rendered.width * 2, png.width)
            assertEquals(rendered.height * 2, png.height)
            assertFilled(png, "$format")
            // The header band runs edge to edge, so its colour must reach the top-right corner.
            val header = Regex("""<rect x="0" y="0" width="[\d.]+" height="[\d.]+" fill="(#[0-9A-Fa-f]{6})"""")
                .findAll(rendered.svg).elementAt(1).groupValues[1]
            assertEquals("$format top-right", Color.parseColor(header), png.getPixel(png.width - 4, 4))
        }
    }

    @Test
    fun `SVG with its own width and height is scaled to the full PNG`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="400" height="500" viewBox="0 0 400 500">""" +
            """<rect x="0" y="0" width="400" height="500" fill="#FF0000"/></svg>"""
        val bytes = AndroidSvgRenderer(1080).render(svg)
        assertNotNull(bytes)
        val png = BitmapFactory.decodeByteArray(bytes!!, 0, bytes.size)

        assertEquals(1080, png.width)
        assertEquals(1350, png.height)
        assertEquals(Color.RED, png.getPixel(png.width - 4, png.height - 4))
    }

    /** Every point on a 10 x 10 grid is opaque: nothing is left undrawn. */
    private fun assertFilled(png: Bitmap, label: String) {
        for (i in 0 until 10) for (j in 0 until 10) {
            val x = (png.width - 1) * i / 9
            val y = (png.height - 1) * j / 9
            assertTrue("$label: blank at ($x, $y) of ${png.width}x${png.height}", Color.alpha(png.getPixel(x, y)) == 255)
        }
    }
}

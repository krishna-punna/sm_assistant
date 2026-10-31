package com.smassistant.app.image

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesFilesTest {
    @Test
    fun `series parts are labelled in order and read back, including results saved as carousel pages`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val labelled = labelSeriesPart(File(dir, "poster.png").apply { writeText("x") }, 42, 3)
        assertEquals("series-42-p03-poster.png", labelled.name)
        assertEquals(SeriesPart(42, 3), seriesPart(labelled))
        assertEquals(SeriesPart(9, 2), seriesPart(File("carousel-9-p02-page.png")))
        assertNull(seriesPart(File("tpl-workflow-portrait-a.png")))
    }

    @Test
    fun `only two or more parts of one series count as a series`() {
        assertTrue(isSeries((1..2).map { File("series-5-p0$it-x.png") }))
        assertFalse(isSeries(listOf(File("series-5-p01-x.png"))))
        assertFalse(isSeries(listOf(File("series-5-p01-x.png"), File("series-6-p02-x.png"))))
        assertFalse(isSeries(listOf(File("a.png"), File("b.png"))))
    }
}

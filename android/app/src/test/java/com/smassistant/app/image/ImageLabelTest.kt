package com.smassistant.app.image

import com.smassistant.app.data.ImageSize
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLabelTest {
    @Test
    fun `a labelled file keeps its template and format in its name`() {
        val dir = Files.createTempDirectory("labels").toFile()
        val file = File(dir, "infographic-1.png").apply { writeText("png") }

        val labelled = labelImage(file, "workflow", ImageSize.LANDSCAPE)

        assertEquals("tpl-workflow-landscape-infographic-1.png", labelled.name)
        assertTrue(labelled.exists())
        assertFalse(file.exists())
        assertEquals(ImageLabel("workflow", ImageSize.LANDSCAPE), imageLabel(labelled))
        dir.deleteRecursively()
    }

    @Test
    fun `other files have no label and a file that can't be renamed is kept`() {
        assertEquals(null, imageLabel(File("infographic-1.png")))
        assertEquals(null, imageLabel(File("tpl-workflow-tall-x.png")))
        assertEquals(File("missing.png"), labelImage(File("missing.png"), "code", ImageSize.SQUARE))
    }

    @Test
    fun `the label reads the template's name and the ratio`() {
        val templates = listOf(ImageTemplate("workflow", "Workflow", "", ""))
        assertEquals("Workflow · 2:3", ImageLabel("workflow", ImageSize.PORTRAIT).text(templates))
        assertEquals("Data · 3:2", ImageLabel("data", ImageSize.LANDSCAPE).text(templates))
        // The real size, when known, replaces the planned ratio.
        assertEquals("Workflow · 1:1 · 2160×2160", ImageLabel("workflow", ImageSize.PORTRAIT).text(templates, "1:1 · 2160×2160"))
        assertEquals("Workflow · 2:3 · 1664×2496 · AI", ImageLabel("workflow", ImageSize.PORTRAIT, painted = true).text(templates, "2:3 · 1664×2496"))
    }

    @Test
    fun `a painted image's label says it is AI-painted`() {
        val dir = Files.createTempDirectory("labels").toFile()
        val file = File(dir, "a.png").apply { writeText("png") }

        val labelled = labelImage(file, "workflow", ImageSize.PORTRAIT, painted = true)

        assertEquals("tpl-workflow-portrait-painted-a.png", labelled.name)
        assertEquals(ImageLabel("workflow", ImageSize.PORTRAIT, painted = true), imageLabel(labelled))
        assertEquals("Workflow · 2:3 · AI", imageLabel(labelled)!!.text(listOf(ImageTemplate("workflow", "Workflow", "", ""))))
        dir.deleteRecursively()
    }
}

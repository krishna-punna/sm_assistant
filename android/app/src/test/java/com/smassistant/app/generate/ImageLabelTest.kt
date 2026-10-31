package com.smassistant.app.generate

import com.smassistant.app.data.ImageFormat
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

        val labelled = labelImage(file, "workflow", ImageFormat.LANDSCAPE)

        assertEquals("tpl-workflow-landscape-infographic-1.png", labelled.name)
        assertTrue(labelled.exists())
        assertFalse(file.exists())
        assertEquals(ImageLabel("workflow", ImageFormat.LANDSCAPE), imageLabel(labelled))
        dir.deleteRecursively()
    }

    @Test
    fun `other files have no label and a file that can't be renamed is kept`() {
        assertEquals(null, imageLabel(File("infographic-1.png")))
        assertEquals(null, imageLabel(File("tpl-workflow-tall-x.png")))
        assertEquals(File("missing.png"), labelImage(File("missing.png"), "code", ImageFormat.SQUARE))
    }

    @Test
    fun `the label reads the template's name and the ratio`() {
        val templates = listOf(ImageTemplate("workflow", "Workflow", "", ""))
        assertEquals("Workflow · 2:3", ImageLabel("workflow", ImageFormat.PORTRAIT).text(templates))
        assertEquals("Data · 3:2", ImageLabel("data", ImageFormat.LANDSCAPE).text(templates))
        // The real size, when known, replaces the planned ratio.
        assertEquals("Workflow · 1:1 · 2160×2160", ImageLabel("workflow", ImageFormat.PORTRAIT).text(templates, "1:1 · 2160×2160"))
        assertEquals("Workflow · 2:3 · 1664×2496 · AI", ImageLabel("workflow", ImageFormat.PORTRAIT, painted = true).text(templates, "2:3 · 1664×2496"))
    }

    @Test
    fun `a painted image's label says it is AI-painted`() {
        val dir = Files.createTempDirectory("labels").toFile()
        val file = File(dir, "a.png").apply { writeText("png") }

        val labelled = labelImage(file, "workflow", ImageFormat.PORTRAIT, painted = true)

        assertEquals("tpl-workflow-portrait-painted-a.png", labelled.name)
        assertEquals(ImageLabel("workflow", ImageFormat.PORTRAIT, painted = true), imageLabel(labelled))
        assertEquals("Workflow · 2:3 · AI", imageLabel(labelled)!!.text(listOf(ImageTemplate("workflow", "Workflow", "", ""))))
        dir.deleteRecursively()
    }
}

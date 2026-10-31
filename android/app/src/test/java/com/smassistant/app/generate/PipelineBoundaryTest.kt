package com.smassistant.app.generate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The image and video pipelines share nothing (docs/requirements/19, SP-1 to SP-16): no file in
 * `image/` names anything in `video/` and the other way round, each draws with its own copy of the
 * renderer, and the old shared `infographic` package is gone.
 */
class PipelineBoundaryTest {
    private val app = File("src/main/java/com/smassistant/app")
    private fun sources(folder: String) = File(app, folder).walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `the image pipeline never uses the video pipeline`() {
        val crossing = sources("image").filter { it.readText().contains("com.smassistant.app.video") }
        assertEquals(emptyList<File>(), crossing)
    }

    @Test
    fun `the video pipeline never uses the image pipeline`() {
        val crossing = sources("video").filter { it.readText().contains("com.smassistant.app.image") }
        assertEquals(emptyList<File>(), crossing)
    }

    @Test
    fun `each pipeline has its own renderer and the shared one is gone`() {
        listOf("InfographicRenderer.kt", "InfographicSpec.kt", "InfographicPipeline.kt", "AndroidInfographicComposer.kt").forEach { name ->
            assertTrue(name, File(app, "image/render/$name").isFile)
            assertTrue(name, File(app, "video/render/$name").isFile)
        }
        assertTrue(!File(app, "infographic").exists() || File(app, "infographic").list().isNullOrEmpty())
        val shared = app.walkTopDown().filter { it.extension == "kt" && it.readText().contains("com.smassistant.app.infographic") }.toList()
        assertEquals(emptyList<File>(), shared)
    }
}

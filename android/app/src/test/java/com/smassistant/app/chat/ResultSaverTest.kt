package com.smassistant.app.chat

import com.smassistant.app.generate.ContentAndMedia
import com.smassistant.app.generate.GeneratedVideo
import com.smassistant.app.prompt.PreparedPrompt
import java.io.File
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultSaverTest {
    private val dir = Files.createTempDirectory("saved").toFile()
    private fun file(name: String) = File(dir, name).apply { writeBytes(byteArrayOf(1)) }

    private fun <R> roundTrip(saver: ResultSaver<R>, value: R) = saver.restore(saver.save(value))

    @Test
    fun `text and prepared prompts round trip`() {
        assertEquals("## Post\\n#java", roundTrip(TextSaver, "## Post\\n#java"))
        assertEquals(PreparedPrompt("Short URL", "Design…"), roundTrip(PreparedPromptSaver, PreparedPrompt("Short URL", "Design…")))
    }

    @Test
    fun `media comes back only while its files are still in the cache`() {
        val images = listOf(file("a.png"), file("b.png"))
        assertEquals(images, roundTrip(ImagesSaver, images))
        val video = GeneratedVideo(file("v.mp4"), images, seconds = 150)
        assertEquals(video, roundTrip(VideoSaver, video))
        // Saved before the length option: 4 seconds a slide.
        val old = JsonObject((VideoSaver.save(video) as JsonObject) - "seconds")
        assertEquals(8, VideoSaver.restore(old)?.seconds)

        val saved = ImagesSaver.save(images)
        images[1].delete()
        assertNull(ImagesSaver.restore(saved))
        assertNull(roundTrip(VideoSaver, GeneratedVideo(File(dir, "gone.mp4"), emptyList())))
    }

    @Test
    fun `content and media keep each half and its error`() {
        val saver = ContentAndMediaSaver(ImagesSaver)
        val value = ContentAndMedia<List<File>>(markdown = "Post", media = null, mediaError = "Gemini is down")
        assertEquals(value, roundTrip(saver, value))
    }

    @Test
    fun `a half being retried or whose media is gone comes back as an error`() {
        val saver = ContentAndMediaSaver(ImagesSaver)
        val image = file("c.png")
        val saved = saver.save(ContentAndMedia(textError = "Groq is down", retryingText = true, media = listOf(image)))
        image.delete()
        assertEquals(ContentAndMedia<List<File>>(textError = STOPPED, mediaError = MEDIA_GONE), saver.restore(saved))
    }
}

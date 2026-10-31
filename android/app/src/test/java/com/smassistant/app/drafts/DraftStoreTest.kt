package com.smassistant.app.drafts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DraftStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mediaDir = Files.createTempDirectory("drafts").toFile()
    private val store = DraftStore(context.getSharedPreferences("drafts-test", Context.MODE_PRIVATE), mediaDir) { 9L }
    private val cache = Files.createTempDirectory("cache").toFile()

    private fun cached(name: String) = File(cache, name).apply { writeBytes(byteArrayOf(1, 2)) }

    @Test
    fun `saving copies media out of the cache and titles the draft by its text`() {
        val image = cached("a.png")
        val draft = store.save("Caching in 3 steps\n\nBody #java", listOf(image))
        assertEquals("Caching in 3 steps", draft.title)
        assertEquals(9L, draft.savedAt)
        val kept = draft.files.single()
        assertTrue(kept.path.startsWith(mediaDir.path))
        assertTrue(kept.exists())
        image.delete()
        assertTrue(store.get(draft.id)!!.files.single().exists())
        assertEquals("1 image", draft.mediaLabel)
    }

    @Test
    fun `saving over a draft keeps its media, drops what was removed and moves it to the top`() {
        val first = store.save("One", listOf(cached("a.png"), cached("b.png")))
        store.save("Two", emptyList())
        val updated = store.save("One edited", first.files.take(1), id = first.id)
        assertEquals(first.id, updated.id)
        assertEquals(first.files.take(1), updated.files)
        assertFalse(first.files[1].exists())
        assertEquals(listOf("One edited", "Two"), store.all().map { it.text })
    }

    @Test
    fun `media-only drafts get a title and deleting removes the media`() {
        val video = store.save("", listOf(cached("v.mp4")))
        assertEquals("Video draft", video.title)
        assertEquals("Video", video.mediaLabel)
        store.delete(video.id)
        assertEquals(emptyList<Draft>(), store.all())
        assertFalse(video.files.single().exists())
    }

    @Test
    fun `json round trips and skips broken entries`() {
        val list = listOf(Draft("a", 1L, "T", "text", listOf("/x/a.png")))
        assertEquals(list, DraftJson.decode(DraftJson.encode(list)))
        assertEquals(list, DraftJson.decode(DraftJson.encode(list).replaceFirst("[", "[{\"id\":2},")))
    }
}

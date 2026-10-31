package com.smassistant.app.media

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.image.render.numbered
import java.io.File
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaExportTest {
    private val savedAt = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 31, 9, 15) }.time

    @Test
    fun `images go to Pictures and videos to Movies with readable names`() {
        val image = galleryEntry(File("1b2c.png"), savedAt)
        assertEquals("SM Assistant 2026-10-31 0915.png", image.displayName)
        assertEquals("image/png", image.mimeType)
        assertEquals("${Environment.DIRECTORY_PICTURES}/SM Assistant", image.relativePath)

        val video = galleryEntry(File("9f.mp4"), savedAt)
        assertEquals("video/mp4", video.mimeType)
        assertEquals("${Environment.DIRECTORY_MOVIES}/SM Assistant", video.relativePath)
    }

    @Test
    fun `files saved together get numbered names`() {
        val first = galleryEntry(File("a.jpg"), savedAt, 0)
        val second = galleryEntry(File("b.jpg"), savedAt, 1)
        assertEquals("SM Assistant 2026-10-31 0915 2.jpg", second.displayName)
        assertNotEquals(first.displayName, second.displayName)
    }

    @Test
    fun `share type is exact when files match and wider when they don't`() {
        assertEquals("image/png", shareMimeType(listOf(File("a.png"), File("b.png"))))
        assertEquals("image/*", shareMimeType(listOf(File("a.png"), File("b.jpg"))))
        assertEquals("video/mp4", shareMimeType(listOf(File("v.mp4"))))
        assertEquals("*/*", shareMimeType(listOf(File("a.png"), File("v.mp4"))))
    }

    @Test
    fun `share sends content links with a read grant`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cache = MediaCache(context)
        val a = cache.save(byteArrayOf(1), "png")
        val b = cache.save(byteArrayOf(2), "png")

        val chooser = MediaExport.shareIntent(context, listOf(a, b))
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uris = send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(2, uris.size)
        assertTrue(uris.all { it.scheme == "content" && it.authority == "${context.packageName}.files" })
        assertEquals(2, send.clipData!!.itemCount)
    }
}

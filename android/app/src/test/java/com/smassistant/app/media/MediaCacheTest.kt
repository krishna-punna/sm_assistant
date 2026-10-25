package com.smassistant.app.media

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCacheTest {
    private val dir = Files.createTempDirectory("media").toFile()
    private val cache = MediaCache(dir)

    @Test
    fun `saves bytes to a new file with the extension`() {
        val a = cache.save(byteArrayOf(1, 2, 3), "png")
        val b = cache.save(byteArrayOf(4), "jpg")
        assertTrue(a.name.endsWith(".png"))
        assertTrue(b.name.endsWith(".jpg"))
        assertArrayEquals(byteArrayOf(1, 2, 3), a.readBytes())
        assertEquals(dir, a.parentFile)
    }

    @Test
    fun `removes only files older than the max age`() {
        val now = System.currentTimeMillis()
        val old = cache.save(byteArrayOf(1), "png").apply { setLastModified(now - TimeUnit.DAYS.toMillis(8)) }
        val fresh = cache.save(byteArrayOf(2), "png").apply { setLastModified(now - TimeUnit.DAYS.toMillis(1)) }

        cache.clearOlderThan(now = now)

        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `mime types map to extensions`() {
        assertEquals("jpg", extensionFor("image/jpeg"))
        assertEquals("webp", extensionFor("IMAGE/WEBP"))
        assertEquals("png", extensionFor("image/png"))
        assertEquals("png", extensionFor("application/octet-stream"))
    }

    @Test
    fun `newFile gives an unused path in the cache without writing it`() {
        val a = cache.newFile("mp4")
        assertEquals(dir, a.parentFile)
        assertTrue(a.name.endsWith(".mp4"))
        assertFalse(a.exists())
        assertFalse(a == cache.newFile("mp4"))
    }

    @Test
    fun `extensions map back to image mime types`() {
        assertEquals("image/jpeg", imageMimeType(File("a.JPG")))
        assertEquals("image/webp", imageMimeType(File("a.webp")))
        assertEquals("image/png", imageMimeType(File("a.png")))
    }
}

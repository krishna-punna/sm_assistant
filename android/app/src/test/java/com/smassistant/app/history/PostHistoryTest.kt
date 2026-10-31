package com.smassistant.app.history

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PostHistoryTest {
    private val store = PostHistoryStore(ApplicationProvider.getApplicationContext())

    private fun post(title: String, source: PostSource = PostSource.MANUAL, at: Long = 1L) =
        PublishedPost(postedAt = at, title = title, text = "$title text", url = "https://www.linkedin.com/feed/update/$title", source = source)

    @Test
    fun `an entry records what the post carried and titles itself by the first line`() {
        val images = PublishedPost.of("#java\n\nKafka in 5 points\nmore", listOf(File("a.png"), File("b.png")), "u", PostSource.MANUAL, 5L)
        assertEquals("Kafka in 5 points", images.title)
        assertEquals(PostMedia.IMAGES, images.media)
        assertEquals("2 images", images.mediaLabel)

        val video = PublishedPost.of("Hi", listOf(File("v.mp4")), "u", PostSource.SCHEDULED, 5L, title = "Kafka Partitions")
        assertEquals("Kafka Partitions", video.title)
        assertEquals("Video", video.mediaLabel)
        assertEquals("Text", PublishedPost.of("Hi", emptyList(), "u", PostSource.MANUAL, 5L).mediaLabel)
    }

    @Test
    fun `long first lines are cut and hashtag-only posts are untitled`() {
        assertEquals(80, PublishedPost.titleOf("x".repeat(200)).length)
        assertEquals("Untitled post", PublishedPost.titleOf("#java #kafka"))
    }

    @Test
    fun `json round trips and skips broken entries`() {
        val list = listOf(post("a", PostSource.SCHEDULED), post("b"))
        assertEquals(list, PostHistoryJson.decode(PostHistoryJson.encode(list)))
        val withBroken = PostHistoryJson.encode(list).replaceFirst("[", "[{\"id\":1},")
        assertEquals(list, PostHistoryJson.decode(withBroken))
        assertEquals(emptyList<PublishedPost>(), PostHistoryJson.decode("not json"))
    }

    @Test
    fun `store keeps newest first, removes and clears`() {
        store.add(post("old"))
        store.add(post("new"))
        assertEquals(listOf("new", "old"), store.all().map { it.title })
        store.delete(store.all().first().id)
        assertEquals(listOf("old"), store.all().map { it.title })
        store.clear()
        assertEquals(emptyList<PublishedPost>(), store.all())
    }

    @Test
    fun `store caps its size and lists recent scheduled titles once each`() {
        repeat(PostHistoryStore.MAX_POSTS + 5) { store.add(post("p$it")) }
        assertEquals(PostHistoryStore.MAX_POSTS, store.all().size)
        store.clear()
        store.add(post("Kafka", PostSource.SCHEDULED))
        store.add(post("Mine"))
        store.add(post("Kafka", PostSource.SCHEDULED))
        store.add(post("Redis", PostSource.SCHEDULED))
        assertEquals(listOf("Redis", "Kafka"), store.recentScheduledTitles())
    }
}

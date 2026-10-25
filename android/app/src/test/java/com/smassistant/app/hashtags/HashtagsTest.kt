package com.smassistant.app.hashtags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagsParseTest {
    @Test
    fun `splits on spaces, commas and new lines`() {
        val parsed = Hashtags.parse("#Java, #Kotlin\n#Android  #Compose,\n\n")
        assertEquals(listOf("#Java", "#Kotlin", "#Android", "#Compose"), parsed.tags)
        assertTrue(parsed.isValid)
    }

    @Test
    fun `adds missing hash`() {
        assertEquals(listOf("#JavaDeveloper", "#AIEngineer"), Hashtags.parse("JavaDeveloper #AIEngineer").tags)
    }

    @Test
    fun `removes duplicates case-insensitively keeping first spelling and order`() {
        val parsed = Hashtags.parse("JavaDeveloper, #AIEngineer #javadeveloper #AIENGINEER #Spring")
        assertEquals(listOf("#JavaDeveloper", "#AIEngineer", "#Spring"), parsed.tags)
    }

    @Test
    fun `rejects characters LinkedIn does not allow`() {
        val parsed = Hashtags.parse("#C++ #ok hello-world #a.b # ## #🚀")
        assertEquals(listOf("#ok"), parsed.tags)
        assertEquals(listOf("#C++", "hello-world", "#a.b", "#", "##", "#🚀"), parsed.invalid)
        assertFalse(parsed.isValid)
    }

    @Test
    fun `allows underscores, digits and non-Latin letters`() {
        assertEquals(listOf("#spring_boot", "#Java17", "#開発", "#Café"), Hashtags.parse("spring_boot Java17 #開発 Café").tags)
    }

    @Test
    fun `empty input gives empty list`() {
        assertEquals(ParsedHashtags(emptyList(), emptyList()), Hashtags.parse("  \n , "))
    }

    @Test
    fun `format joins with single spaces`() {
        assertEquals("#A #B", Hashtags.format(listOf("#A", "#B")))
    }
}

class HashtagsAppendTest {
    private val tags = listOf("#JavaDeveloper", "#AIEngineer")

    @Test
    fun `appends after one blank line`() {
        val post = Hashtags.appendTo("📌 In short: YAML is cleaner.\n", tags, enabled = true)
        assertEquals("📌 In short: YAML is cleaner.\n\n#JavaDeveloper #AIEngineer", post.text)
        assertEquals(tags, post.addedTags)
        assertTrue(post.fitsLimit)
    }

    @Test
    fun `disabled or empty list leaves body unchanged`() {
        assertEquals("Body\n", Hashtags.appendTo("Body\n", tags, enabled = false).text)
        assertEquals("Body\n", Hashtags.appendTo("Body\n", emptyList(), enabled = true).text)
    }

    @Test
    fun `skips tags already in the text, case-insensitively`() {
        val post = Hashtags.appendTo("Learning with #javadeveloper friends", tags, enabled = true)
        assertEquals("Learning with #javadeveloper friends\n\n#AIEngineer", post.text)
        assertEquals(listOf("#AIEngineer"), post.addedTags)
    }

    @Test
    fun `a longer tag in the body does not count as the shorter tag`() {
        val post = Hashtags.appendTo("All about #AIEngineering", listOf("#AI", "#AIEngineer"), enabled = true)
        assertEquals(listOf("#AI", "#AIEngineer"), post.addedTags)
    }

    @Test
    fun `all tags present adds nothing`() {
        val body = "Hi #JavaDeveloper #AIEngineer"
        assertEquals(body, Hashtags.appendTo(body, tags, enabled = true).text)
    }

    @Test
    fun `media-only post gets just the tags`() {
        assertEquals("#JavaDeveloper #AIEngineer", Hashtags.appendTo("", tags, enabled = true).text)
        assertEquals("#JavaDeveloper #AIEngineer", Hashtags.appendTo("  \n", tags, enabled = true).text)
    }

    @Test
    fun `reports how far over the limit the text is, keeping the tags`() {
        val body = "x".repeat(2_990)
        val post = Hashtags.appendTo(body, tags, enabled = true)
        assertTrue(post.text.endsWith("#JavaDeveloper #AIEngineer"))
        assertEquals(2_990 + 2 + 26 - LINKEDIN_POST_LIMIT, post.overLimitBy)
        assertFalse(post.fitsLimit)
    }

    @Test
    fun `exactly at the limit fits`() {
        val body = "x".repeat(LINKEDIN_POST_LIMIT - 2 - "#A".length)
        assertTrue(Hashtags.appendTo(body, listOf("#A"), enabled = true).fitsLimit)
    }
}

class PostLengthTest {
    @Test
    fun `counts emoji as one character and line breaks as characters`() {
        assertEquals(1, postLength("🔧"))
        assertEquals(5, postLength("a\n\n📌b"))
    }

    @Test
    fun `an emoji-heavy post is measured in code points, not UTF-16 units`() {
        val post = Hashtags.appendTo("🚀".repeat(3_000), emptyList(), enabled = false)
        assertTrue(post.fitsLimit)
    }

    @Test
    fun `reserved length is the tags plus the blank line, only when on`() {
        assertEquals("#Java #AI".length + 2, Hashtags.reservedLength(listOf("#Java", "#AI"), enabled = true))
        assertEquals(0, Hashtags.reservedLength(listOf("#Java"), enabled = false))
        assertEquals(0, Hashtags.reservedLength(emptyList(), enabled = true))
    }
}

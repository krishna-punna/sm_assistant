package com.smassistant.app.linkedin

import org.junit.Assert.assertEquals
import org.junit.Test

class LittleTextTest {
    @Test
    fun `reserved characters are escaped`() {
        assertEquals(
            "Spring \\(Boot\\) \\<3 \\[a\\] \\{b\\} \\@me a\\*b\\_c\\~d \\| back\\\\slash",
            toLittleText("Spring (Boot) <3 [a] {b} @me a*b_c~d | back\\slash"),
        )
    }

    @Test
    fun `hashtags become hashtag templates`() {
        assertEquals(
            "Done.\n\n{hashtag|\\#|systemdesign} {hashtag|\\#|java\\_17}",
            toLittleText("Done.\n\n#systemdesign #java_17"),
        )
    }

    @Test
    fun `a hash inside a word or alone is escaped, not a hashtag`() {
        assertEquals("C\\# and \\# and issue\\#12", toLittleText("C# and # and issue#12"))
    }

    @Test
    fun `plain text, emoji and newlines pass through`() {
        val text = "🔧 YAML vs .properties — Quick Comparison\n📌 In short: YAML is cleaner."
        assertEquals(text, toLittleText(text))
    }
}

package com.smassistant.app.generate

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoLengthTest {
    @Test
    fun `lengths run from half a minute to five minutes`() {
        assertEquals(listOf(30, 60, 90, 120, 150, 180, 210, 240, 270, 300), VIDEO_LENGTHS)
        assertEquals(
            listOf("30 sec", "1 min", "1 min 30 sec", "5 min"),
            listOf(30, 60, 90, 300).map(::videoLengthLabel),
        )
    }

    @Test
    fun `longer videos get more slides, up to the most one reply can plan`() {
        assertEquals(listOf(4, 7, 10, 10), listOf(30, 60, 90, 300).map(::slidesFor))
    }
}

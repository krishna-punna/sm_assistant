package com.smassistant.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideTimingTest {
    @Test
    fun `requested seconds are scaled to the picked length`() {
        assertEquals(listOf(6_000L, 12_000L, 12_000L), sceneMillis(listOf(5, 10, 10), 30))
        assertEquals(listOf(15_000L, 30_000L, 15_000L), sceneMillis(listOf(4, 8, 4), 60))
    }

    @Test
    fun `no requests share the length equally, with the remainder on the last scene`() {
        assertEquals(List(4) { 7_500L }, sceneMillis(List(4) { null }, 30))
        assertEquals(listOf(3_333L, 3_333L, 3_334L), sceneMillis(List(3) { null }, 10))
    }

    @Test
    fun `a scene without a request gets the average of the others`() {
        assertEquals(listOf(10_000L, 10_000L, 10_000L), sceneMillis(listOf(4, null, 4), 30))
    }

    @Test
    fun `every scene stays up at least three seconds and the total is kept`() {
        val millis = sceneMillis(listOf(1, 20, 20, 20), 30)
        assertEquals(3_000L, millis[0])
        assertTrue(millis.all { it >= MIN_SCENE_SECONDS * 1000L })
        assertEquals(30_000L, millis.sum())
        assertEquals(listOf(3_000L, 9_000L, 9_000L, 9_000L), millis)
    }

    @Test
    fun `too little time for the minimum falls back to equal shares`() {
        assertEquals(List(4) { 2_000L }, sceneMillis(listOf(1, 5, 5, 5), 8))
        assertEquals(emptyList<Long>(), sceneMillis(emptyList(), 30))
    }

    @Test
    fun `a fade blends a quarter, a half and three quarters`() {
        assertEquals(listOf(0.25f, 0.5f, 0.75f), fadeAmounts())
    }
}

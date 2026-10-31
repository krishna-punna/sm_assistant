package com.smassistant.app.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.ImageSize
import com.smassistant.app.data.ImageMode
import com.smassistant.app.data.ImageStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageStyleSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val defaults = ImageStyle(audience = "developers", brandName = "", styleGuide = "", series = false, format = ImageSize.SQUARE)
    private val store = ImageStyleStore(context) { defaults }

    @Before
    fun clear() = store.reset()

    @Test
    fun `saved values replace the app defaults until reset`() {
        assertEquals(defaults, store.load())

        store.save(ImageStyle(audience = " architects ", brandName = "Krishna Tech", styleGuide = "navy and teal", series = true, mode = ImageMode.DRAWN))

        // The format stays the app's: it is picked on the image screens.
        assertEquals(
            ImageStyle(audience = "architects", brandName = "Krishna Tech", styleGuide = "navy and teal", series = true, format = ImageSize.SQUARE, mode = ImageMode.DRAWN),
            store.load(),
        )
        store.reset()
        assertEquals(defaults, store.load())
    }

    @Test
    fun `the screen edits, saves and resets`() {
        val vm = ImageStyleViewModel(store)
        vm.onAudience("students")
        vm.onBrandName("SM")
        vm.onStyleGuide("flat icons")
        vm.onSeries(true)
        vm.onMode(ImageMode.PAINTED)
        // Nothing is saved before Save.
        assertEquals(defaults, store.load())

        vm.save()
        val saved = defaults.copy(audience = "students", brandName = "SM", styleGuide = "flat icons", series = true, mode = ImageMode.PAINTED)
        assertEquals(saved, store.load())
        assertEquals(saved, vm.state.value.style)
        assertEquals("Image style saved", vm.state.value.message)

        vm.reset()
        assertEquals(defaults, vm.state.value.style)
        assertEquals(defaults, store.load())
    }
}

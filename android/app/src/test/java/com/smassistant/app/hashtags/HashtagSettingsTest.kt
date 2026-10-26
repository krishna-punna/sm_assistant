package com.smassistant.app.hashtags

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HashtagSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        context.getSharedPreferences("hashtag_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun store() = HashtagSettingsStore(context)
    private fun viewModel() = DefaultHashtagsViewModel(store())

    @Test
    fun `fresh install has no tags and the toggle off`() {
        assertEquals(emptyList<String>(), store().tags)
        assertFalse(store().enabled)
        assertEquals(DefaultHashtagsUiState(), viewModel().state.value)
    }

    @Test
    fun `save normalises, persists and turns the toggle on the first time`() {
        val vm = viewModel()
        vm.onTextChange("JavaDeveloper, #AIEngineer #javadeveloper")
        vm.onSave()

        val state = vm.state.value
        assertEquals("#JavaDeveloper #AIEngineer", state.text)
        assertTrue(state.enabled)
        assertEquals("Saved 2 hashtags", state.message)
        assertEquals(listOf("#JavaDeveloper", "#AIEngineer"), store().tags)
        assertTrue(store().enabled)
    }

    @Test
    fun `reopening the screen shows saved tags and toggle`() {
        store().save(listOf("#A", "#B"))
        store().setEnabled(false)

        val state = viewModel().state.value
        assertEquals("#A #B", state.text)
        assertFalse(state.enabled)
    }

    @Test
    fun `after the first save only the user changes the toggle`() {
        val vm = viewModel()
        vm.onTextChange("#A")
        vm.onSave()
        vm.onEnabledChange(false)
        vm.onTextChange("#A #B")
        vm.onSave()

        assertFalse(vm.state.value.enabled)
        assertFalse(store().enabled)
    }

    @Test
    fun `invalid tags show an error and save nothing`() {
        store().save(listOf("#Keep"))
        val vm = viewModel()
        vm.onTextChange("#C++ #ok")
        vm.onSave()

        val state = vm.state.value
        assertTrue(state.error!!.contains("#C++"))
        assertNull(state.message)
        assertEquals("#C++ #ok", state.text)
        assertEquals(listOf("#Keep"), store().tags)
    }

    @Test
    fun `typing clears the error`() {
        val vm = viewModel()
        vm.onTextChange("#C++")
        vm.onSave()
        vm.onTextChange("#C")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `clear empties the box but keeps the saved list until save`() {
        store().save(listOf("#A"))
        val vm = viewModel()
        vm.onClear()

        assertEquals("", vm.state.value.text)
        assertEquals(listOf("#A"), store().tags)

        vm.onSave()
        assertEquals(emptyList<String>(), store().tags)
        assertEquals("Default hashtags removed", vm.state.value.message)
    }

    @Test
    fun `empty save does not change the toggle`() {
        store().save(listOf("#A"))
        val vm = viewModel()
        vm.onClear()
        vm.onSave()
        assertTrue(store().enabled)
    }

    @Test
    fun `turning the toggle off keeps the list`() {
        store().save(listOf("#A", "#B"))
        viewModel().onEnabledChange(false)

        assertEquals(listOf("#A", "#B"), store().tags)
        assertFalse(store().enabled)
    }

    @Test
    fun `warns above five tags but still saves`() {
        val vm = viewModel()
        vm.onTextChange("#a #b #c #d #e #f")
        vm.onSave()

        assertEquals("LinkedIn suggests 3–5 hashtags per post. You have 6.", vm.state.value.warning)
        assertEquals(6, store().tags.size)
    }

    @Test
    fun `AI-suggested hashtags start off and persist separately from the default toggle`() {
        assertFalse(store().aiEnabled)
        store().save(listOf("#A"))
        viewModel().onAiEnabledChange(true)

        assertTrue(store().aiEnabled)
        assertTrue(viewModel().state.value.aiEnabled)
        viewModel().onEnabledChange(false)
        assertTrue(store().aiEnabled)
    }

    @Test
    fun `message is cleared once shown`() {
        val vm = viewModel()
        vm.onTextChange("#A")
        vm.onSave()
        vm.messageShown()
        assertNull(vm.state.value.message)
    }
}

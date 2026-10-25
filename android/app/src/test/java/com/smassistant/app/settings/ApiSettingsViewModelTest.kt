package com.smassistant.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApiSettingsViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store get() = ApiSettingsStore(context)

    @Before
    fun clear() {
        context.getSharedPreferences("api_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun viewModel(service: ApiService = ApiService.CLAUDE) = ApiSettingsViewModel(service, store)

    @Test
    fun `pre-fills from app yml defaults`() {
        val state = viewModel().state.value
        assertEquals("https://api.anthropic.com/v1/messages", state.api)
        assertEquals("", state.key)
    }

    @Test
    fun `pre-fills saved values`() {
        store.save(ApiService.GROQ, "https://example.com/groq", "gsk-1")
        val state = viewModel(ApiService.GROQ).state.value
        assertEquals("https://example.com/groq", state.api)
        assertEquals("gsk-1", state.key)
    }

    @Test
    fun `valid save persists trimmed values and confirms`() {
        val vm = viewModel()
        vm.onApiChange(" https://proxy.example.com/v1/messages ")
        vm.onKeyChange(" sk-ant-1 ")
        vm.onSave()

        assertEquals("Claude settings saved", vm.state.value.message)
        assertEquals("https://proxy.example.com/v1/messages", store.load(ApiService.CLAUDE).api)
        assertEquals("sk-ant-1", store.load(ApiService.CLAUDE).key)
    }

    @Test
    fun `blank key shows an error and saves nothing`() {
        val vm = viewModel(ApiService.LINKEDIN)
        vm.onKeyChange("   ")
        vm.onSave()

        assertEquals("Enter your LinkedIn API key", vm.state.value.keyError)
        assertNull(vm.state.value.message)
        assertEquals("", store.load(ApiService.LINKEDIN).key)
    }

    @Test
    fun `non-https url shows an error and saves nothing`() {
        listOf("http://api.groq.com", "api.groq.com", "htp:/x", "https://").forEach { url ->
            val vm = viewModel(ApiService.GROQ)
            vm.onApiChange(url)
            vm.onKeyChange("gsk")
            vm.onSave()
            assertEquals(url, "Enter a full https:// URL", vm.state.value.apiError)
        }
        assertEquals("", store.load(ApiService.GROQ).key)
    }

    @Test
    fun `blank url falls back to the app yml default`() {
        store.save(ApiService.GEMINI, "https://custom.example.com", "k")
        val vm = viewModel(ApiService.GEMINI)
        vm.onApiChange("")
        vm.onKeyChange("k2")
        vm.onSave()

        assertEquals("https://generativelanguage.googleapis.com/v1beta", vm.state.value.api)
        assertEquals("https://generativelanguage.googleapis.com/v1beta", store.load(ApiService.GEMINI).api)
        assertEquals("Gemini settings saved, using the default API", vm.state.value.message)
    }

    @Test
    fun `editing a field clears its error`() {
        val vm = viewModel()
        vm.onApiChange("nope")
        vm.onSave()
        vm.onApiChange("https://ok.example.com")
        vm.onKeyChange("k")
        assertNull(vm.state.value.apiError)
        assertNull(vm.state.value.keyError)
    }

    @Test
    fun `https url check`() {
        assertTrue(isValidHttpsUrl("https://api.linkedin.com/rest/posts"))
        assertTrue(isValidHttpsUrl("HTTPS://localhost:8443"))
        assertFalse(isValidHttpsUrl("http://api.linkedin.com"))
        assertFalse(isValidHttpsUrl("not a url"))
        assertFalse(isValidHttpsUrl(""))
    }
}

package com.smassistant.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApiSettingsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ApiSettingsStore

    @Before
    fun setUp() {
        context.getSharedPreferences("api_settings", Context.MODE_PRIVATE).edit().clear().commit()
        store = ApiSettingsStore(context)
    }

    @Test
    fun `falls back to app yml defaults when nothing is saved`() {
        val config = store.load(ApiService.CLAUDE)

        assertEquals("https://api.anthropic.com/v1/messages", config.api)
        assertEquals("", config.key)
        assertEquals("claude-sonnet-5-5", config.model)
    }

    @Test
    fun `saved values override defaults and are trimmed`() {
        store.save(ApiService.GROQ, "  https://example.com/groq ", " gsk-123 ")

        val config = ApiSettingsStore(context).load(ApiService.GROQ)
        assertEquals("https://example.com/groq", config.api)
        assertEquals("gsk-123", config.key)
        assertEquals("llama-3.3-70b-versatile", config.model)
    }

    @Test
    fun `services are stored independently`() {
        store.save(ApiService.GEMINI, "https://g", "gemini-key")

        assertEquals("", store.load(ApiService.CLAUDE).key)
        assertEquals("", store.load(ApiService.LINKEDIN).key)
        assertEquals("gemini-key", store.load(ApiService.GEMINI).key)
    }
}

package com.smassistant.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        store = ApiSettingsStore(context, FakeCipher())
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

        val config = ApiSettingsStore(context, FakeCipher()).load(ApiService.GROQ)
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

    private val prefs get() = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    @Test
    fun `keys are stored encrypted, never in plain text`() {
        store.save(ApiService.CLAUDE, "https://api.anthropic.com/v1/messages", "sk-ant-secret")

        val stored = prefs.all.values.map { it.toString() }
        assertFalse(stored.any { it.contains("sk-ant-secret") })
        assertEquals(FakeCipher().encrypt("sk-ant-secret"), prefs.getString("CLAUDE.key_enc", null))
    }

    @Test
    fun `plain-text key from an older build is migrated once`() {
        prefs.edit().putString("LINKEDIN.key", "old-token").commit()

        assertEquals("old-token", store.load(ApiService.LINKEDIN).key)
        assertNull(prefs.getString("LINKEDIN.key", null))
        assertEquals(FakeCipher().encrypt("old-token"), prefs.getString("LINKEDIN.key_enc", null))
        assertEquals("old-token", ApiSettingsStore(context, FakeCipher()).load(ApiService.LINKEDIN).key)
    }

    @Test
    fun `undecryptable key falls back to the default`() {
        prefs.edit().putString("GROQ.key_enc", "garbage").commit()
        assertEquals("", store.load(ApiService.GROQ).key)
    }
}

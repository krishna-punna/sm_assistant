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
        val config = store.load(ApiService.GEMINI)

        assertEquals("https://generativelanguage.googleapis.com/v1beta", config.api)
        assertEquals("", config.key)
        assertEquals("gemini-2.5-flash", config.model)
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

        assertEquals("", store.load(ApiService.GROQ).key)
        assertEquals("", store.load(ApiService.LINKEDIN).key)
        assertEquals("gemini-key", store.load(ApiService.GEMINI).key)
    }

    private val prefs get() = context.getSharedPreferences("api_settings", Context.MODE_PRIVATE)

    @Test
    fun `keys are stored encrypted, never in plain text`() {
        store.save(ApiService.GROQ, "https://api.groq.com/openai/v1/chat/completions", "gsk-secret")

        val stored = prefs.all.values.map { it.toString() }
        assertFalse(stored.any { it.contains("gsk-secret") })
        assertEquals(FakeCipher().encrypt("gsk-secret"), prefs.getString("GROQ.key_enc", null))
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

    @Test
    fun `chosen models override the defaults until cleared`() {
        store.saveModels(ApiService.GEMINI, " gemini-3-pro ", "gemini-3-pro-image")
        assertEquals("gemini-3-pro", store.load(ApiService.GEMINI).model)
        assertEquals("gemini-3-pro-image", store.load(ApiService.GEMINI).imageModel)

        store.saveModels(ApiService.GEMINI, "", "")
        assertEquals("gemini-2.5-flash", store.load(ApiService.GEMINI).model)
        assertEquals("gemini-2.5-flash-image", store.load(ApiService.GEMINI).imageModel)
    }

    @Test
    fun `image and video models are saved separately`() {
        assertEquals("", store.load(ApiService.GEMINI).videoModel)
        store.saveMediaModel(ApiService.GEMINI, MediaPurpose.VIDEO, " gemini-3-pro-image ")
        assertEquals("gemini-3-pro-image", store.load(ApiService.GEMINI).videoModel)
        assertEquals("gemini-2.5-flash-image", store.load(ApiService.GEMINI).imageModel)

        store.saveMediaModel(ApiService.GEMINI, MediaPurpose.IMAGE, "imagen")
        assertEquals("imagen", store.load(ApiService.GEMINI).imageModel)
        store.saveMediaModel(ApiService.GEMINI, MediaPurpose.VIDEO, "")
        assertEquals("", store.load(ApiService.GEMINI).videoModel)
    }

    @Test
    fun `the text model is saved apart from the image model`() {
        val defaultModel = store.load(ApiService.GEMINI).model
        store.saveTextModel(ApiService.GEMINI, " gemini-3-pro ")
        assertEquals("gemini-3-pro", store.load(ApiService.GEMINI).model)
        assertEquals("gemini-2.5-flash-image", store.load(ApiService.GEMINI).imageModel)
        store.saveTextModel(ApiService.GEMINI, "")
        assertEquals(defaultModel, store.load(ApiService.GEMINI).model)
    }

    @Test
    fun `fetched model lists are cached with their time`() {
        assertNull(store.cachedModels(ApiService.GEMINI))
        val lists = ModelLists(text = listOf(AiModel("gemini-2.5-flash", "Gemini 2.5 Flash")), image = listOf(AiModel("x")))
        store.saveCachedModels(ApiService.GEMINI, lists, fetchedAt = 1234L)
        assertEquals(CachedModels(lists, 1234L), ApiSettingsStore(context, FakeCipher()).cachedModels(ApiService.GEMINI))
        assertNull(store.cachedModels(ApiService.GROQ))
    }
}

package com.smassistant.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.AiModel
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.ApiSettingsStore
import com.smassistant.app.data.FakeCipher
import com.smassistant.app.data.ModelCatalog
import com.smassistant.app.data.ModelLists
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ApiSettingsViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store get() = ApiSettingsStore(context, FakeCipher())

    @Before
    fun clear() {
        context.getSharedPreferences("api_settings", Context.MODE_PRIVATE).edit().clear().commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val models = ModelLists(text = listOf(AiModel("claude-opus-5-5", "Claude Opus 5.5"), AiModel("claude-sonnet-5-5", "Claude Sonnet 5.5")))

    /** A catalog that answers from a fake provider and counts its calls. */
    private var fetches = 0
    private var reply: ApiResult<ModelLists> = ApiResult.Ok(models)
    private val catalog = object : ModelCatalog() {
        override suspend fun fetch(service: ApiService, config: ApiConfig): ApiResult<ModelLists> {
            fetches++
            return reply
        }
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

    private fun agentViewModel(now: Long = 10_000_000_000L) =
        ApiSettingsViewModel(ApiService.CLAUDE, store, catalog, now = { now })

    @Test
    fun `models are fetched on open once a key is saved, and cached for a day`() {
        agentViewModel()
        assertEquals(0, fetches)

        store.save(ApiService.CLAUDE, "", "sk-ant-1")
        val vm = agentViewModel()
        assertEquals(1, fetches)
        assertEquals(models, vm.state.value.models)
        assertEquals("claude-sonnet-5-5", vm.state.value.model)

        agentViewModel(now = 10_000_000_000L + MODEL_LIST_MAX_AGE_MS - 1)
        assertEquals(1, fetches)
        assertEquals(models, agentViewModel(now = 10_000_000_000L + MODEL_LIST_MAX_AGE_MS + 1).state.value.models)
        assertEquals(2, fetches)
    }

    @Test
    fun `refresh shows an error and keeps the old list when the fetch fails`() {
        store.save(ApiService.CLAUDE, "", "sk-ant-1")
        val vm = agentViewModel()
        reply = ApiResult.Failed(ApiError.InvalidKey)
        vm.refreshModels()
        assertEquals(models, vm.state.value.models)
        assertEquals("Couldn't update the model list. Claude rejected the API key. Check it in settings.", vm.state.value.modelsError)
    }

    @Test
    fun `the chosen model is saved with the key and used by the agents`() {
        val vm = agentViewModel()
        vm.onKeyChange("sk-ant-1")
        vm.onSave()
        assertEquals(1, fetches)

        vm.onModelChange("claude-opus-5-5")
        vm.onSave()
        assertEquals("claude-opus-5-5", store.load(ApiService.CLAUDE).model)
        assertEquals(1, fetches)
    }

    @Test
    fun `the current model stays in the options before the list has it`() {
        val state = ApiSettingsUiState(model = "claude-sonnet-5-5", models = ModelLists(text = listOf(AiModel("claude-opus-5-5"))))
        assertEquals(listOf("claude-sonnet-5-5", "claude-opus-5-5"), state.textOptions.map { it.id })
        assertEquals(listOf("claude-opus-5-5"), state.copy(model = "claude-opus-5-5").textOptions.map { it.id })
    }

    @Test
    fun `cloudflare asks for an account id and saves it with the key`() {
        val vm = ApiSettingsViewModel(ApiService.CLOUDFLARE, store, catalog)
        assertTrue(vm.state.value.showAccountId)
        assertTrue(vm.state.value.showImageModel)
        assertEquals(0, fetches)

        vm.onKeyChange("cf-token")
        vm.onSave()
        assertEquals("Enter your Cloudflare account ID", vm.state.value.accountIdError)
        assertEquals("", store.load(ApiService.CLOUDFLARE).key)

        vm.onAccountIdChange("abc/123")
        vm.onSave()
        assertEquals("Use only letters and numbers", vm.state.value.accountIdError)

        vm.onAccountIdChange(" abc123 ")
        assertNull(vm.state.value.accountIdError)
        vm.onSave()
        assertEquals("Cloudflare settings saved", vm.state.value.message)
        val saved = store.load(ApiService.CLOUDFLARE)
        assertEquals("abc123", saved.accountId)
        assertEquals("cf-token", saved.key)
        assertEquals("https://api.cloudflare.com/client/v4/accounts/abc123/ai", saved.apiUrl)
        assertEquals(1, fetches)
    }

    @Test
    fun `the cloudflare url with its placeholder is a valid url`() {
        val vm = ApiSettingsViewModel(ApiService.CLOUDFLARE, store, catalog)
        vm.onApiChange("https://api.cloudflare.com/client/v4/accounts/{account_id}/ai")
        vm.onKeyChange("t")
        vm.onAccountIdChange("abc")
        vm.onSave()
        assertNull(vm.state.value.apiError)
    }

    @Test
    fun `only agents that make images show an image model picker`() {
        listOf(ApiService.GEMINI, ApiService.HUGGINGFACE, ApiService.POLLINATIONS).forEach {
            assertTrue(it.name, ApiSettingsViewModel(it, store, catalog).state.value.showImageModel)
        }
        listOf(ApiService.CLAUDE, ApiService.GROQ).forEach {
            assertFalse(it.name, ApiSettingsViewModel(it, store, catalog).state.value.showImageModel)
        }
        assertFalse(ApiSettingsViewModel(ApiService.GROQ, store, catalog).state.value.showAccountId)
    }
}

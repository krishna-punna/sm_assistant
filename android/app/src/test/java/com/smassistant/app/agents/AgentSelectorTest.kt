package com.smassistant.app.agents

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
import com.smassistant.app.data.MediaPurpose
import com.smassistant.app.net.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class NamedAgent(override val service: ApiService) : TextAgent {
    override suspend fun generate(system: String, user: String) = ApiResult.Ok(service.name)
}

class AgentSelectorTest {
    private fun selector(configured: Set<ApiService>, preferred: ApiService? = null) = AgentSelector(
        configOf = { if (it in configured) ApiConfig("https://x", "k", "m") else ApiConfig("https://x", "", "m") },
        preferred = { preferred },
        agentFor = ::NamedAgent,
    )

    @Test
    fun `uses the preferred agent when it is set up`() {
        val all = setOf(ApiService.CLOUDFLARE, ApiService.GEMINI, ApiService.GROQ)
        assertEquals(ApiService.GROQ, selector(all, preferred = ApiService.GROQ).textAgent()?.service)
    }

    @Test
    fun `falls back to the first set-up agent`() {
        assertEquals(ApiService.GEMINI, selector(setOf(ApiService.GEMINI, ApiService.GROQ), ApiService.CLOUDFLARE).textAgent()?.service)
        assertEquals(ApiService.GROQ, selector(setOf(ApiService.CLOUDFLARE, ApiService.GROQ)).textAgent()?.service)
    }

    @Test
    fun `none set up gives null`() {
        assertNull(selector(emptySet(), ApiService.GEMINI).textAgent())
    }

    @Test
    fun `an agent without a model is not usable`() {
        assertEquals(false, ApiConfig("https://x", "k", "").isUsableAgent())
        assertEquals(false, ApiConfig("", "k", "m").isUsableAgent())
        assertEquals(true, ApiConfig("https://x", "k", "m").isUsableAgent())
    }
}

class ImageAgentSelectorTest {
    private fun named(agent: ApiService) = object : ImageAgent {
        override val service = agent
        override suspend fun generate(prompt: String, count: Int) = ApiResult.Ok(emptyList<java.io.File>())
    }

    private fun selector(configured: Set<ApiService>, preferred: ApiService? = null) = ImageAgentSelector(
        configOf = { if (it in configured) ApiConfig("https://x", "k", "m", "image-model") else ApiConfig("https://x", "", "m", "image-model") },
        preferred = { preferred },
        agentFor = ::named,
    )

    @Test
    fun `Gemini is the default and Cloudflare is used when chosen or alone`() {
        val both = setOf(ApiService.GEMINI, ApiService.CLOUDFLARE)
        assertEquals(ApiService.GEMINI, selector(both).imageAgent()?.service)
        assertEquals(ApiService.CLOUDFLARE, selector(both, preferred = ApiService.CLOUDFLARE).imageAgent()?.service)
        assertEquals(ApiService.CLOUDFLARE, selector(setOf(ApiService.CLOUDFLARE)).imageAgent()?.service)
        assertEquals(ApiService.GEMINI, selector(setOf(ApiService.GEMINI), preferred = ApiService.CLOUDFLARE).imageAgent()?.service)
    }

    @Test
    fun `none set up gives null`() {
        assertNull(selector(setOf(ApiService.GROQ)).imageAgent())
    }

    @Test
    fun `Gemini needs an image model and text-only agents can't make images`() {
        assertEquals(false, ApiConfig("https://x", "k", "m", "").isUsableImageAgent(ApiService.GEMINI))
        assertEquals(true, ApiConfig("https://x", "k", "", "img").isUsableImageAgent(ApiService.GEMINI))
        assertEquals(false, ApiConfig("https://x", "k", "m", "img").isUsableImageAgent(ApiService.GROQ))
    }
    @Test
    fun `new agents can make images once they have an image model`() {
        listOf(ApiService.CLOUDFLARE, ApiService.POLLINATIONS).forEach { service ->
            assertEquals(service.name, true, ApiConfig("https://x", "k", "", "img").isUsableImageAgent(service))
            assertEquals(service.name, false, ApiConfig("https://x", "k", "m", "").isUsableImageAgent(service))
            assertEquals(service.name, false, ApiConfig("https://x", "", "m", "img").isUsableImageAgent(service))
        }
        assertEquals(false, ApiConfig("https://x/{account_id}/ai", "k", "m", "img").isUsableImageAgent(ApiService.CLOUDFLARE))
        assertEquals(true, ApiConfig("https://x/{account_id}/ai", "k", "m", "img", accountId = "id").isUsableImageAgent(ApiService.CLOUDFLARE))
    }
}

@RunWith(RobolectricTestRunner::class)
class AgentPreferencesTest {
    @Test
    fun `default text agent persists and ignores unknown values`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = AgentPreferences(context)
        assertNull(prefs.defaultTextAgent)

        prefs.defaultTextAgent = ApiService.GEMINI
        assertEquals(ApiService.GEMINI, AgentPreferences(context).defaultTextAgent)

        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE).edit()
            .putString("default_text_agent", "LINKEDIN").commit()
        assertNull(AgentPreferences(context).defaultTextAgent)
    }

    @Test
    fun `default image agent persists separately from the text one`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = AgentPreferences(context)
        assertNull(prefs.defaultImageAgent)

        prefs.defaultTextAgent = ApiService.GROQ
        prefs.defaultImageAgent = ApiService.POLLINATIONS
        assertEquals(ApiService.POLLINATIONS, AgentPreferences(context).defaultImageAgent)
        assertEquals(ApiService.GROQ, AgentPreferences(context).defaultTextAgent)

        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE).edit()
            .putString("default_image_agent", "GROQ").commit()
        assertNull(AgentPreferences(context).defaultImageAgent)
    }

    @Test
    fun `default video agent follows the image agent until it is chosen`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = AgentPreferences(context)
        assertNull(prefs.defaultVideoAgent)

        prefs.defaultImageAgent = ApiService.POLLINATIONS
        assertEquals(ApiService.POLLINATIONS, prefs.defaultMediaAgent(MediaPurpose.VIDEO))

        prefs.setDefaultMediaAgent(MediaPurpose.VIDEO, ApiService.GEMINI)
        assertEquals(ApiService.GEMINI, AgentPreferences(context).defaultVideoAgent)
        assertEquals(ApiService.POLLINATIONS, AgentPreferences(context).defaultMediaAgent(MediaPurpose.IMAGE))
    }

    @Test
    fun `a saved Claude default is ignored now that Claude is gone`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE).edit()
            .putString("default_text_agent", "CLAUDE")
            .putString("default_image_agent", "CLAUDE")
            .putString("default_video_agent", "CLAUDE")
            .commit()
        val prefs = AgentPreferences(context)
        assertNull(prefs.defaultTextAgent)
        assertNull(prefs.defaultImageAgent)
        assertNull(prefs.defaultVideoAgent)
    }
}

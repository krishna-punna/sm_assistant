package com.smassistant.app.agents

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smassistant.app.data.ApiConfig
import com.smassistant.app.data.ApiService
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
        val all = setOf(ApiService.CLAUDE, ApiService.GEMINI, ApiService.GROQ)
        assertEquals(ApiService.GROQ, selector(all, preferred = ApiService.GROQ).textAgent()?.service)
    }

    @Test
    fun `falls back to the first set-up agent`() {
        assertEquals(ApiService.GEMINI, selector(setOf(ApiService.GEMINI, ApiService.GROQ), ApiService.CLAUDE).textAgent()?.service)
        assertEquals(ApiService.CLAUDE, selector(setOf(ApiService.CLAUDE, ApiService.GROQ)).textAgent()?.service)
    }

    @Test
    fun `none set up gives null`() {
        assertNull(selector(emptySet(), ApiService.CLAUDE).textAgent())
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
    fun `Gemini is the default and Claude is used when chosen or alone`() {
        val both = setOf(ApiService.GEMINI, ApiService.CLAUDE)
        assertEquals(ApiService.GEMINI, selector(both).imageAgent()?.service)
        assertEquals(ApiService.CLAUDE, selector(both, preferred = ApiService.CLAUDE).imageAgent()?.service)
        assertEquals(ApiService.CLAUDE, selector(setOf(ApiService.CLAUDE)).imageAgent()?.service)
        assertEquals(ApiService.GEMINI, selector(setOf(ApiService.GEMINI), preferred = ApiService.CLAUDE).imageAgent()?.service)
    }

    @Test
    fun `none set up gives null`() {
        assertNull(selector(setOf(ApiService.GROQ)).imageAgent())
    }

    @Test
    fun `Gemini needs an image model and Claude a text model`() {
        assertEquals(false, ApiConfig("https://x", "k", "m", "").isUsableImageAgent(ApiService.GEMINI))
        assertEquals(true, ApiConfig("https://x", "k", "", "img").isUsableImageAgent(ApiService.GEMINI))
        assertEquals(false, ApiConfig("https://x", "k", "", "").isUsableImageAgent(ApiService.CLAUDE))
        assertEquals(true, ApiConfig("https://x", "k", "m").isUsableImageAgent(ApiService.CLAUDE))
        assertEquals(false, ApiConfig("https://x", "k", "m", "img").isUsableImageAgent(ApiService.GROQ))
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
        prefs.defaultImageAgent = ApiService.CLAUDE
        assertEquals(ApiService.CLAUDE, AgentPreferences(context).defaultImageAgent)
        assertEquals(ApiService.GROQ, AgentPreferences(context).defaultTextAgent)

        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE).edit()
            .putString("default_image_agent", "GROQ").commit()
        assertNull(AgentPreferences(context).defaultImageAgent)
    }
}

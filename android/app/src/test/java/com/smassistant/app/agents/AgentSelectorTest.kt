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
}

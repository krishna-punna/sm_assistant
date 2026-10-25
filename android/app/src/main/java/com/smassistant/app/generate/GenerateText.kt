package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.loadPromptAsset

/** Sends the user's prompt with a feature's system prompt to the selected text agent (TC-I1, AGC-4). */
class GenerateTextUseCase(
    private val systemPrompt: String,
    private val agent: () -> TextAgent?,
) {
    suspend operator fun invoke(prompt: String): Outcome<String> {
        val selected = agent() ?: return Outcome.NoAgent
        return when (val result = selected.generate(systemPrompt, prompt)) {
            is ApiResult.Ok -> Outcome.Ok(result.value)
            is ApiResult.Failed -> Outcome.Failed(result.error, selected.service.displayName)
        }
    }
}

/** The technical post generator the screens use (TC-I1): `technical_content.md` with the selected text agent. */
fun appGenerateTechnicalContent(context: Context): GenerateTextUseCase =
    GenerateTextUseCase(loadPromptAsset(context, "technical_content"), appAgentSelector(context)::textAgent)

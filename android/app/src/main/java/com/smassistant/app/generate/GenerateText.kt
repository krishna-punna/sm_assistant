package com.smassistant.app.generate

import android.content.Context
import com.smassistant.app.agents.TextAgent
import com.smassistant.app.agents.appAgentSelector
import com.smassistant.app.chat.Outcome
import com.smassistant.app.data.AppConfig
import com.smassistant.app.hashtags.HashtagSettingsStore
import com.smassistant.app.net.ApiResult
import com.smassistant.app.prompt.loadPromptAsset

/**
 * Sends the user's prompt with a feature's system prompt to the selected text agent (TC-I1, AGC-4).
 * With a [lengthGuard], the prompt carries the post's character budget and a reply that's still
 * over LinkedIn's limit is sent back to be shortened (docs/requirements/12-post-length.md).
 */
class GenerateTextUseCase(
    private val systemPrompt: String,
    private val agent: () -> TextAgent?,
    private val lengthGuard: PostLengthGuard? = null,
) {
    suspend operator fun invoke(prompt: String): Outcome<String> {
        val selected = agent() ?: return Outcome.NoAgent
        val system = lengthGuard?.let { systemPrompt.trimEnd() + "\n\n" + it.instructions() } ?: systemPrompt
        val text = when (val result = selected.generate(system, prompt)) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failed -> return Outcome.Failed(result.error, selected.service.displayName)
        }
        return Outcome.Ok(lengthGuard?.let { fit(selected, system, text, it) } ?: text)
    }

    /**
     * Asks the agent to shorten [text] until it fits, up to [PostLengthGuard.shortenAttempts] times (PL-6).
     * Returns the shortest version it got; if none fits, the screen shows how far over it is (PL-7).
     */
    private suspend fun fit(agent: TextAgent, system: String, text: String, guard: PostLengthGuard): String {
        var best = text
        var bestOver = guard.measure(text).overLimitBy
        repeat(guard.shortenAttempts) {
            if (bestOver == 0) return best
            val shorter = agent.generate(system, guard.shortenRequest(best, guard.measure(best)))
            if (shorter !is ApiResult.Ok) return best
            val over = guard.measure(shorter.value).overLimitBy
            if (over < bestOver) {
                best = shorter.value
                bestOver = over
            }
        }
        return best
    }
}

/**
 * The technical post generator the screens use (TC-I1): `technical_content.md` with the selected
 * text agent, kept within LinkedIn's limit including the saved default hashtags, and ending with
 * a few AI-suggested hashtags when that toggle is on (HT-21).
 */
fun appGenerateTechnicalContent(context: Context): GenerateTextUseCase {
    val hashtags = HashtagSettingsStore(context)
    val rules = AppConfig.postRules(context)
    return GenerateTextUseCase(
        loadPromptAsset(context, "technical_content"),
        appAgentSelector(context)::textAgent,
        PostLengthGuard(
            { rules.maxCharacters },
            { hashtags.tags },
            { hashtags.enabled },
            rules.shortenAttempts,
            aiHashtags = { if (hashtags.aiEnabled) rules.aiHashtags else 0 },
        ),
    )
}

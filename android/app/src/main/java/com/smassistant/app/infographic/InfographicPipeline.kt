package com.smassistant.app.infographic

import com.smassistant.app.agents.ImageAgent
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** At most this many AI illustrations per infographic, to keep image calls close to the old flow. */
const val MAX_ILLUSTRATIONS = 2

/**
 * Draws a [RenderedInfographic] into a high-resolution PNG, painting each illustration over its
 * slot ([illustrations] is in slot order; null keeps the placeholder). Null when it can't be drawn.
 */
fun interface InfographicComposer {
    suspend fun compose(rendered: RenderedInfographic, illustrations: List<File?>): File?
}

/**
 * The drawing half of the pipeline (docs/design/image-video-pipeline.md): render the definition,
 * ask [illustrator] for the first [maxIllustrations] slots' pictures in parallel (with the house
 * style from `prompts/illustration.md`), and compose them. A failed illustration keeps its
 * placeholder, and without an illustrator the image is drawn from the definition alone.
 */
class InfographicPipeline(
    private val renderer: InfographicRenderer,
    private val illustrationTemplate: String,
    private val composer: InfographicComposer,
    private val maxIllustrations: Int = MAX_ILLUSTRATIONS,
) {
    suspend fun draw(spec: InfographicSpec, illustrator: ImageAgent?): File? {
        val rendered = renderer.render(spec)
        val painted = if (illustrator == null) {
            emptyList()
        } else {
            coroutineScope {
                rendered.slots.take(maxIllustrations).map { slot ->
                    async {
                        when (val result = illustrator.generate(illustrationPrompt(slot.prompt, spec.palette), 1)) {
                            is ApiResult.Ok -> result.value.firstOrNull()
                            is ApiResult.Failed -> null
                        }
                    }
                }.awaitAll()
            }
        }
        return composer.compose(rendered, rendered.slots.indices.map { painted.getOrNull(it) })
    }

    internal fun illustrationPrompt(prompt: String, palette: Palette) = illustrationTemplate
        .replace("{{prompt}}", prompt.trim().trimEnd('.'))
        .replace("{{colors}}", palette.colorWords)
}

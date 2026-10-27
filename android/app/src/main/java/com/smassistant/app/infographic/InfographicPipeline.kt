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
 *
 * With an [inspector], every illustration is checked before it is painted in (a blank, transparent
 * or tiny one is painted once more, then left as the placeholder), and the finished PNG is checked
 * too: one that is blank or smaller than [minSide] isn't returned.
 */
class InfographicPipeline(
    private val renderer: InfographicRenderer,
    private val illustrationTemplate: String,
    private val composer: InfographicComposer,
    private val maxIllustrations: Int = MAX_ILLUSTRATIONS,
    private val inspector: ImageInspector? = null,
    private val minSide: Int = MIN_INFOGRAPHIC_SIDE,
) {
    suspend fun draw(spec: InfographicSpec, illustrator: ImageAgent?, maxIllustrations: Int = this.maxIllustrations): File? {
        val rendered = renderer.render(spec)
        val painted = if (illustrator == null) {
            emptyList()
        } else {
            coroutineScope {
                rendered.slots.take(maxIllustrations).map { slot ->
                    async { illustrate(illustrator, illustrationPrompt(slot.prompt, spec.palette)) }
                }.awaitAll()
            }
        }
        val png = composer.compose(rendered, rendered.slots.indices.map { painted.getOrNull(it) }) ?: return null
        return png.takeIf { usable(it, minSide) }
    }

    /** One illustration, asked for once more when the first one comes back unusable. */
    private suspend fun illustrate(illustrator: ImageAgent, prompt: String): File? {
        repeat(2) {
            val file = when (val result = illustrator.generate(prompt, 1)) {
                is ApiResult.Ok -> result.value.firstOrNull()
                is ApiResult.Failed -> return null
            }
            if (file != null && usable(file, MIN_ILLUSTRATION_SIDE)) return file
        }
        return null
    }

    private suspend fun usable(file: File, minSide: Int) = inspector == null || imageProblem(inspector.facts(file), minSide) == null

    internal fun illustrationPrompt(prompt: String, palette: Palette) = illustrationTemplate
        .replace("{{prompt}}", prompt.trim().trimEnd('.'))
        .replace("{{colors}}", palette.colorWords)
}

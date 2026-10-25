package com.smassistant.app.agents

import com.smassistant.app.data.ApiService
import com.smassistant.app.media.MediaCache
import com.smassistant.app.net.ApiError
import com.smassistant.app.net.ApiResult
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Claude's reply budget for one SVG: big cheat-sheet layouts run to several thousand tokens. */
const val SVG_MAX_TOKENS = 16000

/** Draws SVG markup to PNG bytes, or null when the markup can't be drawn. */
fun interface SvgRenderer {
    fun render(svg: String): ByteArray?
}

/**
 * Claude can't paint pictures, so it writes each image as SVG code ([systemPrompt] says how),
 * which [renderer] turns into a PNG in [cache]. [count] calls run in parallel, each its own
 * design. Returns the images that worked; fails only if none did.
 */
class ClaudeSvgImageAgent(
    private val text: TextAgent,
    private val systemPrompt: String,
    private val cache: MediaCache,
    private val renderer: SvgRenderer,
) : ImageAgent {
    override val service = ApiService.CLAUDE

    override suspend fun generate(prompt: String, count: Int): ApiResult<List<File>> {
        val results = coroutineScope { List(count) { async { generateOne(prompt) } }.awaitAll() }
        val files = results.filterIsInstance<ApiResult.Ok<File>>().map { it.value }
        return if (files.isNotEmpty()) ApiResult.Ok(files) else results.first() as ApiResult.Failed
    }

    private suspend fun generateOne(prompt: String): ApiResult<File> {
        val reply = when (val result = text.generate(systemPrompt, prompt)) {
            is ApiResult.Failed -> return result
            is ApiResult.Ok -> result.value
        }
        val svg = extractSvg(reply) ?: return ApiResult.Failed(
            ApiError.BadResponse(if (reply.contains("<svg")) "the SVG was cut off" else "no SVG in the reply"),
        )
        val png = renderer.render(svg) ?: return ApiResult.Failed(ApiError.BadResponse("the SVG couldn't be drawn"))
        return ApiResult.Ok(cache.save(png, "png"))
    }
}

/** The `<svg …>…</svg>` element in a reply, ignoring any code fence or words around it. Null if it's missing or unfinished. */
internal fun extractSvg(reply: String): String? {
    val start = reply.indexOf("<svg")
    val end = reply.lastIndexOf("</svg>")
    return if (start < 0 || end < start) null else reply.substring(start, end + "</svg>".length)
}

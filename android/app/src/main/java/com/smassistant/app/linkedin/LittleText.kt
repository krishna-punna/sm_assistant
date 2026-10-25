package com.smassistant.app.linkedin

private val HASHTAG = Regex("(?<![\\p{L}\\p{N}_&])#([\\p{L}\\p{N}_]+)")
private const val RESERVED = "\\|{}@[]()<>#*_~"

/**
 * Encodes plain text for the `commentary` field of LinkedIn's Posts API, which uses the
 * "little text" format: reserved characters must be backslash-escaped or the post is cut
 * short at the first one, and hashtags become `{hashtag|\#|tag}` so they link.
 */
fun toLittleText(text: String): String {
    val out = StringBuilder()
    var last = 0
    for (match in HASHTAG.findAll(text)) {
        escapeInto(out, text.substring(last, match.range.first))
        out.append("{hashtag|\\#|")
        escapeInto(out, match.groupValues[1])
        out.append('}')
        last = match.range.last + 1
    }
    escapeInto(out, text.substring(last))
    return out.toString()
}

private fun escapeInto(out: StringBuilder, text: String) {
    text.forEach { c ->
        if (c in RESERVED) out.append('\\')
        out.append(c)
    }
}

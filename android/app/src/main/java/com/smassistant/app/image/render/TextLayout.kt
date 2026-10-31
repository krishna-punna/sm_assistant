package com.smassistant.app.image.render

/** Font families the renderer uses; SVG has no text wrapping, so lines are broken by measuring. */
enum class FontFamily(val svg: String) { SANS("sans-serif"), MONO("monospace") }

/** Width of [text] at [size] in SVG units. On the device this measures with the real font. */
fun interface TextMeasurer {
    fun width(text: String, size: Float, bold: Boolean, family: FontFamily): Float
}

/**
 * Estimates text width from character classes, close to Android's default sans-serif. Used in
 * tests and as a fallback; it errs on the wide side so lines don't overflow.
 */
object ApproxTextMeasurer : TextMeasurer {
    override fun width(text: String, size: Float, bold: Boolean, family: FontFamily): Float {
        if (family == FontFamily.MONO) return text.length * size * 0.61f
        val em = text.sumOf { c ->
            when {
                c == ' ' -> 0.28
                c in "iljI.,:;'|!" -> 0.28
                c in "ftr()[]{}-\"" -> 0.38
                c in "mwMW@" -> 0.88
                c.isUpperCase() -> 0.68
                c.isDigit() -> 0.56
                c.code > 0x2000 -> 1.0
                else -> 0.55
            }
        }
        return (em * size * if (bold) 1.06 else 1.0).toFloat()
    }
}

/**
 * Breaks [text] into lines no wider than [maxWidth], at spaces where possible and inside long
 * words otherwise. Keeps the caller's line breaks. After [maxLines] the last line ends in "…".
 */
fun wrapText(
    text: String,
    maxWidth: Float,
    size: Float,
    measurer: TextMeasurer,
    bold: Boolean = false,
    family: FontFamily = FontFamily.SANS,
    maxLines: Int = Int.MAX_VALUE,
): List<String> {
    fun fits(s: String) = measurer.width(s, size, bold, family) <= maxWidth
    val lines = mutableListOf<String>()
    for (paragraph in text.split('\n')) {
        var line = ""
        for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (fits(candidate)) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) lines += line
            line = word
            while (!fits(line) && line.length > 1) {
                var cut = line.length - 1
                while (cut > 1 && !fits(line.substring(0, cut))) cut--
                lines += line.substring(0, cut)
                line = line.substring(cut)
            }
        }
        lines += line
    }
    if (lines.size <= maxLines) return lines
    val kept = lines.take(maxLines).toMutableList()
    var last = kept.last().trimEnd()
    while (last.isNotEmpty() && !fits("$last…")) last = last.dropLast(1).trimEnd()
    kept[kept.lastIndex] = "$last…"
    return kept
}

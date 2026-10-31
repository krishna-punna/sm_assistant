package com.smassistant.app.image.render

/**
 * The poster text standard (AC-10): every word the renderer draws goes through these rules, so a
 * model's spacing or casing slips never reach the poster. Titles and card headings are title case,
 * other text is sentence case, nothing shouts in ALL CAPS, words keep one real space between them,
 * and ellipses and dashes are the real characters. Code is never touched.
 */
object Typography {
    /** Spacing and punctuation only: one plain space between words, no invisible characters, real ellipses and dashes. */
    fun clean(text: String): String = text
        .replace(INVISIBLE, "")
        .replace(ODD_SPACE, " ")
        .replace(Regex(" {2,}"), " ")
        .replace("...", "…")
        .replace(" -- ", " — ")
        .replace(" - ", " – ")
        .trim()

    /** A title or card heading: each word capitalised except short joining words, acronyms and brand casing kept. */
    fun title(text: String): String {
        val words = calm(clean(text)).split(' ')
        return words.mapIndexed { i, word ->
            val bare = word.trim { !it.isLetterOrDigit() }.lowercase()
            if (i > 0 && i < words.lastIndex && bare in SMALL_WORDS && word == word.lowercase()) word else capitalised(word)
        }.joinToString(" ")
    }

    /** Body text: the first letter capitalised, the rest as written, without ALL CAPS shouting. */
    fun sentence(text: String): String {
        val calm = calm(clean(text))
        val first = calm.indexOfFirst { it.isLetter() }
        if (first < 0) return calm
        val word = calm.substring(first).substringBefore(' ')
        // Leave words written with their own casing ("iOS", "gRPC", "kubectl") as they are.
        return if (word != word.lowercase() || word.any { it.isDigit() } || word in LOWER_TERMS) calm
        else calm.substring(0, first) + calm[first].uppercaseChar() + calm.substring(first + 1)
    }

    /**
     * Text written in capitals is turned back into normal case, word by word, when most of its long
     * words are capitals; short all-capital words are acronyms (API, HTTP, JSON, CQRS) and stay,
     * except joining words such as "TO" and "AND".
     */
    private fun calm(text: String): String {
        val words = text.split(' ')
        val long = words.filter { w -> w.count { it.isLetter() } >= SHOUT_LENGTH }
        val shouting = long.count { w -> w.any { it.isLetter() } && w == w.uppercase() }
        if (long.isEmpty() || shouting * 2 < long.size) return text
        return words.joinToString(" ") { w ->
            val shouted = w == w.uppercase() && w.any { it.isLetter() }
            if (shouted && (w.count { it.isLetter() } >= SHOUT_LENGTH || w.lowercase() in SMALL_WORDS)) w.lowercase() else w
        }
    }

    private fun capitalised(word: String): String {
        val first = word.indexOfFirst { it.isLetter() }
        if (first < 0 || word[first].isUpperCase()) return word
        // "iOS", "gRPC", "eBPF": mixed casing that starts lower case is the term's own spelling.
        if (word.drop(first + 1).any { it.isUpperCase() } || word.lowercase() in LOWER_TERMS) return word
        return word.substring(0, first) + word[first].uppercaseChar() + word.substring(first + 1)
    }

    private val INVISIBLE = Regex("[\u200B\u200C\u200D\u2060\uFEFF\u00AD]")
    private val ODD_SPACE = Regex("[\\t\\r\u00A0\u1680\u2000-\u200A\u202F\u205F\u3000]+")
    private const val SHOUT_LENGTH = 5
    private val SMALL_WORDS = setOf("a", "an", "the", "and", "or", "nor", "but", "of", "in", "on", "to", "for", "at", "by", "vs", "via", "with", "as", "per")

    /** Terms whose proper spelling is lower case, left as they are wherever they stand. */
    private val LOWER_TERMS = setOf("npm", "kubectl", "git", "curl", "bash", "pip", "yarn", "sudo")
}

/**
 * [spec] with the text standard applied to every drawn word ([Typography]): the title and card
 * headings in title case, the subtitle, items, captions and takeaway in sentence case, labels and
 * table cells with clean spacing. Code and the highlight words are left as written.
 */
fun typeset(spec: InfographicSpec): InfographicSpec = spec.copy(
    title = Typography.title(spec.title),
    subtitle = spec.subtitle?.let(Typography::sentence),
    takeaway = spec.takeaway?.let(Typography::sentence),
    sections = spec.sections.map { section -> section.copy(heading = Typography.title(section.heading), body = typeset(section.body)) },
)

private fun typeset(body: SectionBody): SectionBody {
    val s = Typography::sentence
    val c = Typography::clean
    return when (body) {
        is SectionBody.Bullets -> body.copy(items = body.items.map(s))
        is SectionBody.Steps -> body.copy(items = body.items.map(s))
        is SectionBody.ProsCons -> body.copy(pros = body.pros.map(s), cons = body.cons.map(s))
        is SectionBody.Code -> body
        is SectionBody.Table -> body.copy(columns = body.columns.map(c), rows = body.rows.map { it.map(c) })
        is SectionBody.Flow -> body.copy(nodes = body.nodes.map(c))
        is SectionBody.Compare -> body.copy(
            left = body.left.copy(label = c(body.left.label), items = body.left.items.map(s)),
            right = body.right.copy(label = c(body.right.label), items = body.right.items.map(s)),
        )
        is SectionBody.Stats -> body.copy(items = body.items.map { it.copy(value = c(it.value), label = c(it.label)) })
        is SectionBody.Illustration -> body.copy(caption = body.caption?.let(s))
        is SectionBody.Chart -> body.copy(bars = body.bars.map { it.copy(label = c(it.label)) }, unit = body.unit?.let(c))
        is SectionBody.Diagram -> body.copy(
            nodes = body.nodes.map { it.copy(label = c(it.label), group = it.group?.let(c)) },
            edges = body.edges.map { it.copy(label = it.label?.let(c)) },
        )
    }
}

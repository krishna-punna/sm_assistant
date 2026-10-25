package com.smassistant.app.markdown

/** A block of the Markdown subset the agents produce (docs/requirements/06, TC-1). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    /** One or more lines; inline markup is kept for [parseInline]. */
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    /** [start] is the first number of an ordered list, null for bullets. */
    data class ListBlock(val items: List<String>, val start: Int?) : MdBlock
    data class Code(val language: String, val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

/** A run of text with one set of styles. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
)

private val HEADING = Regex("^(#{1,6})\\s+(.*?)(\\s+#+)?\\s*$")
private val BULLET = Regex("^\\s*[-*+]\\s+(.*)$")
private val ORDERED = Regex("^\\s*(\\d{1,9})[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val FENCE = Regex("^\\s*(```|~~~)\\s*(\\S*).*$")
private val TABLE_SEPARATOR = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

/** Splits Markdown into blocks. Anything it doesn't recognise is a paragraph. */
fun parseMarkdown(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            FENCE.matches(line) -> {
                val match = FENCE.find(line)!!
                val fence = match.groupValues[1]
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) body += lines[i++]
                i++ // closing fence, if any
                blocks += MdBlock.Code(match.groupValues[2], body.joinToString("\n"))
            }
            HEADING.matches(line) -> {
                val match = HEADING.find(line)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                i++
            }
            RULE.matches(line) -> {
                blocks += MdBlock.Rule
                i++
            }
            line.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) && lines[i + 1].contains('-') -> {
                val header = cells(line)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows += cells(lines[i++])
                blocks += MdBlock.Table(header, rows)
            }
            BULLET.matches(line) || ORDERED.matches(line) -> {
                val ordered = ORDERED.find(line)
                val pattern = if (ordered != null) ORDERED else BULLET
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val item = pattern.find(lines[i])
                    when {
                        item != null -> items += item.groupValues.last()
                        // An indented line continues the previous item.
                        lines[i].startsWith("  ") && lines[i].isNotBlank() && items.isNotEmpty() ->
                            items[items.lastIndex] = items.last() + " " + lines[i].trim()
                        else -> break
                    }
                    i++
                }
                blocks += MdBlock.ListBlock(items, ordered?.groupValues?.get(1)?.toInt())
            }
            line.trimStart().startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i++].trimStart().removePrefix(">").trimStart()
                }
                blocks += MdBlock.Quote(quote.joinToString("\n"))
            }
            else -> {
                val paragraph = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines, i)) paragraph += lines[i++].trim()
                if (paragraph.isEmpty()) paragraph += lines[i++].trim()
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
            }
        }
    }
    return blocks
}

private fun startsBlock(lines: List<String>, i: Int): Boolean {
    val line = lines[i]
    return FENCE.matches(line) || HEADING.matches(line) || RULE.matches(line) ||
        BULLET.matches(line) || ORDERED.matches(line) || line.trimStart().startsWith(">") ||
        (line.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) && lines[i + 1].contains('-'))
}

private fun cells(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

private val INLINE = Regex("`([^`]+)`|\\*\\*(.+?)\\*\\*|__(.+?)__|\\[([^\\]]+)]\\(([^)\\s]+)\\)|\\*([^*\\s][^*]*?)\\*")

/** Splits inline Markdown (`code`, **bold**, *italic*, [link](url)) into styled spans. */
fun parseInline(text: String): List<MdSpan> = inline(text, MdSpan(""))

private fun inline(text: String, style: MdSpan): List<MdSpan> {
    val spans = mutableListOf<MdSpan>()
    var last = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first > last) spans += style.copy(text = text.substring(last, match.range.first))
        val (code, bold, bold2, linkText, url, italic) = match.destructured
        spans += when {
            code.isNotEmpty() -> listOf(style.copy(text = code, code = true))
            bold.isNotEmpty() -> inline(bold, style.copy(bold = true))
            bold2.isNotEmpty() -> inline(bold2, style.copy(bold = true))
            linkText.isNotEmpty() -> inline(linkText, style.copy(url = url))
            else -> inline(italic, style.copy(italic = true))
        }
        last = match.range.last + 1
    }
    if (last < text.length) spans += style.copy(text = text.substring(last))
    return spans
}

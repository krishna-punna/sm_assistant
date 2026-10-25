package com.smassistant.app.markdown

/**
 * Turns an agent's Markdown into plain text for a LinkedIn post, which shows Markdown
 * literally (docs/tasks TC-I3, LP-NFR): no `#`, `**`, backticks or table pipes in the output.
 */
object MarkdownToLinkedIn {
    fun convert(markdown: String): String = parseMarkdown(markdown).joinToString("\n\n") { block ->
        when (block) {
            is MdBlock.Heading -> plain(block.text)
            is MdBlock.Paragraph -> plain(block.text)
            is MdBlock.Quote -> plain(block.text)
            is MdBlock.ListBlock -> block.items.mapIndexed { index, item ->
                val marker = block.start?.let { "${it + index}." } ?: "•"
                "$marker ${plain(item)}"
            }.joinToString("\n")
            is MdBlock.Code -> block.text
            is MdBlock.Table -> table(block)
            MdBlock.Rule -> "―――"
        }
    }.trim()

    /** A two-column table reads "• Availability: 99.99%"; wider rows add "Header: value" pairs. */
    private fun table(table: MdBlock.Table): String {
        val header = table.header.map(::plain)
        if (table.rows.isEmpty()) return header.joinToString(" · ")
        return table.rows.joinToString("\n") { row ->
            val cells = row.map(::plain)
            val rest = cells.drop(1)
            val details = if (rest.size <= 1) {
                rest.joinToString("")
            } else {
                rest.mapIndexed { index, cell ->
                    header.getOrNull(index + 1)?.takeIf { it.isNotBlank() }?.let { "$it: $cell" } ?: cell
                }.joinToString(" · ")
            }
            val first = cells.firstOrNull().orEmpty()
            if (details.isBlank()) "• $first" else "• $first: $details"
        }
    }

    /** Drops inline markup; a link keeps its URL after the text, as LinkedIn shows bare URLs as links. */
    private fun plain(text: String): String {
        val spans = parseInline(text)
        return buildString {
            spans.forEachIndexed { index, span ->
                append(span.text)
                val url = span.url
                if (url != null && spans.getOrNull(index + 1)?.url != url && url != span.text) append(" ($url)")
            }
        }
    }
}

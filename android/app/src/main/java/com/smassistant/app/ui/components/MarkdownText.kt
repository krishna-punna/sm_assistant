package com.smassistant.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.smassistant.app.markdown.MdBlock
import com.smassistant.app.markdown.parseInline
import com.smassistant.app.markdown.parseMarkdown

/** Renders an agent's Markdown: headings, lists, tables, code and inline styles (TC-1). */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { MarkdownBlock(it) }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
    val typography = MaterialTheme.typography
    when (block) {
        is MdBlock.Heading -> Text(
            inlineText(block.text),
            style = when (block.level) {
                1 -> typography.titleLarge
                2 -> typography.titleMedium
                else -> typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
        )
        is MdBlock.Paragraph -> Text(inlineText(block.text), style = typography.bodyMedium)
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Surface(color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(3.dp).fillMaxHeight()) {}
            Text(inlineText(block.text), style = typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 8.dp))
        }
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(block.start?.let { "${it + index}." } ?: "•", style = typography.bodyMedium, modifier = Modifier.width(24.dp))
                    Text(inlineText(item), style = typography.bodyMedium)
                }
            }
        }
        is MdBlock.Code -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                block.text,
                fontFamily = FontFamily.Monospace,
                style = typography.bodySmall,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            )
        }
        is MdBlock.Table -> MarkdownTable(block)
        MdBlock.Rule -> HorizontalDivider()
    }
}

@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    val border = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, border)) {
        (listOf(table.header) + table.rows).forEachIndexed { rowIndex, row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                for (column in 0 until columns) {
                    Text(
                        inlineText(row.getOrElse(column) { "" }),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (rowIndex == 0) FontWeight.Bold else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .border(0.5.dp, border)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun inlineText(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, codeBackground, linkColor) {
        buildAnnotatedString {
            parseInline(text).forEach { span ->
                val style = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) codeBackground else Color.Unspecified,
                )
                val url = span.url
                if (url != null) {
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                        withStyle(style) { append(span.text) }
                    }
                } else {
                    withStyle(style) { append(span.text) }
                }
            }
        }
    }
}

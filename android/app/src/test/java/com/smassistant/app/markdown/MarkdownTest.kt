package com.smassistant.app.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTest {
    @Test
    fun `parses headings, lists, tables, code, quotes and rules`() {
        val blocks = parseMarkdown(
            """
            # Title
            ## Learn C#
            Line one
            line two

            1. First
            2. Second
               continued
            - Bullet

            | Requirement | Target |
            |---|:--:|
            | Availability | 99.99% |

            ```kotlin
            val x = 1
            ```
            > Quoted
            ---
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"),
                MdBlock.Heading(2, "Learn C#"),
                MdBlock.Paragraph("Line one\nline two"),
                MdBlock.ListBlock(listOf("First", "Second continued"), start = 1),
                MdBlock.ListBlock(listOf("Bullet"), start = null),
                MdBlock.Table(listOf("Requirement", "Target"), listOf(listOf("Availability", "99.99%"))),
                MdBlock.Code("kotlin", "val x = 1"),
                MdBlock.Quote("Quoted"),
                MdBlock.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun `hashtags and a pipe without a separator row stay in the paragraph`() {
        assertEquals(listOf(MdBlock.Paragraph("#systemdesign #java\na | b")), parseMarkdown("#systemdesign #java\na | b"))
    }

    @Test
    fun `an unclosed code fence runs to the end`() {
        assertEquals(listOf(MdBlock.Code("", "a\n# not a heading")), parseMarkdown("```\na\n# not a heading"))
    }

    @Test
    fun `inline markup becomes styled spans`() {
        assertEquals(
            listOf(
                MdSpan("a "),
                MdSpan("b ", bold = true),
                MdSpan("c", bold = true, italic = true),
                MdSpan(" d", bold = true),
                MdSpan(" "),
                MdSpan("link", url = "https://x.io"),
                MdSpan(" "),
                MdSpan("**z**", code = true),
                MdSpan(" 2 * 3 * 4"),
            ),
            parseInline("a **b *c* d** [link](https://x.io) `**z**` 2 * 3 * 4"),
        )
    }
}

class MarkdownToLinkedInTest {
    private val sample = """
        # System Design: Short URL Service

        ## 1. Problem Statement
        Turn **long** URLs like `https://example.com/a` into short ones, e.g. [sho.rt](https://sho.rt/aB91xZ).

        ### Functional Requirements
        1. Create Short URL
        2. *Custom* Alias

        - Read-heavy

        | Requirement | Target |
        |---|---|
        | Availability | 99.99% |
        | Redirect latency | < 100 ms |

        | Part | Tech | Why |
        |---|---|---|
        | Cache | Redis | Hot keys |

        ```
        GET /{code} -> 301
        ```

        #systemdesign #java
    """.trimIndent()

    @Test
    fun `converts each construct to plain text`() {
        assertEquals(
            """
            System Design: Short URL Service

            1. Problem Statement

            Turn long URLs like https://example.com/a into short ones, e.g. sho.rt (https://sho.rt/aB91xZ).

            Functional Requirements

            1. Create Short URL
            2. Custom Alias

            • Read-heavy

            • Availability: 99.99%
            • Redirect latency: < 100 ms

            • Cache: Tech: Redis · Why: Hot keys

            GET /{code} -> 301

            #systemdesign #java
            """.trimIndent(),
            MarkdownToLinkedIn.convert(sample),
        )
    }

    @Test
    fun `output has no Markdown markers`() {
        val text = MarkdownToLinkedIn.convert(sample)
        listOf("**", "##", "`", "|", "](").forEach { assertFalse("found $it", text.contains(it)) }
    }

    @Test
    fun `plain text passes through unchanged`() {
        val post = "🚀 Big news!\n\n📌 In short: ship small PRs.\n\n#engineering"
        assertEquals(post, MarkdownToLinkedIn.convert(post))
    }
}

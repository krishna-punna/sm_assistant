package com.smassistant.app.infographic

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where an AI illustration goes on the drawn infographic, in SVG units (see [RenderedInfographic.width]). */
data class IllustrationSlot(
    val prompt: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
)

/**
 * A drawn infographic: SVG of the whole layout (diagrams, text and icons), [width] x [height]
 * units, plus the [slots] that AI illustrations are painted into. Each slot already holds a
 * placeholder icon, so the SVG is complete on its own.
 */
data class RenderedInfographic(val svg: String, val width: Int, val height: Int, val slots: List<IllustrationSlot>)

/**
 * The infographic renderer (docs/design/image-video-pipeline.md): lays an [InfographicSpec] out
 * as a header band, a grid of numbered cards and a takeaway footer, and writes it as SVG that
 * AndroidSVG can draw. Text is broken into lines with [measurer], since SVG doesn't wrap.
 */
class InfographicRenderer(private val measurer: TextMeasurer = ApproxTextMeasurer) {

    /**
     * Draws [spec] at exactly its format's ratio (2:3, 1:1 or 3:2; docs/design/image-templates.md).
     * Short content is padded out to the ratio at the bottom. Content too long for it is laid out
     * on a wider page with more columns, up to [MAX_WIDEN] times the format's width, so the text is
     * drawn smaller instead; content too long even for that is centred on a page widened to the ratio.
     */
    /** Whether [spec] fits its format's ratio at the format's own width, with text at full size (no widening). */
    fun fits(spec: InfographicSpec): Boolean = renderAt(spec, spec.format.width.toFloat(), padSides = false).second

    fun render(spec: InfographicSpec): RenderedInfographic {
        val base = spec.format.width.toFloat()
        var width = base
        while (true) {
            val last = width >= base * MAX_WIDEN
            val (rendered, fits) = renderAt(spec, width, padSides = last)
            if (fits || last) return rendered
            width = min(width * 1.1f, base * MAX_WIDEN)
        }
    }

    private fun renderAt(spec: InfographicSpec, width: Float, padSides: Boolean): Pair<RenderedInfographic, Boolean> {
        val palette = spec.palette
        val contentWidth = width - 2 * MARGIN

        val header = header(spec, width)
        val columns = columnsFor(spec, width)
        val columnWidth = (contentWidth - (columns - 1) * GAP) / columns
        // Reading order: wide cards span the page. Between them, narrow cards go in the shortest
        // column, and the last few that can't fill the columns share a row instead of leaving a gap.
        val wide = spec.sections.map { columns > 1 && isWide(it.body, columnWidth) }
        val bottoms = FloatArray(columns)
        val placements = mutableListOf<Placement>()
        fun row(indices: List<Int>) {
            val cardWidth = (contentWidth - (indices.size - 1) * GAP) / indices.size
            val rowHeight = indices.maxOf { card(spec.sections[it], it, cardWidth, palette).height }
            val top = bottoms.max()
            indices.forEachIndexed { i, index ->
                placements += Placement(index, card(spec.sections[index], index, cardWidth, palette, rowHeight), MARGIN + i * (cardWidth + GAP), top)
            }
            bottoms.fill(top + rowHeight + GAP)
        }
        var next = 0
        while (next < spec.sections.size) {
            if (wide[next]) {
                row(listOf(next++))
                continue
            }
            var end = next
            while (end < spec.sections.size && !wide[end]) end++
            val run = (next until end).toList()
            val leftover = run.size % columns
            run.dropLast(leftover).forEach { index ->
                val column = bottoms.indices.minBy { bottoms[it] }
                val block = card(spec.sections[index], index, columnWidth, palette)
                placements += Placement(index, block, MARGIN + column * (columnWidth + GAP), bottoms[column])
                bottoms[column] += block.height + GAP
            }
            if (leftover > 0) row(run.takeLast(leftover))
            next = end
        }
        val bodyHeight = max(0f, bottoms.maxOrNull()?.minus(GAP) ?: 0f)
        val brand = spec.brand?.trim()?.takeIf { it.isNotEmpty() }
        val footer = if (spec.takeaway != null || brand != null) footer(spec.takeaway, brand, width, palette) else null

        val contentHeight = header.height + GAP + bodyHeight + GAP + (footer?.height ?: 0f)
        // The format's ratio: the sample images' sizes (2:3, 1:1, 3:2), or a video's (9:16, 16:9).
        val ratioHeight = when (spec.format) {
            InfographicFormat.PORTRAIT -> width * 1.5f
            InfographicFormat.SQUARE -> width
            InfographicFormat.LANDSCAPE -> width * 2 / 3
            InfographicFormat.VERTICAL -> width * 16 / 9
            InfographicFormat.WIDE -> width * 9 / 16
        }
        val fits = contentHeight <= ratioHeight + 0.5f
        val height = max(contentHeight, ratioHeight)
        // The last resort for content that doesn't fit: a page widened to the ratio, the layout centred on it.
        val pageWidth = if (fits || !padSides) width else height * width / ratioHeight
        val dx = (pageWidth - width) / 2

        val svg = Svg()
        svg.rect(0f, 0f, pageWidth, height, fill = palette.background)
        svg.rect(0f, 0f, pageWidth, header.height, fill = palette.header)
        svg.rect(0f, header.height - 6, pageWidth, 6f, fill = palette.highlight)
        header.draw(svg, dx, 0f)
        val bodyTop = header.height + GAP
        // A reveal step draws only its first sections, where the whole layout puts them, and the footer once all are shown.
        val shown = spec.revealed ?: spec.sections.size
        placements.filter { it.index < shown }.forEach { it.block.draw(svg, dx + it.x, bodyTop + it.top) }
        if (footer != null && shown >= spec.sections.size) {
            val top = height - footer.height
            svg.rect(0f, top, pageWidth, footer.height, fill = palette.header)
            footer.draw(svg, dx, top)
        }
        val rendered = RenderedInfographic(
            svg = svg.document(pageWidth.roundToInt(), height.roundToInt()),
            width = pageWidth.roundToInt(),
            height = height.roundToInt(),
            slots = svg.slots,
        )
        return rendered to fits
    }

    /** Something drawn at an (x, y) that is decided after its [height] is known. */
    private class Block(val height: Float, val draw: (Svg, Float, Float) -> Unit)

    /** Section [index]'s card and where it goes in the body. */
    private class Placement(val index: Int, val block: Block, val x: Float, val top: Float)

    /** 2 columns (3 for landscape and wide) at the format's width, and one more for each such column a wider page has room for. */
    private fun columnsFor(spec: InfographicSpec, width: Float): Int {
        val base = if (spec.format.isWide) 3 else 2
        val room = max(base, floor(width / (spec.format.width / base.toFloat())).toInt())
        return min(room, max(1, spec.sections.count { !isAlwaysWide(it.body) }))
    }

    private fun isAlwaysWide(body: SectionBody) =
        body is SectionBody.Table && body.columns.size > 2 || body is SectionBody.Compare || body is SectionBody.Diagram ||
            body is SectionBody.Flow && body.nodes.size > 3

    private fun isWide(body: SectionBody, columnWidth: Float): Boolean = isAlwaysWide(body) ||
        body is SectionBody.Code && body.code.lines().maxOf { it.length } * CODE_SIZE * 0.61f > columnWidth - 2 * CARD_PAD - 32

    // ---- Header and footer ----

    private fun header(spec: InfographicSpec, width: Float): Block {
        val palette = spec.palette
        val art = when {
            spec.hero != null -> if (spec.format.isWide) 260f else 230f
            spec.icon != null -> 132f
            else -> 0f
        }
        val textWidth = width - 2 * MARGIN - if (art > 0) art + GAP else 0f
        var titleSize = 64f
        var title = wrapText(spec.title, textWidth, titleSize, measurer, bold = true)
        if (title.size > 2) {
            titleSize = 52f
            title = wrapText(spec.title, textWidth, titleSize, measurer, bold = true, maxLines = 3)
        }
        val titleLine = titleSize * 1.16f
        val subtitle = spec.subtitle?.let { wrapText(it, textWidth, 28f, measurer, maxLines = 3) }.orEmpty()
        val textHeight = title.size * titleLine + if (subtitle.isEmpty()) 0f else 14 + subtitle.size * 38f
        val height = HEADER_PAD * 2 + max(textHeight, art)
        return Block(height) { svg, x, y ->
            val textTop = y + HEADER_PAD + max(0f, (art - textHeight) / 2)
            val highlight = spec.highlight?.lowercase()?.split(' ')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            title.forEachIndexed { i, line ->
                svg.highlightedLine(line, highlight, x + MARGIN, baseline(textTop + i * titleLine, titleLine, titleSize),
                    titleSize, palette.headerText, palette.highlight)
            }
            svg.lines(subtitle, x + MARGIN, textTop + title.size * titleLine + 14, 28f, 38f, palette.headerText, opacity = 0.82f)
            if (art > 0) {
                val artX = x + width - MARGIN - art
                val artY = y + (height - art) / 2
                if (spec.hero != null) {
                    svg.illustration(spec.hero, artX, artY, art, art, 28f, spec.icon ?: "idea",
                        tint = mix(palette.highlight, palette.header, 0.75f), iconColor = palette.highlight)
                } else {
                    svg.circle(artX + art / 2, artY + art / 2, art / 2, fill = mix(palette.highlight, palette.header, 0.8f))
                    svg.icon(spec.icon!!, artX + art * 0.22f, artY + art * 0.22f, art * 0.56f, palette.highlight)
                }
            }
        }
    }

    /** The takeaway with its light bulb, and the [brand] name small on the right; either may be missing. */
    private fun footer(takeaway: String?, brand: String?, width: Float, palette: Palette): Block {
        val iconSize = 44f
        val brandLines = brand?.let { wrapText(it, width / 4, 22f, measurer, bold = true, maxLines = 2) }.orEmpty()
        val brandWidth = if (brandLines.isEmpty()) 0f else brandLines.maxOf { measurer.width(it, 22f, true, FontFamily.SANS) } + GAP
        val lines = takeaway?.let {
            wrapText(it, width - 2 * MARGIN - iconSize - 20 - brandWidth, 26f, measurer, bold = true, maxLines = 4)
        }.orEmpty()
        val height = 2 * 30f + maxOf(if (lines.isEmpty()) 0f else iconSize, lines.size * 36f, brandLines.size * 30f)
        return Block(height) { svg, x, y ->
            if (lines.isNotEmpty()) {
                svg.icon("idea", x + MARGIN, y + (height - iconSize) / 2, iconSize, palette.highlight)
                svg.lines(lines, x + MARGIN + iconSize + 20, y + (height - lines.size * 36f) / 2, 26f, 36f, palette.headerText, bold = true)
            }
            brandLines.forEachIndexed { i, line ->
                val top = y + (height - brandLines.size * 30f) / 2 + i * 30f
                svg.text(line, x + width - MARGIN, baseline(top, 30f, 22f), 22f, palette.headerText, bold = true, anchor = "end", opacity = 0.8f)
            }
        }
    }

    // ---- Cards ----

    private fun card(section: Section, index: Int, width: Float, palette: Palette, minHeight: Float = 0f): Block {
        val color = palette.accent(index)
        val headingX = CARD_PAD + 44 + 12 + (if (section.icon != null) 30 + 10 else 0)
        val heading = wrapText(section.heading.ifBlank { " " }, width - headingX - CARD_PAD, 28f, measurer, bold = true, maxLines = 2)
        val tab = max(68f, heading.size * 36f + 26)
        val inner = width - 2 * CARD_PAD
        val body = body(section, inner, color, palette)
        val height = max(minHeight, tab + CARD_PAD + body.height + CARD_PAD)
        return Block(height) { svg, x, y ->
            svg.rect(x, y, width, height, fill = palette.card, stroke = palette.cardBorder, strokeWidth = 2f, rx = 18f)
            svg.rect(x, y, width, tab, fill = color, rx = 18f)
            svg.rect(x, y + tab - 18, width, 18f, fill = color)
            svg.circle(x + CARD_PAD + 22, y + tab / 2, 22f, fill = "#FFFFFF")
            svg.text("${index + 1}", x + CARD_PAD + 22, baseline(y + tab / 2 - 15, 30f, 24f), 24f, color, bold = true, anchor = "middle")
            section.icon?.let { svg.icon(it, x + CARD_PAD + 56, y + tab / 2 - 15, 30f, "#FFFFFF") }
            svg.lines(heading, x + headingX, y + (tab - heading.size * 36f) / 2, 28f, 36f, "#FFFFFF", bold = true)
            body.draw(svg, x + CARD_PAD, y + tab + CARD_PAD)
        }
    }

    private fun body(section: Section, width: Float, color: String, palette: Palette): Block = when (val body = section.body) {
        is SectionBody.Bullets -> list(body.items, width, palette) { svg, x, y, _ -> svg.circle(x + 8, y + LINE / 2, 6f, fill = color) }
        is SectionBody.Steps -> list(body.items, width, palette, indent = 46f) { svg, x, y, i ->
            svg.circle(x + 17, y + LINE / 2, 17f, fill = color)
            svg.text("${i + 1}", x + 17, baseline(y, LINE, 20f), 20f, "#FFFFFF", bold = true, anchor = "middle")
        }
        is SectionBody.ProsCons -> {
            val pros = list(body.pros, width, palette, indent = 36f) { svg, x, y, _ -> svg.icon("check", x, y + 5, 24f, GOOD, 3f) }
            val cons = list(body.cons, width, palette, indent = 36f) { svg, x, y, _ -> svg.icon("cross", x, y + 5, 24f, BAD, 3f) }
            stack(listOf(pros, cons).filter { it.height > 0 }, ITEM_GAP)
        }
        is SectionBody.Code -> code(body, width)
        is SectionBody.Table -> table(body, width, color, palette)
        is SectionBody.Flow -> flow(body, width, color, palette)
        is SectionBody.Compare -> compare(body, width, palette)
        is SectionBody.Stats -> stats(body, width, color, palette)
        is SectionBody.Illustration -> illustration(body, section.icon, width, color, palette)
        is SectionBody.Chart -> chart(body, width, color, palette)
        is SectionBody.Diagram -> diagram(body, width, palette)
    }

    private fun list(
        items: List<String>,
        width: Float,
        palette: Palette,
        indent: Float = 28f,
        size: Float = BODY_SIZE,
        marker: (Svg, Float, Float, Int) -> Unit,
    ): Block {
        val wrapped = items.map { wrapText(it, width - indent, size, measurer) }
        val height = wrapped.sumOf { (it.size * LINE).toDouble() }.toFloat() + ITEM_GAP * max(0, wrapped.size - 1)
        return Block(height) { svg, x, y ->
            var top = y
            wrapped.forEachIndexed { i, lines ->
                marker(svg, x, top, i)
                svg.lines(lines, x + indent, top, size, LINE, palette.text)
                top += lines.size * LINE + ITEM_GAP
            }
        }
    }

    private fun stack(blocks: List<Block>, gap: Float): Block {
        val height = blocks.sumOf { it.height.toDouble() }.toFloat() + gap * max(0, blocks.size - 1)
        return Block(height) { svg, x, y ->
            var top = y
            blocks.forEach { block ->
                block.draw(svg, x, top)
                top += block.height + gap
            }
        }
    }

    private fun code(body: SectionBody.Code, width: Float) : Block {
        val charWidth = measurer.width("0000000000", CODE_SIZE, false, FontFamily.MONO) / 10
        val perLine = max(8, floor((width - 32) / charWidth).toInt())
        val lines = body.code.lines().flatMap { line -> if (line.isEmpty()) listOf("") else line.chunked(perLine) }.take(Limits.CODE_LINES + 4)
        val lineHeight = CODE_SIZE * 1.4f
        val height = lines.size * lineHeight + 32
        return Block(height) { svg, x, y ->
            svg.rect(x, y, width, height, fill = CODE_BACKGROUND, rx = 12f)
            lines.forEachIndexed { i, line ->
                val comment = line.trimStart().let { it.startsWith("//") || it.startsWith("#") || it.startsWith("--") }
                // Non-breaking spaces keep the indentation, since SVG collapses normal ones.
                svg.text(line.replace(' ', ' '), x + 16, baseline(y + 16 + i * lineHeight, lineHeight, CODE_SIZE), CODE_SIZE,
                    if (comment) CODE_COMMENT else CODE_TEXT, family = FontFamily.MONO)
            }
        }
    }

    private fun table(body: SectionBody.Table, width: Float, color: String, palette: Palette): Block {
        val columns = body.columns.size
        val cellWidth = width / columns
        val size = 22f
        val lineHeight = 29f
        fun wrapRow(cells: List<String>, bold: Boolean) =
            List(columns) { c -> wrapText(cells.getOrElse(c) { "" }, cellWidth - 20, size, measurer, bold = bold, maxLines = 4) }
        val rows = listOf(wrapRow(body.columns, true)) + body.rows.map { wrapRow(it, false) }
        val heights = rows.map { row -> row.maxOf { it.size } * lineHeight + 18 }
        return Block(heights.sum()) { svg, x, y ->
            var top = y
            rows.forEachIndexed { r, row ->
                val fill = when {
                    r == 0 -> color
                    r % 2 == 1 -> palette.card
                    else -> mix(color, palette.card, 0.9f)
                }
                svg.rect(x, top, width, heights[r], fill = fill)
                row.forEachIndexed { c, lines ->
                    svg.lines(lines, x + c * cellWidth + 10, top + 9, size, lineHeight,
                        if (r == 0) "#FFFFFF" else palette.text, bold = r == 0 || c == 0)
                }
                top += heights[r]
            }
            svg.rect(x, y, width, heights.sum(), fill = "none", stroke = palette.cardBorder, strokeWidth = 2f)
        }
    }

    private fun flow(body: SectionBody.Flow, width: Float, color: String, palette: Palette): Block {
        val arrow = 38f
        val perRow = max(1, min(body.nodes.size, floor((width + arrow) / (MIN_NODE + arrow)).toInt()))
        val nodeWidth = (width - (perRow - 1) * arrow) / perRow
        val labels = body.nodes.map { wrapText(it, nodeWidth - 20, 22f, measurer, bold = true, maxLines = 3) }
        val nodeHeight = labels.maxOf { it.size } * 28f + 28
        val rows = labels.indices.chunked(perRow)
        val rowGap = 44f
        val height = rows.size * nodeHeight + (rows.size - 1) * rowGap
        return Block(height) { svg, x, y ->
            fun nodeX(i: Int) = x + (i % perRow) * (nodeWidth + arrow)
            fun nodeY(i: Int) = y + (i / perRow) * (nodeHeight + rowGap)
            labels.forEachIndexed { i, lines ->
                val nx = nodeX(i)
                val ny = nodeY(i)
                svg.rect(nx, ny, nodeWidth, nodeHeight, fill = mix(color, palette.card, 0.86f), stroke = color, strokeWidth = 2.5f, rx = 12f)
                svg.lines(lines, nx + nodeWidth / 2, ny + (nodeHeight - lines.size * 28f) / 2, 22f, 28f, palette.text, bold = true, anchor = "middle")
                if (i == labels.lastIndex) return@forEachIndexed
                if ((i + 1) % perRow != 0) {
                    svg.arrow(listOf(nx + nodeWidth + 4 to ny + nodeHeight / 2, nx + nodeWidth + arrow - 4 to ny + nodeHeight / 2), color)
                } else {
                    val startX = nx + nodeWidth / 2
                    val endX = nodeX(i + 1) + nodeWidth / 2
                    val midY = ny + nodeHeight + rowGap / 2
                    svg.arrow(listOf(startX to ny + nodeHeight + 2, startX to midY, endX to midY, endX to nodeY(i + 1) - 4), color)
                }
            }
        }
    }

    private fun compare(body: SectionBody.Compare, width: Float, palette: Palette): Block {
        val gap = 16f
        val panelWidth = (width - gap) / 2
        val sides = listOf(body.left to BAD, body.right to GOOD)
        val lists = sides.map { (side, color) ->
            list(side.items, panelWidth - 28, palette, size = 22f) { svg, x, y, _ -> svg.circle(x + 8, y + LINE / 2, 6f, fill = color) }
        }
        val height = 44 + 14 + lists.maxOf { it.height } + 14
        return Block(height) { svg, x, y ->
            sides.forEachIndexed { i, (side, color) ->
                val px = x + i * (panelWidth + gap)
                svg.rect(px, y, panelWidth, height, fill = mix(color, palette.card, 0.92f), stroke = color, strokeWidth = 2f, rx = 12f)
                svg.rect(px, y, panelWidth, 44f, fill = color, rx = 12f)
                svg.rect(px, y + 30, panelWidth, 14f, fill = color)
                svg.text(side.label, px + panelWidth / 2, baseline(y, 44f, 22f), 22f, "#FFFFFF", bold = true, anchor = "middle")
                lists[i].draw(svg, px + 14, y + 44 + 14)
            }
            svg.circle(x + width / 2, y + height / 2, 18f, fill = palette.card, stroke = palette.cardBorder)
            svg.icon("arrow", x + width / 2 - 11, y + height / 2 - 11, 22f, palette.mutedText)
        }
    }

    private fun stats(body: SectionBody.Stats, width: Float, color: String, palette: Palette): Block {
        val perRow = if (width >= 600) min(4, body.items.size) else min(2, body.items.size)
        val gap = 14f
        val tileWidth = (width - (perRow - 1) * gap) / perRow
        val labels = body.items.map { wrapText(it.label, tileWidth - 20, 20f, measurer, maxLines = 2) }
        val tileHeight = 20 + 52 + 6 + labels.maxOf { it.size } * 26f + 16
        val rows = (body.items.size + perRow - 1) / perRow
        return Block(rows * tileHeight + (rows - 1) * gap) { svg, x, y ->
            body.items.forEachIndexed { i, stat ->
                val tx = x + (i % perRow) * (tileWidth + gap)
                val ty = y + (i / perRow) * (tileHeight + gap)
                svg.rect(tx, ty, tileWidth, tileHeight, fill = mix(color, palette.card, 0.9f), rx = 12f)
                svg.text(stat.value, tx + tileWidth / 2, ty + 20 + 44, 46f, color, bold = true, anchor = "middle")
                svg.lines(labels[i], tx + tileWidth / 2, ty + 20 + 52 + 6, 20f, 26f, palette.mutedText, anchor = "middle")
            }
        }
    }

    /** Horizontal bars to scale: the label on the left, the bar, then the value exactly as written; the unit underneath. */
    private fun chart(body: SectionBody.Chart, width: Float, color: String, palette: Palette): Block {
        val labelWidth = width * 0.34f
        val labels = body.bars.map { wrapText(it.label.ifBlank { " " }, labelWidth, 20f, measurer, maxLines = 2) }
        val valueWidth = body.bars.maxOf { measurer.width(it.value, 20f, true, FontFamily.SANS) } + 10
        val track = max(40f, width - labelWidth - 12 - valueWidth)
        val most = body.bars.maxOf { it.amount }.takeIf { it > 0 } ?: 1.0
        val rowHeights = labels.map { max(34f, it.size * 26f) }
        val unit = body.unit?.let { wrapText(it, width, 18f, measurer, maxLines = 1) }.orEmpty()
        val height = rowHeights.sum() + (rowHeights.size - 1) * ITEM_GAP + if (unit.isEmpty()) 0f else 8 + 24f
        return Block(height) { svg, x, y ->
            var top = y
            body.bars.forEachIndexed { i, bar ->
                val rowHeight = rowHeights[i]
                svg.lines(labels[i], x, top + (rowHeight - labels[i].size * 26f) / 2, 20f, 26f, palette.text)
                val barX = x + labelWidth + 12
                val barY = top + (rowHeight - 24) / 2
                svg.rect(barX, barY, track, 24f, fill = mix(color, palette.card, 0.88f), rx = 6f)
                val length = max(6f, (bar.amount / most * track).toFloat())
                svg.rect(barX, barY, length, 24f, fill = color, rx = 6f)
                svg.text(bar.value, barX + length + 8, baseline(barY, 24f, 20f), 20f, palette.text, bold = true)
                top += rowHeight + ITEM_GAP
            }
            svg.lines(unit, x, top - ITEM_GAP + 8, 18f, 24f, palette.mutedText)
        }
    }

    /**
     * Nodes in layers by the longest chain of arrows that leads to them, left to right when the
     * layers fit side by side and top to bottom otherwise. An arrow to the next layer runs straight
     * between the boxes; one that skips layers runs along a lane above (or beside) the boxes, and one
     * that points back along a lane below (or on the other side), so no arrow passes under a box.
     * Arrow labels sit beside their line. Nodes take their group's colour, named in a legend underneath.
     */
    private fun diagram(body: SectionBody.Diagram, width: Float, palette: Palette): Block {
        val layerOf = diagramLayers(body)
        val layers = body.nodes.groupBy { layerOf.getValue(it.id) }.toSortedMap().values.toList()
        val groups = body.nodes.mapNotNull { it.group }.distinct()
        fun colorOf(node: SectionBody.Diagram.Node) = palette.accent(node.group?.let { groups.indexOf(it) } ?: 0)
        val gap = if (body.edges.any { it.label != null }) 96f else 64f
        val sideways = (width - (layers.size - 1) * gap) / layers.size >= MIN_DIAGRAM_NODE
        val longEdges = body.edges.filter { layerOf.getValue(it.to) - layerOf.getValue(it.from) != 1 }
        val forwardLane = if (longEdges.any { layerOf.getValue(it.to) > layerOf.getValue(it.from) }) DIAGRAM_LANE else 0f
        val backLane = if (longEdges.any { layerOf.getValue(it.to) < layerOf.getValue(it.from) }) DIAGRAM_LANE else 0f
        // Box size: one layer per column (sideways), or the widest layer's nodes side by side in a row.
        val across = if (sideways) layers.size else layers.maxOf { it.size }
        val usable = if (sideways) width else width - forwardLane - backLane
        val boxWidth = (usable - (across - 1) * gap) / across
        val labels = body.nodes.associate { it.id to wrapText(it.label, boxWidth - 20, 20f, measurer, bold = true, maxLines = 3) }
        val boxHeight = labels.values.maxOf { it.size } * 26f + 26
        val alongGap = 40f
        val lineSize = if (sideways) layers.maxOf { it.size } else layers.size
        val graphHeight = if (sideways) lineSize * boxHeight + (lineSize - 1) * alongGap else layers.size * boxHeight + (layers.size - 1) * gap
        val top = if (sideways) forwardLane else 0f
        val legend = if (groups.isEmpty()) 0f else 14 + 28f
        // Each box's top-left corner, relative to the block.
        val boxes = mutableMapOf<String, Pair<Float, Float>>()
        layers.forEachIndexed { li, layer ->
            if (sideways) {
                val span = layer.size * boxHeight + (layer.size - 1) * alongGap
                layer.forEachIndexed { ni, node -> boxes[node.id] = li * (boxWidth + gap) to top + (graphHeight - span) / 2 + ni * (boxHeight + alongGap) }
            } else {
                val span = layer.size * boxWidth + (layer.size - 1) * gap
                layer.forEachIndexed { ni, node -> boxes[node.id] = backLane + (usable - span) / 2 + ni * (boxWidth + gap) to li * (boxHeight + gap) }
            }
        }
        val height = top + graphHeight + (if (sideways) backLane else 0f) + legend
        return Block(height) { svg, x, y ->
            val drawnLabels = mutableListOf<Triple<String, Float, Float>>()
            body.edges.forEach { edge ->
                val (fx, fy) = boxes.getValue(edge.from).let { (bx, by) -> x + bx to y + by }
                val (tx, ty) = boxes.getValue(edge.to).let { (bx, by) -> x + bx to y + by }
                val step = layerOf.getValue(edge.to) - layerOf.getValue(edge.from)
                val points = when {
                    step == 1 && sideways -> listOf(fx + boxWidth + 2 to fy + boxHeight / 2, tx - 6 to ty + boxHeight / 2)
                    step == 1 -> listOf(fx + boxWidth / 2 to fy + boxHeight + 2, tx + boxWidth / 2 to ty - 6)
                    sideways && step > 1 -> listOf(fx + boxWidth / 2 to fy - 2, fx + boxWidth / 2 to y + 18, tx + boxWidth / 2 to y + 18, tx + boxWidth / 2 to ty - 6)
                    sideways -> (y + top + graphHeight + backLane - 18).let { lane ->
                        listOf(fx + boxWidth / 2 to fy + boxHeight + 2, fx + boxWidth / 2 to lane, tx + boxWidth / 2 to lane, tx + boxWidth / 2 to ty + boxHeight + 6)
                    }
                    step > 1 -> (x + width - 18).let { lane ->
                        listOf(fx + boxWidth + 2 to fy + boxHeight / 2, lane to fy + boxHeight / 2, lane to ty + boxHeight / 2, tx + boxWidth + 6 to ty + boxHeight / 2)
                    }
                    else -> (x + 18).let { lane ->
                        listOf(fx - 2 to fy + boxHeight / 2, lane to fy + boxHeight / 2, lane to ty + boxHeight / 2, tx - 6 to ty + boxHeight / 2)
                    }
                }
                svg.arrow(points, palette.mutedText)
                edge.label?.let { label ->
                    // Beside the middle segment (a side lane's first one): above a horizontal one, to the right of a vertical one.
                    val segment = if (points.size == 4 && !sideways) 0 else (points.size - 1) / 2
                    val (a, b) = points[segment] to points[segment + 1]
                    val horizontal = kotlin.math.abs(b.first - a.first) >= kotlin.math.abs(b.second - a.second)
                    val mx = (a.first + b.first) / 2
                    val my = (a.second + b.second) / 2
                    drawnLabels += if (horizontal) Triple(label, mx, my - 16) else Triple(label, mx + 8 + measurer.width(label, 17f, false, FontFamily.SANS) / 2 + 6, my)
                }
            }
            drawnLabels.forEach { (label, cx, cy) ->
                val labelWidth = measurer.width(label, 17f, false, FontFamily.SANS) + 12
                svg.rect(cx - labelWidth / 2, cy - 12, labelWidth, 24f, fill = palette.card, stroke = palette.cardBorder, rx = 8f)
                svg.text(label, cx, baseline(cy - 12, 24f, 17f), 17f, palette.mutedText, anchor = "middle")
            }
            body.nodes.forEach { node ->
                val (bx, by) = boxes.getValue(node.id)
                val color = colorOf(node)
                val lines = labels.getValue(node.id)
                svg.rect(x + bx, y + by, boxWidth, boxHeight, fill = mix(color, palette.card, 0.86f), stroke = color, strokeWidth = 2.5f, rx = 12f)
                svg.lines(lines, x + bx + boxWidth / 2, y + by + (boxHeight - lines.size * 26f) / 2, 20f, 26f, palette.text, bold = true, anchor = "middle")
            }
            var lx = x
            val ly = y + height - 28f
            groups.forEachIndexed { i, group ->
                if (lx + 24 + measurer.width(group, 18f, false, FontFamily.SANS) > x + width) return@forEachIndexed
                svg.rect(lx, ly + 6, 16f, 16f, fill = palette.accent(i), rx = 4f)
                svg.text(group, lx + 24, baseline(ly, 28f, 18f), 18f, palette.mutedText)
                lx += 24 + measurer.width(group, 18f, false, FontFamily.SANS) + 28
            }
        }
    }

    private fun illustration(body: SectionBody.Illustration, icon: String?, width: Float, color: String, palette: Palette): Block {
        val artHeight = min(width * if (width < 500) 0.75f else 0.56f, MAX_ART_HEIGHT)
        val caption = body.caption?.let { wrapText(it, width, 22f, measurer, maxLines = 3) }.orEmpty()
        val height = artHeight + if (caption.isEmpty()) 0f else 12 + caption.size * 30f
        return Block(height) { svg, x, y ->
            svg.illustration(body.prompt, x, y, width, artHeight, 14f, icon ?: "idea",
                tint = mix(color, palette.card, 0.88f), iconColor = color)
            svg.lines(caption, x + width / 2, y + artHeight + 12, 22f, 30f, palette.mutedText, anchor = "middle")
        }
    }

    private companion object {
        const val MARGIN = 48f

        /** Narrowest diagram box when the layers are laid out side by side. */
        const val MIN_DIAGRAM_NODE = 150f

        /** Room for the arrows that skip layers or point back, beside the diagram's boxes. */
        const val DIAGRAM_LANE = 36f

        /** How much wider than its format a page may be laid out to keep long content at the format's ratio. */
        const val MAX_WIDEN = 2f
        const val GAP = 28f
        const val HEADER_PAD = 44f
        const val CARD_PAD = 24f
        const val BODY_SIZE = 24f
        const val LINE = 33f
        const val ITEM_GAP = 10f
        const val CODE_SIZE = 21f
        const val MIN_NODE = 150f
        const val MAX_ART_HEIGHT = 560f
        const val GOOD = "#16A34A"
        const val BAD = "#DC2626"
        const val CODE_BACKGROUND = "#1E293B"
        const val CODE_TEXT = "#E2E8F0"
        const val CODE_COMMENT = "#94A3B8"
    }
}

/** Baseline that centres a line of [size] text in a line box starting at [top] and [lineHeight] tall. */
private fun baseline(top: Float, lineHeight: Float, size: Float) = top + lineHeight / 2 + size * 0.35f

/**
 * Each node's layer: the longest chain of arrows that leads to it (0 for a node nothing points to).
 * Arrows that close a cycle are ignored, so every node gets a layer.
 */
internal fun diagramLayers(body: SectionBody.Diagram): Map<String, Int> {
    // Drop the arrows that point back into the current path of a depth-first walk, in node order.
    val outgoing = body.edges.groupBy({ it.from }, { it.to })
    val kept = mutableListOf<Pair<String, String>>()
    val done = mutableSetOf<String>()
    val path = mutableSetOf<String>()
    fun walk(id: String) {
        path += id
        outgoing[id].orEmpty().forEach { to ->
            if (to in path) return@forEach
            kept += id to to
            if (to !in done) walk(to)
        }
        path -= id
        done += id
    }
    body.nodes.forEach { if (it.id !in done) walk(it.id) }
    val incoming = kept.groupBy({ it.second }, { it.first })
    val layers = mutableMapOf<String, Int>()
    fun layer(id: String): Int = layers.getOrPut(id) { incoming[id].orEmpty().maxOfOrNull { layer(it) + 1 } ?: 0 }
    body.nodes.forEach { layer(it.id) }
    return layers
}

/** Mixes [color] into [base]: [baseShare] 0 is all [color], 1 all [base]. Both are #RRGGBB. */
internal fun mix(color: String, base: String, baseShare: Float): String {
    fun channels(hex: String) = listOf(1, 3, 5).map { hex.substring(it, it + 2).toInt(16) }
    val mixed = channels(color).zip(channels(base)) { c, b -> (c + (b - c) * baseShare).roundToInt().coerceIn(0, 255) }
    return mixed.joinToString("", prefix = "#") { it.toString(16).padStart(2, '0').uppercase() }
}

/** Builds the SVG markup, using only what AndroidSVG draws (no CSS, filters or foreignObject). */
private class Svg {
    private val out = StringBuilder()
    val slots = mutableListOf<IllustrationSlot>()

    fun document(width: Int, height: Int) =
        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">""" +
            out + "</svg>"

    fun rect(x: Float, y: Float, w: Float, h: Float, fill: String, stroke: String? = null, strokeWidth: Float = 0f, rx: Float = 0f) {
        out.append("""<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}"""")
        if (rx > 0) out.append(""" rx="${n(rx)}"""")
        out.append(""" fill="$fill"""")
        if (stroke != null) out.append(""" stroke="$stroke" stroke-width="${n(max(strokeWidth, 1f))}"""")
        out.append("/>")
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: String, stroke: String? = null) {
        out.append("""<circle cx="${n(cx)}" cy="${n(cy)}" r="${n(r)}" fill="$fill"""")
        if (stroke != null) out.append(""" stroke="$stroke" stroke-width="2"""")
        out.append("/>")
    }

    fun text(
        content: String,
        x: Float,
        y: Float,
        size: Float,
        fill: String,
        bold: Boolean = false,
        anchor: String = "start",
        family: FontFamily = FontFamily.SANS,
        opacity: Float = 1f,
    ) {
        if (content.isBlank()) return
        out.append("""<text x="${n(x)}" y="${n(y)}" font-family="${family.svg}" font-size="${n(size)}" fill="$fill"""")
        if (bold) out.append(""" font-weight="bold"""")
        if (anchor != "start") out.append(""" text-anchor="$anchor"""")
        if (opacity < 1f) out.append(""" opacity="${n(opacity)}"""")
        out.append(">").append(escape(content)).append("</text>")
    }

    /** One line per entry, the first line box starting at [top]. */
    fun lines(
        lines: List<String>,
        x: Float,
        top: Float,
        size: Float,
        lineHeight: Float,
        fill: String,
        bold: Boolean = false,
        anchor: String = "start",
        opacity: Float = 1f,
    ) = lines.forEachIndexed { i, line ->
        text(line, x, baseline(top + i * lineHeight, lineHeight, size), size, fill, bold, anchor, opacity = opacity)
    }

    /** A bold title line whose words in [highlight] (lower case) are drawn in [accent]. */
    fun highlightedLine(line: String, highlight: Set<String>, x: Float, y: Float, size: Float, fill: String, accent: String) {
        out.append("""<text x="${n(x)}" y="${n(y)}" font-family="sans-serif" font-size="${n(size)}" font-weight="bold" fill="$fill">""")
        line.split(' ').forEachIndexed { i, word ->
            val text = escape(if (i == 0) word else " $word")
            val key = word.lowercase().trim { !it.isLetterOrDigit() }
            if (key in highlight) out.append("""<tspan fill="$accent">$text</tspan>""") else out.append("<tspan>$text</tspan>")
        }
        out.append("</text>")
    }

    fun icon(name: String, x: Float, y: Float, size: Float, color: String, strokeWidth: Float = 2f) {
        val shapes = ICONS[name] ?: return
        out.append(
            """<g transform="translate(${n(x)} ${n(y)}) scale(${n3(size / 24)})" fill="none" stroke="$color" """ +
                """stroke-width="${n(strokeWidth)}" stroke-linecap="round" stroke-linejoin="round">$shapes</g>""",
        )
    }

    /** A polyline through [points] with an arrow head at the end. */
    fun arrow(points: List<Pair<Float, Float>>, color: String) {
        val d = points.mapIndexed { i, (px, py) -> (if (i == 0) "M" else "L") + "${n(px)} ${n(py)}" }.joinToString(" ")
        out.append("""<path d="$d" fill="none" stroke="$color" stroke-width="3" stroke-linejoin="round"/>""")
        val (tx, ty) = points.last()
        val (fx, fy) = points[points.size - 2]
        val len = kotlin.math.hypot(tx - fx, ty - fy).takeIf { it > 0 } ?: return
        val ux = (tx - fx) / len
        val uy = (ty - fy) / len
        val head = 12f
        val p1 = (tx - ux * head - uy * head * 0.6f) to (ty - uy * head + ux * head * 0.6f)
        val p2 = (tx - ux * head + uy * head * 0.6f) to (ty - uy * head - ux * head * 0.6f)
        out.append("""<polygon points="${n(tx)},${n(ty)} ${n(p1.first)},${n(p1.second)} ${n(p2.first)},${n(p2.second)}" fill="$color"/>""")
    }

    /** A placeholder (tinted panel with [icon]) for an AI illustration, recorded as a slot. */
    fun illustration(prompt: String, x: Float, y: Float, w: Float, h: Float, radius: Float, icon: String, tint: String, iconColor: String) {
        rect(x, y, w, h, fill = tint, rx = radius)
        val size = min(w, h) * 0.42f
        icon(icon, x + (w - size) / 2, y + (h - size) / 2, size, iconColor, 1.6f)
        slots += IllustrationSlot(prompt, x, y, w, h, radius)
    }

    private fun n(v: Float): String {
        val tenths = (v * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10.0).toString()
    }

    private fun n3(v: Float) = ((v * 1000).roundToInt() / 1000.0).toString()

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

private val InfographicFormat.isWide get() = this == InfographicFormat.LANDSCAPE || this == InfographicFormat.WIDE

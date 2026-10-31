package com.smassistant.app.image

import java.io.File

/** Where a file sits in a poster series (AC-7): the series' [id] and its 1-based [part]. */
data class SeriesPart(val id: Long, val part: Int)

/** Series file names; `carousel-` is how results saved before AC-5 named their pages. */
private val SERIES_PART = Regex("""^(?:series|carousel)-(\d+)-p(\d+)-""")

/**
 * [file] renamed as part [part] of series [id] (`series-<id>-p03-…`), so a result, a saved result
 * and a scheduled run keep the posters together and in order; the file itself when it can't be renamed.
 */
fun labelSeriesPart(file: File, id: Long, part: Int): File {
    val labelled = File(file.parentFile, "series-$id-p${part.toString().padStart(2, '0')}-${file.name}")
    return if (file.renameTo(labelled)) labelled else file
}

/** The series and part in a file name written by [labelSeriesPart]; null for other files. */
fun seriesPart(file: File): SeriesPart? {
    val match = SERIES_PART.find(file.name) ?: return null
    return SeriesPart(match.groupValues[1].toLong(), match.groupValues[2].toInt())
}

/** Whether [files] are the posters of one series (AC-7), shown and posted together in order, rather than separate images. */
fun isSeries(files: List<File>): Boolean {
    val ids = files.map { seriesPart(it)?.id }
    return files.size > 1 && ids.first() != null && ids.all { it == ids.first() }
}

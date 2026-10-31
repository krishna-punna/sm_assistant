package com.smassistant.app.image

import java.io.File

/** Where a file sits in a carousel: the carousel's [id] and its 1-based [page]. */
data class CarouselPage(val id: Long, val page: Int)

private val CAROUSEL_PAGE = Regex("""^carousel-(\d+)-p(\d+)-""")

/**
 * [file] renamed as page [page] of carousel [id] (`carousel-<id>-p03-…`), so a result, a saved
 * result and a scheduled run keep the pages together and in order; the file itself when it can't be renamed.
 */
fun labelCarouselPage(file: File, id: Long, page: Int): File {
    val labelled = File(file.parentFile, "carousel-$id-p${page.toString().padStart(2, '0')}-${file.name}")
    return if (file.renameTo(labelled)) labelled else file
}

/** The carousel and page in a file name written by [labelCarouselPage]; null for other files. */
fun carouselPage(file: File): CarouselPage? {
    val match = CAROUSEL_PAGE.find(file.name) ?: return null
    return CarouselPage(match.groupValues[1].toLong(), match.groupValues[2].toInt())
}

/** Whether [files] are the pages of one carousel (docs/design/single-image-carousel.md), rather than separate images. */
fun isCarousel(files: List<File>): Boolean {
    val ids = files.map { carouselPage(it)?.id }
    return files.size > 1 && ids.first() != null && ids.all { it == ids.first() }
}

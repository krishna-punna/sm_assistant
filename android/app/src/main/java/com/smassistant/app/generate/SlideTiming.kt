package com.smassistant.app.generate

/** The shortest time a video scene stays on screen (docs/design/video-templates.md, section 4). */
const val MIN_SCENE_SECONDS = 3

/**
 * How long each scene stays up, in milliseconds (docs/tasks VT-I3): the [requested] seconds from
 * the storyboard, scaled so they add up to [totalSeconds], with every scene up for at least
 * [minSeconds]. A scene without a request gets the average of the others; with no requests at
 * all, or too little time for the minimum, the scenes share the time equally.
 */
fun sceneMillis(requested: List<Int?>, totalSeconds: Int, minSeconds: Int = MIN_SCENE_SECONDS): List<Long> {
    val count = requested.size
    if (count == 0) return emptyList()
    val total = totalSeconds * 1000L
    val min = minSeconds * 1000L
    if (requested.all { it == null || it <= 0 } || total <= min * count) return equalShares(total, count)
    val given = requested.mapNotNull { it?.takeIf { s -> s > 0 } }
    val weights = requested.map { it?.takeIf { s -> s > 0 }?.toDouble() ?: given.average() }
    // Scenes whose share would fall under the minimum get the minimum; the rest share what is left by weight.
    val atMin = BooleanArray(count)
    while (true) {
        val free = total - min * atMin.count { it }
        val freeWeight = weights.indices.filter { !atMin[it] }.sumOf { weights[it] }
        val short = weights.indices.filter { !atMin[it] && free * weights[it] / freeWeight < min }
        if (short.isEmpty()) break
        short.forEach { atMin[it] = true }
    }
    val free = total - min * atMin.count { it }
    val freeWeight = weights.indices.filter { !atMin[it] }.sumOf { weights[it] }
    val shares = weights.indices.map { if (atMin[it]) min else maxOf(min, (free * weights[it] / freeWeight).toLong()) }.toMutableList()
    // Rounding leftovers go to the longest scene, so the video is exactly as long as picked.
    val longest = shares.indices.maxBy { shares[it] }
    shares[longest] += total - shares.sum()
    return shares
}

private fun equalShares(total: Long, count: Int): List<Long> =
    List(count) { i -> total / count + if (i == count - 1) total % count else 0 }

package com.smassistant.app.video

/** Video lengths offered next to the video agent and model (Requirement 9): 0:30 to 5:00 in half minutes. */
val VIDEO_LENGTHS: List<Int> = (1..10).map { it * 30 }

/** The length until the user picks one. */
const val DEFAULT_VIDEO_SECONDS = 30

/** About how long one slide should stay on screen; longer videos get more slides up to [MAX_SLIDES]. */
const val TARGET_SECONDS_PER_SLIDE = 8

/**
 * The most slides one video plans. The whole deck comes back in one text-agent reply, and ten
 * slides of JSON fit well inside a 4096-token reply; past that the slides stay up longer and
 * carry more content instead.
 */
const val MAX_SLIDES = 10

/** "30 sec", "1 min", "1 min 30 sec" … "5 min". */
fun videoLengthLabel(seconds: Int): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return when {
        minutes == 0 -> "$rest sec"
        rest == 0 -> "$minutes min"
        else -> "$minutes min $rest sec"
    }
}

/** How many slides a video of [seconds] plans: one per [TARGET_SECONDS_PER_SLIDE], from [SLIDES_PER_VIDEO] to [MAX_SLIDES]. */
fun slidesFor(seconds: Int): Int = (seconds / TARGET_SECONDS_PER_SLIDE).coerceIn(SLIDES_PER_VIDEO, MAX_SLIDES)

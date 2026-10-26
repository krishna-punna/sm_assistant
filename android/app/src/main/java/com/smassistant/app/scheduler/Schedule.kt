package com.smassistant.app.scheduler

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** How often a scheduler posts (docs/requirements/13-scheduled-posts.md, SC-4). */
enum class Frequency(val label: String) {
    HOURLY("Every few hours"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

/**
 * When a scheduler runs. Which fields apply depends on [frequency]:
 * - HOURLY: every [everyHours] hours from [windowStart] up to [windowEnd] (the hours the user is happy to post in).
 * - DAILY: at [time] every day.
 * - WEEKLY: at [time] on each of [days].
 * - MONTHLY: at [time] on [dayOfMonth] (1–28, or [LAST_DAY]).
 *
 * HOURLY and DAILY can skip Saturday and Sunday with [weekdaysOnly]. Times are local, so a run
 * stays at 09:00 across daylight saving changes.
 */
data class Schedule(
    val frequency: Frequency = Frequency.DAILY,
    val time: LocalTime = LocalTime.of(9, 0),
    val everyHours: Int = 4,
    val windowStart: LocalTime = LocalTime.of(9, 0),
    val windowEnd: LocalTime = LocalTime.of(18, 0),
    val days: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
    val dayOfMonth: Int = 1,
    val weekdaysOnly: Boolean = false,
) {
    /**
     * The first run strictly after [after], in [after]'s time zone. There is always one: a weekly
     * schedule with no days runs on Mondays, and a day of month out of range runs on the last day.
     */
    fun nextRun(after: ZonedDateTime): ZonedDateTime {
        val zone = after.zone
        val start = after.toLocalDate()
        return when (frequency) {
            Frequency.HOURLY -> days(start).flatMap { hourlySlots(it, zone) }.first { it.isAfter(after) }
            Frequency.DAILY -> days(start).map { at(it, time, zone) }.first { it.isAfter(after) }
            Frequency.WEEKLY -> {
                val chosen = days.ifEmpty { setOf(DayOfWeek.MONDAY) }
                days(start).filter { it.dayOfWeek in chosen }.map { at(it, time, zone) }.first { it.isAfter(after) }
            }
            Frequency.MONTHLY -> (0L..13L).asSequence()
                .map { monthDay(start.withDayOfMonth(1).plusMonths(it)) }
                .map { at(it, time, zone) }
                .first { it.isAfter(after) }
        }
    }

    /** Today and the next eight days, without weekends when [weekdaysOnly] applies. */
    private fun days(start: LocalDate): Sequence<LocalDate> = (0L..8L).asSequence()
        .map { start.plusDays(it) }
        .filter { !skipsWeekends() || it.dayOfWeek !in WEEKEND }

    private fun skipsWeekends() = weekdaysOnly && (frequency == Frequency.HOURLY || frequency == Frequency.DAILY)

    /**
     * [windowStart], then every [everyHours] hours up to and including [windowEnd]. A window that
     * ends at or before it starts covers the whole day from [windowStart].
     */
    private fun hourlySlots(date: LocalDate, zone: ZoneId): Sequence<ZonedDateTime> {
        val step = everyHours.coerceIn(1, 24).toLong()
        val first = date.atTime(windowStart)
        val last = if (windowEnd.isAfter(windowStart)) date.atTime(windowEnd) else first.plusHours(23).plusMinutes(59)
        return generateSequence(first) { it.plusHours(step) }
            .takeWhile { !it.isAfter(last) }
            .map { it.atZone(zone) }
    }

    private fun monthDay(firstOfMonth: LocalDate): LocalDate =
        if (dayOfMonth == LAST_DAY || dayOfMonth > firstOfMonth.lengthOfMonth()) {
            firstOfMonth.with(TemporalAdjusters.lastDayOfMonth())
        } else {
            firstOfMonth.withDayOfMonth(dayOfMonth.coerceAtLeast(1))
        }

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId) = ZonedDateTime.of(date, time, zone)

    /** "Daily at 09:00", "Every 4 hours, 09:00–18:00, weekdays", "Tue, Thu at 09:00", "Monthly on the last day at 09:00". */
    fun describe(): String = when (frequency) {
        Frequency.HOURLY -> {
            val every = if (everyHours <= 1) "Every hour" else "Every $everyHours hours"
            val window = if (windowEnd.isAfter(windowStart)) ", $windowStart–$windowEnd" else " from $windowStart"
            every + window + if (weekdaysOnly) ", weekdays" else ""
        }
        Frequency.DAILY -> (if (weekdaysOnly) "Weekdays" else "Daily") + " at $time"
        Frequency.WEEKLY -> days.ifEmpty { setOf(DayOfWeek.MONDAY) }.sorted()
            .joinToString(", ") { shortName(it) } + " at $time"
        Frequency.MONTHLY -> "Monthly on the " + (if (dayOfMonth == LAST_DAY) "last day" else ordinal(dayOfMonth)) + " at $time"
    }

    companion object {
        /** [dayOfMonth] for the last day of every month. */
        const val LAST_DAY = 0

        /** The interval choices offered for HOURLY. */
        val HOUR_STEPS = listOf(1, 2, 3, 4, 6, 8, 12)

        /** Days offered for MONTHLY: 1–28 exist in every month, then "Last day". */
        val MONTH_DAYS = (1..28).toList() + LAST_DAY

        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun shortName(day: DayOfWeek) = day.name.take(1) + day.name.substring(1, 3).lowercase()

        fun ordinal(n: Int): String = n.toString() + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}

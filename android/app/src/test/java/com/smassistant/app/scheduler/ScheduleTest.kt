package com.smassistant.app.scheduler

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 10) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    // 2026-10-23 is a Friday.
    private val fridayEvening = at(23, 17, 30)

    @Test
    fun `daily runs at the time today, or tomorrow once it has passed`() {
        val daily = Schedule(Frequency.DAILY, time = LocalTime.of(18, 0))
        assertEquals(at(23, 18), daily.nextRun(fridayEvening))
        assertEquals(at(24, 18), daily.nextRun(at(23, 18)))
    }

    @Test
    fun `weekdays only skips the weekend`() {
        val weekdays = Schedule(Frequency.DAILY, time = LocalTime.of(9, 0), weekdaysOnly = true)
        assertEquals(at(26, 9), weekdays.nextRun(fridayEvening))
    }

    @Test
    fun `every few hours stays inside the window`() {
        val hourly = Schedule(Frequency.HOURLY, everyHours = 4, windowStart = LocalTime.of(9, 0), windowEnd = LocalTime.of(18, 0))
        assertEquals(at(23, 13), hourly.nextRun(at(23, 9)))
        assertEquals(at(23, 17), hourly.nextRun(at(23, 13)))
        assertEquals(at(24, 9), hourly.nextRun(fridayEvening))
        assertEquals(at(26, 9), hourly.copy(weekdaysOnly = true).nextRun(fridayEvening))
    }

    @Test
    fun `a window ending at its start covers the whole day`() {
        val allDay = Schedule(Frequency.HOURLY, everyHours = 1, windowStart = LocalTime.MIDNIGHT, windowEnd = LocalTime.MIDNIGHT)
        assertEquals(at(23, 23), allDay.nextRun(at(23, 22, 10)))
        assertEquals(at(24, 0), allDay.nextRun(at(23, 23, 10)))
    }

    @Test
    fun `weekly runs on the next chosen day`() {
        val weekly = Schedule(Frequency.WEEKLY, time = LocalTime.of(9, 0), days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(at(27, 9), weekly.nextRun(fridayEvening))
        assertEquals(at(29, 9), weekly.nextRun(at(27, 9)))
        assertEquals(at(26, 9), weekly.copy(days = emptySet()).nextRun(fridayEvening))
    }

    @Test
    fun `monthly runs on the day, the last day, or the month's end when it is short`() {
        val monthly = Schedule(Frequency.MONTHLY, time = LocalTime.of(9, 0), dayOfMonth = 1)
        assertEquals(at(1, 9, month = 11), monthly.nextRun(fridayEvening))
        assertEquals(at(31, 9), monthly.copy(dayOfMonth = Schedule.LAST_DAY).nextRun(fridayEvening))
        assertEquals(at(30, 9, month = 11), monthly.copy(dayOfMonth = 31).nextRun(at(1, 0, month = 11)))
    }

    @Test
    fun `local time is kept across the clock change`() {
        // UK clocks go back on 2026-10-25.
        val daily = Schedule(Frequency.DAILY, time = LocalTime.of(9, 0))
        val next = daily.nextRun(at(24, 10))
        assertEquals(LocalTime.of(9, 0), next.toLocalTime())
        assertEquals(25, next.dayOfMonth)
    }

    @Test
    fun `describes each frequency`() {
        assertEquals("Daily at 09:00", Schedule(Frequency.DAILY).describe())
        assertEquals("Weekdays at 09:00", Schedule(Frequency.DAILY, weekdaysOnly = true).describe())
        assertEquals("Every 4 hours, 09:00–18:00", Schedule(Frequency.HOURLY).describe())
        assertEquals("Every hour, 09:00–18:00, weekdays", Schedule(Frequency.HOURLY, everyHours = 1, weekdaysOnly = true).describe())
        assertEquals("Tue, Thu at 09:00", Schedule(Frequency.WEEKLY).describe())
        assertEquals("Monthly on the 1st at 09:00", Schedule(Frequency.MONTHLY).describe())
        assertEquals("Monthly on the last day at 09:00", Schedule(Frequency.MONTHLY, dayOfMonth = Schedule.LAST_DAY).describe())
        assertEquals("22nd", Schedule.ordinal(22))
        assertEquals("11th", Schedule.ordinal(11))
    }
}

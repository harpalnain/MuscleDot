package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.EnumSet

class WeekCountTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    @Test
    fun countsSavedDaysThisWeekAgainstScheduledDays() = assertEquals(
        WeekCount(done = 3, planned = 6),
        weekCount(listOf(d(2), d(9), d(10), d(11)), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), d(12)),
    )

    @Test
    fun emptyHistory_andEmptySchedule() =
        assertEquals(WeekCount(0, 0), weekCount(emptyList(), emptySet(), d(12)))
}

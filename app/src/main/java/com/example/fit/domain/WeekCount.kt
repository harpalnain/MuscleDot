package com.example.fit.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** "X of Y done" for the week on the main screen (spec 5.8). */
data class WeekCount(val done: Int, val planned: Int)

/**
 * [done] is the number of days in [today]'s week (Monday to Sunday) that have a
 * saved workout; [planned] is the number of days the weekly schedule has a routine on.
 */
fun weekCount(savedDates: Collection<LocalDate>, scheduledDays: Set<DayOfWeek>, today: LocalDate): WeekCount {
    val thisWeek = weekStart(today)
    val done = savedDates.filter { weekStart(it) == thisWeek }.distinct().size
    return WeekCount(done, scheduledDays.size)
}

package com.example.fit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** The app's day runs from 04:00 to 03:59 local time, so a late session stays one workout. */
const val DAY_START_HOUR = 4

fun interface AppClock {
    fun now(): LocalDateTime
}

/** The app day that [now] falls in. */
fun appDay(now: LocalDateTime): LocalDate = now.minusHours(DAY_START_HOUR.toLong()).toLocalDate()

/** The Monday on or before [date]. Weeks run Monday to Sunday. */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AppDayTest {
    @Test
    fun dayStartsAtFourAm() {
        assertEquals(LocalDate.of(2026, 11, 12), appDay(LocalDateTime.of(2026, 11, 12, 4, 0)))
        assertEquals(LocalDate.of(2026, 11, 11), appDay(LocalDateTime.of(2026, 11, 12, 3, 59)))
    }

    @Test
    fun lateSessionStaysOnTheDayItStarted() =
        assertEquals(LocalDate.of(2026, 11, 12), appDay(LocalDateTime.of(2026, 11, 13, 0, 30)))

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 12))) // Thursday
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 9))) // Monday
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 15))) // Sunday
    }
}

package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test
    fun kilograms() {
        assertEquals("1,010 kg", formatKg(10100))
        assertEquals("42.5 kg", formatKg(425))
        assertEquals("0 kg", formatKg(0))
        assertEquals("17,200 kg", formatKg(172000))
        assertEquals("40.0", formatStepWeight(400))
        assertEquals("42.5", formatStepWeight(425))
    }

    @Test
    fun axisLabels() {
        assertEquals("600", formatAxis(600.0))
        assertEquals("1,000", formatAxis(1000.0))
        assertEquals("20K", formatAxis(20000.0))
        assertEquals("17.2K", formatAxis(17200.0))
    }

    @Test
    fun axisLabelsOfOneAxisNeverRepeat() {
        // Round ticks keep the short form from 10,000 up.
        assertEquals(listOf("0", "5,000", "10K"), formatAxisLabels(listOf(0.0, 5000.0, 10000.0)))
        assertEquals(listOf("10K", "10.1K", "10.2K"), formatAxisLabels(listOf(10000.0, 10100.0, 10200.0)))
        // Ticks closer than 100 apart would all round to the same short label, so they are written in full.
        assertEquals(listOf("15,000", "15,025", "15,050"), formatAxisLabels(listOf(15000.0, 15025.0, 15050.0)))
        assertEquals(listOf("600", "800", "1,000"), formatAxisLabels(listOf(600.0, 800.0, 1000.0)))
        assertEquals(emptyList<String>(), formatAxisLabels(emptyList()))
    }

    @Test
    fun bodyWeightParsing() {
        assertEquals(723, parseKgToHg("72.3"))
        assertEquals(720, parseKgToHg("72"))
        assertEquals(720, parseKgToHg(" 72.0 "))
        assertNull(parseKgToHg("72.35"))
        assertNull(parseKgToHg("abc"))
        assertNull(parseKgToHg(""))
        assertNull(parseKgToHg("19.9"))
        assertNull(parseKgToHg("400.1"))
    }

    @Test
    fun age() {
        assertEquals(30, ageOn(LocalDate.of(1996, 3, 14), LocalDate.of(2026, 11, 12)))
        assertEquals(29, ageOn(LocalDate.of(1996, 12, 1), LocalDate.of(2026, 11, 12)))
    }

    @Test
    fun dates() {
        assertEquals("Thursday, 12 November", formatDayLong(LocalDate.of(2026, 11, 12)))
        assertEquals("5 Nov", formatDayShort(LocalDate.of(2026, 11, 5)))
    }
}

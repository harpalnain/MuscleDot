package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MovingAverageTest {
    @Test
    fun usesFewerValuesWhileHistoryIsShort() =
        assertEquals(listOf(720.0, 740.0, 740.0, 755.0), movingAverage(listOf(720.0, 760.0, 740.0, 800.0), 10))

    @Test
    fun windowSlides() =
        assertEquals(listOf(1.0, 1.5, 2.5, 3.5, 4.5), movingAverage(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 2))

    @Test
    fun mockupSeriesEndsAt893() { // the series drawn in mockup 03, screen 1
        val loads = listOf(720, 760, 740, 800, 780, 840, 820, 880, 860, 900, 940, 920, 1010, 980).map { it.toDouble() }
        assertEquals(893.0, movingAverage(loads, 10).last(), 1e-9)
    }

    @Test
    fun emptyHistory_givesEmptyAverage() = assertEquals(emptyList<Double>(), movingAverage(emptyList(), 10))
}

package com.example.fit.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepRepeatTest {
    @Test
    fun schedule() {
        assertNull(repeatIntervalMs(0))
        assertNull(repeatIntervalMs(399))
        assertEquals(150L, repeatIntervalMs(400))
        assertEquals(150L, repeatIntervalMs(1499))
        assertEquals(50L, repeatIntervalMs(1500))
        assertEquals(50L, repeatIntervalMs(60_000))
    }
}

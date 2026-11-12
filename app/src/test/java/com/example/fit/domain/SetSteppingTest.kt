package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SetSteppingTest {
    @Test
    fun weightMovesInHalfKilos() {
        assertEquals(SetValues(405, 10), SetValues(400, 10).stepped(SetField.WEIGHT, +1))
        assertEquals(SetValues(390, 10), SetValues(400, 10).stepped(SetField.WEIGHT, -2))
    }

    @Test
    fun valuesStayInsideLimits() {
        assertEquals(0, SetValues(weightHg = 0).stepped(SetField.WEIGHT, -1).weightHg)
        assertEquals(9995, SetValues(weightHg = 9995).stepped(SetField.WEIGHT, +1).weightHg)
        assertEquals(1, SetValues(reps = 1).stepped(SetField.REPS, -1).reps)
        assertEquals(999, SetValues(reps = 999).stepped(SetField.REPS, +1).reps)
        assertEquals(1, SetValues(minutes = 1).stepped(SetField.MINUTES, -1).minutes)
        assertEquals(999, SetValues(minutes = 999).stepped(SetField.MINUTES, +1).minutes)
    }

    @Test
    fun steppingOneFieldLeavesTheOthers() =
        assertEquals(SetValues(400, 11, 0), SetValues(400, 10, 0).stepped(SetField.REPS, +1))
}

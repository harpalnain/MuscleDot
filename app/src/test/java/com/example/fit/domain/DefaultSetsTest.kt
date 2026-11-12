package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSetsTest {
    private fun three(hg: Int) = List(3) { SetValues(hg, 10) }

    @Test
    fun weightsStartByEquipment() {
        assertEquals(three(200), defaultStartingSets(ExerciseKind.WEIGHTS, "barbell"))
        assertEquals(three(50), defaultStartingSets(ExerciseKind.WEIGHTS, "dumbbell"))
        assertEquals(three(100), defaultStartingSets(ExerciseKind.WEIGHTS, "cable"))
        assertEquals(three(100), defaultStartingSets(ExerciseKind.WEIGHTS, "machine"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.WEIGHTS, "other"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.WEIGHTS, null))
    }

    @Test
    fun otherKinds() {
        assertEquals(three(0), defaultStartingSets(ExerciseKind.BODY_WEIGHT, "body only"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.REPS_ONLY, "body only"))
        assertEquals(listOf(SetValues(minutes = 10)), defaultStartingSets(ExerciseKind.CARDIO, "machine"))
    }
}

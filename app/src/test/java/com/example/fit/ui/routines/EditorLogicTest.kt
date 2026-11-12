package com.example.fit.ui.routines

import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.SetValues
import com.example.fit.domain.setsSummary
import com.example.fit.ui.picker.PickerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {
    @Test
    fun dropIndexFollowsTheDrag() {
        assertEquals(2, dropIndex(0, 130f, 64f, 5))
        assertEquals(1, dropIndex(1, 20f, 64f, 5))
        assertEquals(0, dropIndex(3, -1000f, 64f, 5))
        assertEquals(4, dropIndex(3, 1000f, 64f, 5))
    }

    @Test
    fun summaries() {
        assertEquals("3 sets · 40 kg × 10", setsSummary(ExerciseKind.WEIGHTS, List(3) { SetValues(400, 10) }))
        assertEquals("1 set · 42.5 kg × 8", setsSummary(ExerciseKind.WEIGHTS, listOf(SetValues(425, 8))))
        assertEquals("3 sets · 10 reps", setsSummary(ExerciseKind.BODY_WEIGHT, List(3) { SetValues(0, 10) }))
        assertEquals("3 sets · +5 kg × 10", setsSummary(ExerciseKind.BODY_WEIGHT, List(3) { SetValues(50, 10) }))
        assertEquals("3 sets · 20 reps", setsSummary(ExerciseKind.REPS_ONLY, List(3) { SetValues(0, 20) }))
        assertEquals("10 min", setsSummary(ExerciseKind.CARDIO, listOf(SetValues(minutes = 10))))
    }

    @Test
    fun pickerOffersCreateOnlyForANewName() {
        val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
        assertFalse(PickerUiState(query = "", results = listOf(bench)).offerCreate)
        assertFalse(PickerUiState(query = "bench press", results = listOf(bench)).offerCreate)
        assertTrue(PickerUiState(query = "press", results = listOf(bench)).offerCreate)
    }
}

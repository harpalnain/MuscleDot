package com.example.fit.ui.routines

import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.Routine
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetValues
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RoutinesStateTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val chest = Routine(1, "Chest", listOf(RoutineExercise(bench, List(3) { SetValues(200, 10) })))
    private val empty = Routine(2, "Empty", emptyList())

    @Test
    fun buildsSevenDaysAndTheRoutineList() {
        val state = buildRoutinesState(
            listOf(chest, empty),
            mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.THURSDAY to 99L),
            LocalDate.of(2026, 11, 12),
        )
        assertEquals(DayOfWeek.entries.toList(), state.week.map { it.day })
        assertEquals(WeekDayRow(DayOfWeek.MONDAY, 1, "Chest", isToday = false), state.week[0])
        // an unknown routine id reads as a rest day
        assertEquals(WeekDayRow(DayOfWeek.THURSDAY, null, null, isToday = true), state.week[3])
        assertEquals(listOf(RoutineRow(1, "Chest", 1), RoutineRow(2, "Empty", 0)), state.routines)
    }
}

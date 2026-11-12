package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LoadTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)

    private fun ex(kind: ExerciseKind, vararg sets: WorkoutSet) = WorkoutExercise(bench.copy(kind = kind), sets.toList())

    private fun set(pos: Int, hg: Int, reps: Int, done: Boolean = true, minutes: Int = 0) =
        WorkoutSet(pos, SetValues(hg, reps, minutes), done)

    @Test
    fun exerciseLoadAddsTickedSets() { // spec example: 40x10 + 42.5x8 + 45x6 = 1010 kg
        val e = ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8), set(2, 450, 6))
        assertEquals(10100L, e.loadHg(null))
    }

    @Test
    fun untickedSetsAddNothing() {
        val e = ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8), set(2, 450, 6, done = false))
        assertEquals(7400L, e.loadHg(null))
        assertEquals(18, e.totalReps())
    }

    @Test
    fun bodyWeightExerciseUsesBodyWeightPlusExtra() { // spec example: 10 pull-ups at 70 kg = 700 kg
        assertEquals(7000L, ex(ExerciseKind.BODY_WEIGHT, set(0, 0, 10)).loadHg(700))
        assertEquals(7500L, ex(ExerciseKind.BODY_WEIGHT, set(0, 50, 10)).loadHg(700))
        assertEquals(750, effectiveWeightHg(ExerciseKind.BODY_WEIGHT, 50, 700))
    }

    @Test
    fun repsOnlyAndCardioHaveNoLoad() {
        assertEquals(0L, ex(ExerciseKind.REPS_ONLY, set(0, 0, 20)).loadHg(700))
        val cardio = ex(ExerciseKind.CARDIO, set(0, 0, 0, minutes = 25))
        assertEquals(0L, cardio.loadHg(700))
        assertEquals(25, cardio.totalMinutes())
    }

    @Test
    fun workoutTotals() {
        val w = Workout(
            1, LocalDate.of(2026, 11, 9), 1, "Chest", WorkoutStatus.FINISHED, 700,
            listOf(
                ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8, done = false)),
                ex(ExerciseKind.CARDIO, set(0, 0, 0, minutes = 25)),
            ),
        )
        assertEquals(4000L, w.loadHg())
        assertEquals(25, w.cardioMinutes())
        assertEquals(2, w.doneSetCount())
    }
}

package com.example.fit.ui.history

import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.SetValues
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutExercise
import com.example.fit.domain.WorkoutSet
import com.example.fit.domain.WorkoutStatus
import com.example.fit.domain.formatMonth
import com.example.fit.domain.formatWeekdayShort
import com.example.fit.domain.tickedOnly
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryStateTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY)

    private fun ex(e: Exercise, vararg sets: Pair<SetValues, Boolean>) =
        WorkoutExercise(e, sets.mapIndexed { i, (v, done) -> WorkoutSet(i, v, done) })

    private fun workout(id: Long, date: LocalDate, name: String, vararg ex: WorkoutExercise) =
        Workout(id, date, 1, name, WorkoutStatus.FINISHED, null, ex.toList())

    private val saved = listOf(
        workout(1, LocalDate.of(2026, 10, 30), "Chest", ex(bench, SetValues(400, 10) to true)),
        workout(2, LocalDate.of(2026, 11, 2), "Chest", ex(bench, SetValues(400, 10) to true, SetValues(450, 6) to true)),
        workout(3, LocalDate.of(2026, 11, 7), "Cardio", ex(run, SetValues(minutes = 35) to true)),
        workout(4, LocalDate.of(2026, 11, 9), "Core", ex(crunch, SetValues(0, 60) to true, SetValues(0, 60) to true)),
    )

    @Test
    fun groupsByMonthNewestFirst_withTheRightMetric() {
        val months = buildHistory(saved)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(listOf(4L, 3L, 2L), months[0].rows.map { it.workoutId })
        assertEquals(listOf("120 reps", "35 min", "670 kg"), months[0].rows.map { it.metric })
        // against 30 Oct: max weight 45 > 40 and session 670 > 400 kg are records; best set ties at 400 kg, so it is not
        assertEquals(
            HistoryRow(2, LocalDate.of(2026, 11, 2), "Chest", exerciseCount = 1, setCount = 2, metric = "670 kg", records = 2),
            months[0].rows[2],
        )
        assertEquals(0, months[1].rows[0].records)
    }

    @Test
    fun emptyHistory_isAnEmptyList() = assertEquals(emptyList<HistoryMonth>(), buildHistory(emptyList()))

    @Test
    fun tickedOnlyDropsUntickedSetsAndEmptyExercises() {
        val mixed = workout(
            9, LocalDate.of(2026, 11, 9), "Chest",
            ex(bench, SetValues(400, 10) to true, SetValues(425, 8) to false, SetValues(450, 6) to true),
            ex(run, SetValues(minutes = 10) to false),
        )
        val ticked = mixed.tickedOnly()
        assertEquals(listOf("bench-press"), ticked.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 2), ticked.exercises[0].sets.map { it.position })
    }

    @Test
    fun monthHeadingAndWeekday() {
        assertEquals("November 2026", formatMonth(YearMonth.of(2026, 11)))
        assertEquals("Wed", formatWeekdayShort(LocalDate.of(2026, 11, 11)))
    }
}

package com.example.fit.ui.progress

import com.example.fit.domain.ChartPoint
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.Routine
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetValues
import com.example.fit.domain.WeekTotal
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutExercise
import com.example.fit.domain.WorkoutSet
import com.example.fit.domain.WorkoutStatus
import com.example.fit.ui.components.ColumnDatum
import com.example.fit.ui.exercise.RoutineFilter
import com.example.fit.ui.exercise.buildExerciseDetailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressStateTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)

    private fun ex(e: Exercise, vararg v: SetValues) = WorkoutExercise(e, v.mapIndexed { i, s -> WorkoutSet(i, s, true) })

    private fun workout(id: Long, day: Int, routineId: Long?, vararg ex: WorkoutExercise) =
        Workout(id, d(day), routineId, "R$routineId", WorkoutStatus.FINISHED, null, ex.toList())

    private val chest = Routine(1, "Chest", listOf(RoutineExercise(bench, emptyList()), RoutineExercise(run, emptyList())))
    private val full = Routine(2, "Full Body", listOf(RoutineExercise(bench, emptyList())))
    private val unused = Routine(3, "Unused", emptyList())
    private val saved = listOf(
        workout(1, 2, 1, ex(bench, SetValues(400, 10), SetValues(425, 8)), ex(run, SetValues(minutes = 20))), // 740 kg
        workout(2, 5, 2, ex(bench, SetValues(450, 6)), ex(crunch, SetValues(0, 25))), // 270 kg
        workout(3, 9, 1, ex(bench, SetValues(400, 10), SetValues(425, 8), SetValues(450, 6))), // 1010 kg
    )

    @Test
    fun exerciseRowsListOnlyExercisesWithHistory() = assertEquals(
        listOf("Bench Press" to "Max 45 kg", "Crunches" to "Most 25 reps in a set", "Treadmill" to "Longest 20 min"),
        exerciseProgressRows(saved).map { it.exercise.name to it.headline },
    )

    @Test
    fun emptyHistory_givesEmptyProgress() {
        assertEquals(emptyList<ExerciseProgressRow>(), exerciseProgressRows(emptyList()))
        assertEquals(WeeklyProgress(emptyList(), null, null), weeklyProgress(emptyList(), d(12)))
        val none = routineProgress(chest, emptyList(), 10)
        assertNull(none.best)
        assertNull(none.last)
        assertTrue(none.series.points.isEmpty())
    }

    @Test
    fun routineProgressUsesOnlyThatRoutinesWorkouts() {
        val progress = routineProgress(chest, saved, 10)
        assertEquals(listOf(740.0, 1010.0), progress.series.points.map { it.value })
        assertEquals(ChartPoint(d(9), 1010.0), progress.best)
        assertEquals(ChartPoint(d(9), 1010.0), progress.last)
        assertEquals(listOf("Bench Press", "Treadmill"), progress.exercises.map { it.exercise.name })
    }

    @Test
    fun exerciseDetailOffersFiltersForRoutinesWithSessions() {
        val state = buildExerciseDetailState(bench, saved, listOf(chest, full, unused), selectedRoutineId = null, n = 10)
        assertEquals(listOf(RoutineFilter(1, "Chest"), RoutineFilter(2, "Full Body")), state.filters)
        assertEquals(listOf(740.0, 270.0, 1010.0), state.series.points.map { it.value })
        assertEquals(450, state.records.maxWeightHg?.value)
        val filtered = buildExerciseDetailState(bench, saved, listOf(chest, full, unused), selectedRoutineId = 2, n = 10)
        assertEquals(listOf(270.0), filtered.series.points.map { it.value })
        assertEquals(450, filtered.records.maxWeightHg?.value) // records ignore the filter
    }

    @Test
    fun weeklyProgressAndColumns() {
        val weekly = weeklyProgress(saved, d(12))
        assertEquals(listOf(d(2), d(9)), weekly.weeks.map { it.weekStart })
        assertEquals(WeekTotal(d(9), 10100, 0, 1), weekly.thisWeek)
        // Both weeks total 1,010 kg; the earlier one stays the best, a later equal week is not a new best.
        assertEquals(WeekTotal(d(2), 10100, 20, 2), weekly.bestWeek)
        val load = weekColumns(weekly.weeks, cardio = false)
        assertEquals(ColumnDatum("2 Nov", 1010.0, "Week of 2 Nov · 1,010 kg"), load[0])
        assertEquals(ColumnDatum("9 Nov", 1010.0, "This week so far · 1,010 kg", muted = true, note = "so far"), load[1])
        assertEquals(ColumnDatum("2 Nov", 20.0, "Week of 2 Nov · 20 min"), weekColumns(weekly.weeks, cardio = true)[0])
    }
}

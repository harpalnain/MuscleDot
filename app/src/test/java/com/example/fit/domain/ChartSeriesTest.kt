package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ChartSeriesTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)

    private fun session(day: Int, routineId: Long, vararg sets: SetValues, kind: ExerciseKind = ExerciseKind.WEIGHTS) =
        ExerciseSession(day.toLong(), d(11, day), routineId, kind, null, sets.toList())

    private val sessions = listOf(
        session(2, 1, s(400, 10), s(425, 8)), // 740 kg
        session(5, 2, s(400, 10), s(425, 8), s(450, 6)), // 1010 kg
        session(9, 1, s(400, 10), s(425, 8), s(450, 5)), // 965 kg
    )

    @Test
    fun exerciseSeriesInKilogramsWithAverage() {
        val series = exerciseSeries(ExerciseKind.WEIGHTS, sessions, routineId = null, n = 2)
        assertEquals(Measure.LOAD_KG, series.measure)
        assertEquals(listOf(740.0, 1010.0, 965.0), series.points.map { it.value })
        assertEquals(listOf(d(11, 2), d(11, 5), d(11, 9)), series.points.map { it.date })
        assertEquals(listOf(740.0, 875.0, 987.5), series.average)
    }

    @Test
    fun routineFilterChangesTheAverageToo() {
        val series = exerciseSeries(ExerciseKind.WEIGHTS, sessions, routineId = 1, n = 2)
        assertEquals(listOf(740.0, 965.0), series.points.map { it.value })
        assertEquals(listOf(740.0, 852.5), series.average)
    }

    @Test
    fun repsOnlyAndCardioUseTheirOwnMeasure() {
        val reps = exerciseSeries(
            ExerciseKind.REPS_ONLY,
            listOf(session(2, 1, s(0, 20), s(0, 15), kind = ExerciseKind.REPS_ONLY)),
            null,
            10,
        )
        assertEquals(Measure.REPS, reps.measure)
        assertEquals(listOf(35.0), reps.points.map { it.value })
        val cardio = exerciseSeries(
            ExerciseKind.CARDIO,
            listOf(session(2, 1, SetValues(minutes = 25), kind = ExerciseKind.CARDIO)),
            null,
            10,
        )
        assertEquals(Measure.MINUTES, cardio.measure)
        assertEquals(listOf(25.0), cardio.points.map { it.value })
    }

    @Test
    fun emptyHistory_givesEmptySeries() {
        assertEquals(
            SessionSeries(Measure.LOAD_KG, emptyList(), emptyList()),
            exerciseSeries(ExerciseKind.WEIGHTS, emptyList(), null, 10),
        )
        assertEquals(emptyList<ChartPoint>(), routineSeries(emptyList(), 10).points)
        assertEquals(emptyList<WeekTotal>(), weeklyTotals(emptyList(), d(11, 12)))
    }

    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, null, "machine", ExerciseKind.CARDIO)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abs", "body only", ExerciseKind.REPS_ONLY)

    private fun workout(id: Long, date: LocalDate, vararg ex: WorkoutExercise) =
        Workout(id, date, 1, "R", WorkoutStatus.FINISHED, null, ex.toList())

    private fun lift(hg: Int, reps: Int) = WorkoutExercise(bench, listOf(WorkoutSet(0, s(hg, reps), true)))
    private fun cardio(min: Int) = WorkoutExercise(run, listOf(WorkoutSet(0, SetValues(minutes = min), true)))
    private fun crunches(reps: Int) = WorkoutExercise(crunch, listOf(WorkoutSet(0, s(0, reps), true)))

    @Test
    fun routineSeriesPicksItsMeasure() {
        assertEquals(Measure.LOAD_KG, routineSeries(listOf(workout(1, d(11, 2), lift(400, 10), cardio(10))), 10).measure)
        val cardioOnly = routineSeries(listOf(workout(1, d(11, 2), cardio(20)), workout(2, d(11, 5), cardio(35))), 10)
        assertEquals(Measure.MINUTES, cardioOnly.measure)
        assertEquals(listOf(20.0, 35.0), cardioOnly.points.map { it.value })
        assertEquals(Measure.REPS, routineSeries(listOf(workout(1, d(11, 2), crunches(40))), 10).measure)
    }

    @Test
    fun weeklyTotalsIncludeEmptyWeeksUpToToday() = assertEquals(
        listOf(
            WeekTotal(d(10, 19), 4000, 0, 1),
            WeekTotal(d(10, 26), 0, 0, 0),
            WeekTotal(d(11, 2), 14100, 0, 2),
            WeekTotal(d(11, 9), 5000, 20, 1),
        ),
        weeklyTotals(
            listOf(
                workout(1, d(10, 19), lift(400, 10)),
                workout(2, d(11, 2), lift(400, 10)),
                workout(3, d(11, 5), lift(1010, 10)),
                workout(4, d(11, 12), lift(500, 10), cardio(20)),
            ),
            today = d(11, 12),
        ),
    )

    @Test
    fun axisTicks() {
        assertEquals(listOf(0.0, 10000.0, 20000.0), niceTicks(0.0, 17200.0))
        assertEquals(listOf(4000.0, 4500.0, 5000.0), niceTicks(4100.0, 4820.0))
        assertEquals(listOf(600.0, 800.0, 1000.0, 1200.0), niceTicks(720.0, 1010.0))
        assertEquals(listOf(499.0, 500.0, 501.0), niceTicks(500.0, 500.0))
    }
}

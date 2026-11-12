package com.example.fit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.EnumSet

class TodayStateTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val chest = Routine(
        1,
        "Chest",
        listOf(
            RoutineExercise(bench, listOf(SetValues(400, 10), SetValues(425, 8), SetValues(450, 6))),
            RoutineExercise(run, listOf(SetValues(minutes = 10))),
        ),
    )
    private val days = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

    private fun sets(vararg done: Boolean, hg: List<Int> = listOf(400, 425, 450), reps: List<Int> = listOf(10, 8, 6)) =
        done.mapIndexed { i, d -> WorkoutSet(i, SetValues(hg[i], reps[i]), d) }

    private fun workout(status: WorkoutStatus, benchSets: List<WorkoutSet>, date: LocalDate = monday, id: Long = 7) = Workout(
        id, date, 1, "Chest", status, null,
        listOf(WorkoutExercise(bench, benchSets), WorkoutExercise(run, listOf(WorkoutSet(0, SetValues(minutes = 10), false)))),
    )

    private val lastWeek =
        workout(WorkoutStatus.FINISHED, sets(true, true, hg = listOf(400, 425), reps = listOf(10, 8)), monday.minusDays(7), id = 3)

    private fun input(
        workout: Workout?,
        scheduled: Routine? = chest,
        saved: List<Workout> = listOf(lastWeek),
        open: String? = null,
    ) = TodayInput(monday, workout, scheduled, listOf(chest), saved, days, open)

    @Test
    fun restDayWhenNothingIsScheduled() {
        val state = buildTodayState(input(null, scheduled = null)) as TodayState.RestDay
        assertEquals(WeekCount(0, 5), state.week)
        assertEquals(listOf(chest), state.routines)
    }

    @Test
    fun scheduledRoutineIsShownAsAPreview() {
        val state = buildTodayState(input(null)) as TodayState.Active
        assertTrue(state.isPreview)
        assertEquals("Chest", state.routineName)
        assertEquals("bench-press", state.openExerciseId)
        assertEquals(listOf(400, 425, 450), state.exercises[0].sets.map { it.values.weightHg })
        assertEquals(0, state.tickedCount)
        assertEquals(4, state.untickedCount)
        assertTrue(state.canChangeRoutine)
        assertFalse(state.canFinish)
        assertEquals(425, state.exercises[0].bestWeightHg)
    }

    @Test
    fun activeWorkoutCountsLoadTicksAndRecords() {
        val state = buildTodayState(input(workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)))) as TodayState.Active
        assertFalse(state.isPreview)
        assertEquals(10100L, state.totalLoadHg)
        assertEquals(3, state.tickedCount)
        assertEquals(1, state.untickedCount)
        assertFalse(state.canChangeRoutine)
        assertTrue(state.canFinish)
        assertEquals(listOf(false, false, true), state.exercises[0].sets.map { it.isRecord }) // 45 kg beats last week's 42.5
        assertEquals(3, state.exercises[0].doneCount)
        assertEquals("treadmill", state.openExerciseId)
    }

    @Test
    fun firstEverSessionShowsNoRecordBadge() {
        val state = buildTodayState(
            input(workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)), saved = emptyList()),
        ) as TodayState.Active
        assertTrue(state.exercises[0].sets.none { it.isRecord })
        assertNull(state.exercises[0].bestWeightHg)
    }

    @Test
    fun finishedWorkoutShowsItsSummaryAndCountsForTheWeek() {
        val done = workout(WorkoutStatus.FINISHED, sets(true, true, true))
        val state = buildTodayState(input(done, saved = listOf(lastWeek, done))) as TodayState.Finished
        assertEquals(
            FinishSummary(
                10100L, 3, 0,
                listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT), RecordBroken("bench-press", RecordType.BEST_SESSION)),
            ),
            state.summary.copy(records = state.summary.records.sortedBy { it.type }),
        )
        assertEquals(
            listOf("Bench Press" to RecordType.MAX_WEIGHT, "Bench Press" to RecordType.BEST_SESSION),
            state.recordLines.sortedBy { it.second },
        )
        assertEquals(WeekCount(1, 5), state.week)
    }

    @Test
    fun requestedOpenExerciseIsKeptIfItExists() {
        assertEquals("treadmill", (buildTodayState(input(null, open = "treadmill")) as TodayState.Active).openExerciseId)
        assertEquals("bench-press", (buildTodayState(input(null, open = "gone")) as TodayState.Active).openExerciseId)
    }

    @Test
    fun openExerciseMovesOnWhenItsLastSetIsTicked() {
        val exercises = workout(WorkoutStatus.IN_PROGRESS, sets(true, true, false)).exercises
        assertEquals("bench-press", nextOpenExercise(exercises, "bench-press"))
        val finishedBench = workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)).exercises
        assertEquals("treadmill", nextOpenExercise(finishedBench, "bench-press"))
        val allDone = finishedBench.map { it.copy(sets = it.sets.map { s -> s.copy(done = true) }) }
        assertNull(nextOpenExercise(allDone, "treadmill"))
    }
}

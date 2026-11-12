package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.WorkoutStatus
import com.example.fit.domain.loadHg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Finishing, day close, carry-forward into the routine, and the saved history (spec 5.6, 5.7). */
@RunWith(AndroidJUnit4::class)
class WorkoutSavingTest {
    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private val monday = LocalDate.of(2026, 11, 9)
    private val tuesday = monday.plusDays(1)
    private var routineId = 0L

    private suspend fun today() = workouts.observeWorkoutOn(monday).first()!!
    private suspend fun bench() = today().exercises.first { it.exercise.id == "bench-press" }.sets
    private suspend fun routineBench() =
        routines.getRoutine(routineId)!!.exercises.first { it.exercise.id == "bench-press" }.sets

    /** Bench: set 0 at 20.5 kg ticked, set 1 ticked, set 2 changed to 9 reps but not ticked. Treadmill untouched. */
    private suspend fun trainMonday(): Long {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1)
        workouts.setDone(id, "bench-press", 0, true)
        workouts.setDone(id, "bench-press", 1, true)
        workouts.adjustSet(id, "bench-press", 2, SetField.REPS, -1)
        return id
    }

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        seedTestExercises(db)
        val starting = StartingSets(db)
        routines = RoutineRepository(db, starting)
        workouts = WorkoutRepository(db, starting)
        routineId = routines.createRoutine("Chest")
        routines.addExercise(routineId, "bench-press")
        routines.addExercise(routineId, "treadmill")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun finish_savesTickedSetsOnly_andCarriesNumbersIntoTheRoutine() = runTest {
        val id = trainMonday()
        workouts.finish(id)
        val saved = workouts.observeSaved().first().single()
        assertEquals(WorkoutStatus.FINISHED, saved.status)
        assertEquals(listOf("bench-press"), saved.exercises.map { it.exercise.id })
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), saved.exercises[0].sets.map { it.values })
        assertEquals(4050L, saved.loadHg())
        // the third set was not ticked, so it keeps its old numbers
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10), SetValues(200, 10)), routineBench())
        assertEquals(listOf(SetValues(minutes = 10)), routines.getRoutine(routineId)!!.exercises[1].sets)
    }

    @Test
    fun reopen_bringsBackTheUntickedSets() = runTest {
        val id = trainMonday()
        workouts.finish(id)
        workouts.reopen(id)
        assertEquals(WorkoutStatus.IN_PROGRESS, today().status)
        assertEquals(SetValues(200, 9), bench()[2].values)
        assertTrue(workouts.observeSaved().first().isEmpty())
        workouts.setDone(id, "bench-press", 2, true)
        workouts.finish(id)
        assertEquals(SetValues(200, 9), routineBench()[2])
    }

    @Test
    fun closeDaysBefore_savesYesterdayAndIsSafeToRepeat() = runTest {
        val id = trainMonday()
        workouts.closeDaysBefore(monday)
        assertEquals(WorkoutStatus.IN_PROGRESS, today().status) // still its own day
        repeat(2) { workouts.closeDaysBefore(tuesday) }
        val closed = workouts.observeWorkout(id).first()!!
        assertEquals(WorkoutStatus.FINISHED, closed.status)
        assertEquals(listOf("bench-press"), closed.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 1), closed.exercises[0].sets.map { it.position })
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10), SetValues(200, 10)), routineBench())
    }

    @Test
    fun closeDaysBefore_dropsAWorkoutWithNoTicks() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +4)
        workouts.closeDaysBefore(tuesday)
        assertNull(workouts.observeWorkoutOn(monday).first())
        assertTrue(workouts.observeSaved().first().isEmpty())
        assertEquals(List(3) { SetValues(200, 10) }, routineBench())
    }

    @Test
    fun finish_afterRoutineDeleted_keepsTheWorkout() = runTest {
        val id = trainMonday()
        routines.deleteRoutine(routineId)
        workouts.finish(id)
        val saved = workouts.observeSaved().first().single()
        assertNull(saved.routineId)
        assertEquals("Chest", saved.routineName)
        assertEquals(4050L, saved.loadHg())
    }

    @Test
    fun finish_afterExerciseRemovedFromRoutine_doesNotPutItBack() = runTest {
        val id = trainMonday()
        routines.removeExercise(routineId, "bench-press")
        workouts.finish(id)
        assertEquals(listOf("treadmill"), routines.getRoutine(routineId)!!.exercises.map { it.exercise.id })
        assertEquals(1, workouts.observeSaved().first().size)
    }

    @Test
    fun finish_afterRoutineSetsChanged_usesTheRoutinesCurrentSets() = runTest {
        val id = trainMonday()
        routines.removeSet(routineId, "bench-press", 2)
        workouts.finish(id)
        // the unticked third set had no routine position left, so it is dropped
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), routineBench())
    }

    @Test
    fun savedWorkoutsAreInDateOrder_andExerciseAddedElsewhereStartsFromHistory() = runTest {
        workouts.finish(trainMonday())
        val later = workouts.ensureWorkout(tuesday, routineId)
        workouts.setDone(later, "treadmill", 0, true)
        workouts.finish(later)
        assertEquals(listOf(monday, tuesday), workouts.observeSaved().first().map { it.date })
        val other = routines.createRoutine("Full Body")
        routines.addExercise(other, "bench-press")
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), routines.getRoutine(other)!!.exercises[0].sets)
    }
}

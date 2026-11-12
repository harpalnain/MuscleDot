package com.example.fit.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.WorkoutSet
import com.example.fit.domain.WorkoutStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var profile: ProfileRepository
    private lateinit var exercises: ExerciseRepository
    private val monday = LocalDate.of(2026, 11, 9)
    private var routineId = 0L

    private suspend fun today() = workouts.observeWorkoutOn(monday).first()!!
    private suspend fun ids() = today().exercises.map { it.exercise.id }
    private suspend fun bench() = today().exercises.first { it.exercise.id == "bench-press" }.sets
    private suspend fun routineIds() = routines.getRoutine(routineId)!!.exercises.map { it.exercise.id }

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        seedTestExercises(db)
        val starting = StartingSets(db)
        routines = RoutineRepository(db, starting)
        workouts = WorkoutRepository(db, starting)
        profile = ProfileRepository(db)
        exercises = ExerciseRepository(db)
        routineId = routines.createRoutine("Chest")
        routines.addExercise(routineId, "bench-press")
        routines.addExercise(routineId, "treadmill")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun ensureWorkout_createsFromTheRoutineOnce() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        assertEquals(id, workouts.ensureWorkout(monday, routineId))
        val w = today()
        assertEquals(WorkoutStatus.IN_PROGRESS, w.status)
        assertEquals("Chest", w.routineName)
        assertEquals(routineId, w.routineId)
        assertEquals(listOf("bench-press", "treadmill"), ids())
        assertEquals(List(3) { WorkoutSet(it, SetValues(200, 10), false) }, bench())
        assertEquals(w, workouts.observeWorkout(id).first())
        routines.addExercise(routineId, "pull-up")
        routines.adjustSet(routineId, "bench-press", 0, SetField.WEIGHT, +4)
        assertEquals(w, today()) // editing the routine does not change a workout already created from it (spec 5.5)
    }

    @Test
    fun ticksAndNumbersAreStored() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1)
        workouts.adjustSet(id, "bench-press", 0, SetField.REPS, -2)
        workouts.setDone(id, "bench-press", 0, true)
        assertEquals(WorkoutSet(0, SetValues(205, 8), true), bench()[0])
        workouts.setDone(id, "bench-press", 0, false)
        assertFalse(bench()[0].done)
    }

    @Test
    fun adjustSet_isAtomic_underRapidSteps() = runBlocking {
        val id = workouts.ensureWorkout(monday, routineId)
        (1..50).map { async(Dispatchers.Default) { workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1) } }.awaitAll()
        assertEquals(200 + 50 * 5, bench()[0].values.weightHg)
    }

    @Test
    fun setsCanBeAddedAndRemoved() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 2, SetField.WEIGHT, +3)
        workouts.addSet(id, "bench-press")
        assertEquals(WorkoutSet(3, SetValues(215, 10), false), bench()[3])
        workouts.removeSet(id, "bench-press", 0)
        assertEquals(listOf(0, 1, 2), bench().map { it.position })
        assertEquals(215, bench()[1].values.weightHg)
        workouts.removeSet(id, "treadmill", 0)
        assertEquals(listOf("bench-press"), ids())
    }

    @Test
    fun startWorkout_replacesAnUntouchedWorkout_butRefusesOnceASetIsTicked() = runTest {
        val other = routines.createRoutine("Core").also { routines.addExercise(it, "crunches") }
        workouts.ensureWorkout(monday, routineId)
        val id = workouts.startWorkout(monday, other)
        assertEquals("Core", today().routineName)
        assertEquals(listOf("crunches"), ids())
        workouts.setDone(id, "crunches", 0, true)
        assertThrows(IllegalStateException::class.java) { runBlocking { workouts.startWorkout(monday, routineId) } }
        assertEquals("Core", today().routineName)
    }

    @Test
    fun addExercise_onlyToday_orAlsoToTheRoutine() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.addExercise(id, "pull-up", alsoUpdateRoutine = false)
        workouts.addExercise(id, "pull-up", alsoUpdateRoutine = false)
        assertEquals(listOf("bench-press", "treadmill", "pull-up"), ids())
        assertEquals(listOf("bench-press", "treadmill"), routineIds())
        workouts.addExercise(id, "crunches", alsoUpdateRoutine = true)
        assertEquals(listOf("bench-press", "treadmill", "crunches"), routineIds())
    }

    @Test
    fun removeAndReplaceExercise() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.replaceExercise(id, "bench-press", "pull-up", alsoUpdateRoutine = false)
        assertEquals(listOf("pull-up", "treadmill"), ids())
        assertEquals(listOf("bench-press", "treadmill"), routineIds())
        workouts.replaceExercise(id, "pull-up", "crunches", alsoUpdateRoutine = true)
        assertEquals(listOf("crunches", "treadmill"), ids())
        assertEquals(listOf("bench-press", "treadmill", "crunches"), routineIds())
        workouts.removeExercise(id, "treadmill", alsoUpdateRoutine = true)
        assertEquals(listOf("crunches"), ids())
        assertEquals(listOf("bench-press", "crunches"), routineIds())
    }

    @Test
    fun workoutRemembersBodyWeightOfItsDay() = runTest {
        profile.setBodyWeight(monday.minusDays(3), 700)
        workouts.ensureWorkout(monday, routineId)
        assertEquals(700, today().bodyWeightHg)
        profile.setBodyWeight(monday, 705)
        assertEquals(705, today().bodyWeightHg)
        profile.setBodyWeight(monday.plusDays(1), 720)
        assertEquals(705, today().bodyWeightHg)
    }

    @Test
    fun deletingACustomExerciseWithHistoryOnlyHidesIt() = runTest {
        val custom = exercises.createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        routines.addExercise(routineId, custom)
        val id = workouts.ensureWorkout(monday, routineId)
        exercises.deleteCustom(custom)
        assertTrue(exercises.get(custom)!!.isHidden)
        assertTrue(custom in ids())
        assertFalse(custom in routineIds())
        assertTrue(exercises.observeAll().first().none { it.id == custom })
        workouts.deleteWorkout(id)
        assertNull(workouts.observeWorkoutOn(monday).first())
    }

    @Test
    fun ticks_surviveReopeningTheDatabase(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("persist-test.db")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "persist-test.db").build()
        var file = open()
        seedTestExercises(file)
        val r = RoutineRepository(file, StartingSets(file))
        val rid = r.createRoutine("Chest")
        r.addExercise(rid, "bench-press")
        val w = WorkoutRepository(file, StartingSets(file))
        val id = w.ensureWorkout(monday, rid)
        w.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1)
        w.setDone(id, "bench-press", 0, true)
        file.close()
        file = open()
        val set = WorkoutRepository(file, StartingSets(file)).observeWorkoutOn(monday).first()!!.exercises[0].sets[0]
        assertEquals(WorkoutSet(0, SetValues(205, 10), true), set)
        file.close()
        context.deleteDatabase("persist-test.db")
    }
}

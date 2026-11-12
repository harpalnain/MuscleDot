package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.Profile
import com.example.fit.domain.SetValues
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class SeederTest {
    /** The real built-in list bundled with the app. */
    private val json = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("exercises.json").bufferedReader().readText()
    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var exercises: ExerciseRepository

    @Before
    fun setUp() {
        db = inMemoryDb()
        routines = RoutineRepository(db, StartingSets(db))
        exercises = ExerciseRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun firstRun_seedsExercisesRoutinesAndSchedule() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        assertTrue(exercises.observeAll().first().size in 140..160)
        val benchPress = exercises.get("bench-press")!!
        assertEquals("exercises/bench-press", benchPress.imageBase)
        assertFalse(benchPress.isCustom)
        val all = routines.observeRoutines().first()
        assertEquals(listOf("Full Body", "Chest + Triceps", "Back + Biceps", "Leg + Shoulder", "Cardio"), all.map { it.name })
        val chest = all[1]
        assertEquals(
            listOf("bench-press", "incline-dumbbell-press", "dumbbell-flyes", "triceps-pushdown", "overhead-triceps-extension"),
            chest.exercises.map { it.exercise.id },
        )
        assertEquals(List(3) { SetValues(200, 10) }, chest.exercises[0].sets) // barbell
        assertEquals(List(3) { SetValues(50, 10) }, chest.exercises[1].sets) // dumbbell
        assertEquals(List(3) { SetValues(100, 10) }, chest.exercises[3].sets) // cable
        assertEquals(listOf(SetValues(minutes = 10)), all[4].exercises[0].sets)
        val schedule = routines.observeSchedule().first()
        assertEquals(chest.id, schedule[DayOfWeek.MONDAY])
        assertEquals(all[4].id, schedule[DayOfWeek.THURSDAY])
        assertEquals(all[0].id, schedule[DayOfWeek.FRIDAY])
        assertNull(schedule[DayOfWeek.SATURDAY])
        assertNull(schedule[DayOfWeek.SUNDAY])
        assertEquals(Profile(), ProfileRepository(db).observeProfile().first())
    }

    @Test
    fun laterRuns_neverRecreateWhatTheUserDeleted() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        routines.deleteRoutine(routines.observeRoutines().first()[0].id)
        Seeder(db) { json }.seedIfNeeded()
        assertEquals(4, routines.observeRoutines().first().size)
    }

    @Test
    fun newSeedVersion_refreshesBuiltInsAndKeepsCustomExercises() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        val custom = exercises.createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        db.profileDao().putSetting(Seeder.SEED_VERSION_KEY, "0")
        Seeder(db) { json.replace("\"Bench Press\"", "\"Bench Press X\"") }.seedIfNeeded()
        assertEquals("Bench Press X", exercises.get("bench-press")!!.name)
        assertNotNull(exercises.get(custom))
        assertEquals(5, routines.observeRoutines().first().size)
    }
}

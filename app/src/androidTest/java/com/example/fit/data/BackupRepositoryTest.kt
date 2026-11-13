package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.backup.BackupCodec
import com.example.fit.data.backup.BackupException
import com.example.fit.data.backup.BackupRepository
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.Profile
import com.example.fit.domain.Routine
import com.example.fit.domain.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private data class Snapshot(
        val profile: Profile,
        val weights: List<BodyWeightEntry>,
        val n: Int,
        val routines: List<Routine>,
        val schedule: Map<DayOfWeek, Long?>,
        val saved: List<Workout>,
        val today: Workout?,
        val exercises: List<Exercise>,
    )

    private val monday = LocalDate.of(2026, 11, 9)
    private val now = LocalDateTime.of(2026, 11, 10, 9, 0)
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    private suspend fun snapshot(db: AppDatabase): Snapshot {
        val s = StartingSets(db)
        val r = RoutineRepository(db, s)
        val w = WorkoutRepository(db, s)
        val p = ProfileRepository(db)
        return Snapshot(
            p.observeProfile().first(),
            p.observeBodyWeights().first(),
            p.observeAverageN().first(),
            r.observeRoutines().first(),
            r.observeSchedule().first(),
            w.observeSaved().first(),
            w.observeWorkoutOn(monday.plusDays(1)).first(),
            ExerciseRepository(db).observeAll().first(),
        )
    }

    /** Profile, body weights, N = 5, a custom exercise, a routine on Monday, a finished Monday workout, an in-progress Tuesday workout. */
    private suspend fun fill(db: AppDatabase) {
        val s = StartingSets(db)
        val r = RoutineRepository(db, s)
        val w = WorkoutRepository(db, s)
        val p = ProfileRepository(db)
        p.saveProfile(Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, true))
        p.setBodyWeight(monday, 723)
        p.setAverageN(5)
        val custom = ExerciseRepository(db).createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        val id = r.createRoutine("Chest")
        listOf("bench-press", "pull-up", custom).forEach { r.addExercise(id, it) }
        r.setScheduleDay(DayOfWeek.MONDAY, id)
        val first = w.ensureWorkout(monday, id)
        w.setDone(first, "bench-press", 0, true)
        w.setDone(first, "pull-up", 1, true)
        w.finish(first)
        val second = w.ensureWorkout(monday.plusDays(1), id)
        w.setDone(second, custom, 0, true)
    }

    @Before
    fun setUp() = runBlocking {
        source = inMemoryDb()
        target = inMemoryDb()
        seedTestExercises(source)
        seedTestExercises(target)
        fill(source)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun exportThenImport_restoresEverything() = runTest {
        BackupRepository(target).import(BackupRepository(source).export(now))
        assertEquals(snapshot(source), snapshot(target))
    }

    @Test
    fun import_replacesWhatWasThere() = runTest {
        RoutineRepository(target, StartingSets(target)).createRoutine("Old")
        ProfileRepository(target).setBodyWeight(monday.minusDays(30), 800)
        BackupRepository(target).import(BackupRepository(source).export(now))
        assertEquals(snapshot(source), snapshot(target))
    }

    @Test
    fun import_rejectsBadFiles_andKeepsData() = runTest {
        val good = BackupRepository(source).export(now)
        val before = snapshot(source)
        val badFiles = listOf(
            "",
            "hello",
            good.dropLast(40),
            good.replaceFirst("\"version\":1", "\"version\":2"),
            good.replaceFirst("overload-backup", "x"),
        )
        for (bad in badFiles) {
            assertThrows(BackupException::class.java) { runBlocking { BackupRepository(source).import(bad) } }
            assertEquals(before, snapshot(source))
        }
    }

    @Test
    fun import_thatFailsHalfWay_keepsEverything() = runTest {
        val good = BackupRepository(source).export(now)
        val before = snapshot(source)
        val brokenInside = listOf(
            // a routine and workouts of an exercise that neither the file nor the app has
            good.replace("\"exerciseId\":\"bench-press\"", "\"exerciseId\":\"no-such-exercise\""),
            // two workouts on the same day
            good.replace("\"date\":\"2026-11-10\"", "\"date\":\"2026-11-09\""),
            // a workout date that is not a date
            good.replace("\"date\":\"2026-11-10\"", "\"date\":\"2026-13-40\""),
        )
        for (bad in brokenInside) {
            assertTrue(bad != good)
            // Well-formed backup files: each is only found out after the old data has been deleted.
            BackupCodec.decode(bad)
            assertThrows(BackupException::class.java) { runBlocking { BackupRepository(source).import(bad) } }
            assertEquals(before, snapshot(source))
        }
    }

    @Test
    fun import_keepsHistoryOfExercisesThisAppDoesNotShip() = runTest {
        BackupRepository(target).import(BackupRepository(source).export(now).replace("pull-up", "retired-move"))
        val retired = ExerciseRepository(target).get("retired-move")!!
        assertTrue(retired.isHidden)
        assertNull(retired.imageBase)
        val saved = WorkoutRepository(target, StartingSets(target)).observeSaved().first().single()
        assertTrue(saved.exercises.any { it.exercise.id == "retired-move" })
    }
}

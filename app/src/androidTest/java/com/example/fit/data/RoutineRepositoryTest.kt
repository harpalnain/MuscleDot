package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoutineRepository

    private suspend fun chest(): Long = repo.createRoutine("Chest").also {
        repo.addExercise(it, "bench-press")
        repo.addExercise(it, "treadmill")
    }

    private suspend fun benchSets(id: Long) =
        repo.getRoutine(id)!!.exercises.first { it.exercise.id == "bench-press" }.sets

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        seedTestExercises(db)
        repo = RoutineRepository(db, StartingSets(db))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newExerciseGetsDefaultStartingSets_andIsNeverAddedTwice() = runTest {
        val id = chest()
        repo.addExercise(id, "bench-press")
        val routine = repo.getRoutine(id)!!
        assertEquals(listOf("bench-press", "treadmill"), routine.exercises.map { it.exercise.id })
        assertEquals(List(3) { SetValues(200, 10) }, routine.exercises[0].sets)
        assertEquals(listOf(SetValues(minutes = 10)), routine.exercises[1].sets)
    }

    @Test
    fun setsCanBeAdjustedAddedAndRemoved() = runTest {
        val id = chest()
        repo.adjustSet(id, "bench-press", 1, SetField.WEIGHT, +5)
        assertEquals(listOf(200, 225, 200), benchSets(id).map { it.weightHg })
        repo.addSet(id, "bench-press")
        assertEquals(4, benchSets(id).size)
        assertEquals(SetValues(200, 10), benchSets(id)[3])
        repo.removeSet(id, "bench-press", 0)
        assertEquals(listOf(225, 200, 200), benchSets(id).map { it.weightHg })
        repeat(5) { repo.removeSet(id, "bench-press", 0) }
        assertEquals(1, benchSets(id).size)
        repo.addSet(id, "treadmill")
        assertEquals(1, repo.getRoutine(id)!!.exercises[1].sets.size)
    }

    @Test
    fun adjustSet_isAtomic_underRapidSteps() = runBlocking {
        val id = chest()
        (1..50).map { async(Dispatchers.Default) { repo.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1) } }.awaitAll()
        assertEquals(200 + 50 * 5, benchSets(id)[0].weightHg)
    }

    @Test
    fun exercisesCanBeReorderedAndRemoved() = runTest {
        val id = chest()
        repo.addExercise(id, "pull-up")
        repo.moveExercise(id, 0, 2)
        assertEquals(listOf("treadmill", "pull-up", "bench-press"), repo.getRoutine(id)!!.exercises.map { it.exercise.id })
        repo.removeExercise(id, "pull-up")
        assertEquals(listOf("treadmill", "bench-press"), repo.getRoutine(id)!!.exercises.map { it.exercise.id })
    }

    @Test
    fun duplicateIsAnIndependentCopy() = runTest {
        val id = chest()
        val copyId = repo.duplicateRoutine(id)
        assertEquals("Chest copy", repo.getRoutine(copyId)!!.name)
        assertEquals(repo.getRoutine(id)!!.exercises, repo.getRoutine(copyId)!!.exercises)
        repo.adjustSet(copyId, "bench-press", 0, SetField.WEIGHT, +1)
        assertEquals(200, benchSets(id)[0].weightHg)
        assertEquals(listOf("Chest", "Chest copy"), repo.observeRoutines().first().map { it.name })
    }

    @Test
    fun renameChangesTheName() = runTest {
        val id = chest()
        repo.renameRoutine(id, "Push")
        assertEquals("Push", repo.observeRoutine(id).first()!!.name)
    }

    @Test
    fun scheduleCoversSevenDays_andDeletingARoutineFreesItsDays() = runTest {
        val id = chest()
        assertEquals(DayOfWeek.entries.associateWith { null }, repo.observeSchedule().first())
        repo.setScheduleDay(DayOfWeek.MONDAY, id)
        repo.setScheduleDay(DayOfWeek.THURSDAY, id)
        assertEquals(id, repo.observeSchedule().first()[DayOfWeek.MONDAY])
        repo.setScheduleDay(DayOfWeek.THURSDAY, null)
        assertNull(repo.observeSchedule().first()[DayOfWeek.THURSDAY])
        repo.deleteRoutine(id)
        assertNull(repo.getRoutine(id))
        assertEquals(DayOfWeek.entries.associateWith { null }, repo.observeSchedule().first())
    }
}

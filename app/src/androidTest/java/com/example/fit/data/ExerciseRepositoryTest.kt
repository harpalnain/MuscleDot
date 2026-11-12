package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
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

@RunWith(AndroidJUnit4::class)
class ExerciseRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ExerciseRepository

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDb()
        seedTestExercises(db)
        repo = ExerciseRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun listsExercisesSortedByName() = runTest {
        assertEquals(listOf("Bench Press", "Crunches", "Pull-up", "Treadmill"), repo.observeAll().first().map { it.name })
        assertEquals(TEST_EXERCISES[0], repo.get("bench-press"))
    }

    @Test
    fun customExercise_createRenameDelete() = runTest {
        val id = repo.createCustom("Cable Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        assertTrue(id.startsWith("custom-"))
        val created = repo.get(id)!!
        assertTrue(created.isCustom)
        assertNull(created.imageBase)
        assertEquals(MuscleGroup.SHOULDERS, created.group)
        repo.renameCustom(id, "Y Raise")
        assertEquals("Y Raise", repo.get(id)!!.name)
        repo.deleteCustom(id)
        assertNull(repo.get(id))
    }

    @Test
    fun builtInExercisesCannotBeRenamedOrDeleted() = runTest {
        repo.renameCustom("bench-press", "X")
        repo.deleteCustom("bench-press")
        assertEquals("Bench Press", repo.get("bench-press")!!.name)
    }
}

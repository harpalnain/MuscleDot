package com.example.fit.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.data.db.AppDatabase
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepository

    private fun d(day: Int) = LocalDate.of(2026, 11, day)

    @Before
    fun setUp() {
        db = inMemoryDb()
        repo = ProfileRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun profileStartsEmptyThenSaves() = runTest {
        assertEquals(Profile(), repo.observeProfile().first())
        val sam = Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, welcomeDone = true)
        repo.saveProfile(sam)
        assertEquals(sam, repo.observeProfile().first())
    }

    @Test
    fun bodyWeightKeepsOneEntryPerDay() = runTest {
        assertNull(repo.currentBodyWeightHg())
        repo.setBodyWeight(d(9), 723)
        repo.setBodyWeight(d(9), 725)
        repo.setBodyWeight(d(10), 720)
        assertEquals(listOf(BodyWeightEntry(d(9), 725), BodyWeightEntry(d(10), 720)), repo.observeBodyWeights().first())
        assertEquals(720, repo.currentBodyWeightHg())
    }

    @Test
    fun averageNDefaultsToTenAndStaysInRange() = runTest {
        assertEquals(10, repo.observeAverageN().first())
        repo.setAverageN(5)
        assertEquals(5, repo.observeAverageN().first())
        repo.setAverageN(1)
        assertEquals(2, repo.observeAverageN().first())
        repo.setAverageN(99)
        assertEquals(20, repo.observeAverageN().first())
    }
}

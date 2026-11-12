package com.example.fit.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.AppContainer
import com.example.fit.data.seedTestExercises
import com.example.fit.domain.SetField
import com.example.fit.domain.TodayState
import com.example.fit.ui.today.BodyWeightPrompt
import com.example.fit.ui.today.TodayViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDateTime

/** The Today ViewModel against real repositories on an in-memory database, without any screen. */
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0)) // a Monday
    private lateinit var c: AppContainer
    private lateinit var vm: TodayViewModel
    private val store = ViewModelStore()

    private suspend fun active(match: (TodayState.Active) -> Boolean = { true }) =
        withTimeout(5_000) { vm.state.filterIsInstance<TodayState.Active>().first(match) }

    private fun TodayState.Active.sets(id: String) = exercises.first { it.exercise.id == id }.sets

    private suspend fun stored() = c.workouts.observeWorkoutOn(c.today.date.value).first()

    @Before
    fun setUp() = runBlocking {
        c = AppContainer(ApplicationProvider.getApplicationContext(), inMemory = true, clock = clock)
        seedTestExercises(c.db)
        val id = c.routines.createRoutine("Mixed")
        listOf("bench-press", "pull-up").forEach { c.routines.addExercise(id, it) }
        c.routines.setScheduleDay(DayOfWeek.MONDAY, id)
        c.onForeground()
        val factory = viewModelFactory { initializer { TodayViewModel(c.workouts, c.routines, c.profile, c.today) } }
        vm = ViewModelProvider(store, factory)[TodayViewModel::class.java]
    }

    @After
    fun tearDown() {
        // Stop the ViewModel before its database goes away, as happens in the app.
        store.clear()
        c.db.close()
    }

    @Test
    fun firstTouchTurnsThePreviewIntoAStoredWorkout(): Unit = runBlocking {
        assertTrue(active().isPreview)
        assertNull(stored())
        vm.step("bench-press", 0, SetField.WEIGHT, +1)
        // Storing the workout and applying the step are two writes; wait for the second.
        active { !it.isPreview && it.sets("bench-press")[0].values.weightHg == 205 }
        assertEquals(205, stored()!!.exercises[0].sets[0].values.weightHg)
    }

    @Test
    fun rapidStepsAllLand(): Unit = runBlocking {
        repeat(30) { vm.step("bench-press", 0, SetField.WEIGHT, +1) }
        active { it.sets("bench-press")[0].values.weightHg == 350 } // times out if a step was lost
    }

    @Test
    fun tickingTheLastSetOpensTheNextExercise(): Unit = runBlocking {
        assertEquals("bench-press", active().openExerciseId)
        for (i in 0..2) vm.toggleDone("bench-press", i)
        // The tick and the move to the next exercise are two updates; wait for the second.
        // This times out if the next exercise never opens.
        active { it.tickedCount == 3 && it.openExerciseId == "pull-up" }
    }

    @Test
    fun bodyWeightSetWaitsForABodyWeight(): Unit = runBlocking {
        vm.toggleDone("pull-up", 0)
        assertEquals(BodyWeightPrompt("pull-up", 0), withTimeout(5_000) { vm.bodyWeightPrompt.filterNotNull().first() })
        assertEquals(0, active().tickedCount)
        vm.confirmBodyWeight(700)
        val state = active { it.tickedCount == 1 }
        assertTrue(state.sets("pull-up")[0].done)
        assertEquals(7000L, state.totalLoadHg)
        assertEquals(700, c.profile.currentBodyWeightHg())
        assertNull(vm.bodyWeightPrompt.value)
    }

    @Test
    fun finishThenContinue(): Unit = runBlocking {
        vm.toggleDone("bench-press", 0)
        active { it.tickedCount == 1 }
        vm.finish()
        assertEquals(2000L, withTimeout(5_000) { vm.state.filterIsInstance<TodayState.Finished>().first() }.summary.loadHg)
        vm.continueWorkout()
        assertEquals(1, active { !it.isPreview }.tickedCount)
    }

    @Test
    fun routineCanBeSwitchedBeforeTheFirstTick(): Unit = runBlocking {
        val other = c.routines.createRoutine("Core").also { c.routines.addExercise(it, "crunches") }
        vm.startRoutine(other)
        assertEquals(listOf("crunches"), active { it.routineName == "Core" }.exercises.map { it.exercise.id })
    }
}

package com.example.fit.ui

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.AppContainer
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.SetField
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Walks through the real app on an in-memory database and saves a picture of each
 * screen, for a person to look at (see [saveScreenshot]). It asserts nothing about
 * looks; it fails only if a screen cannot be reached.
 */
@RunWith(AndroidJUnit4::class)
class AppGalleryTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0)) // a Monday: Chest + Triceps

    /**
     * Five weeks of finished workouts before [today], following the starter schedule,
     * each 0.5 kg heavier than the one before, so the charts have something to show.
     */
    private suspend fun seedHistory(c: AppContainer, today: LocalDate) {
        listOf(5L to 702, 3L to 698, 1L to 695).forEach { (weeksAgo, hg) -> c.profile.setBodyWeight(today.minusWeeks(weeksAgo), hg) }
        val schedule = c.routines.observeSchedule().first()
        var day = today.minusWeeks(5)
        while (day < today) {
            val routineId = schedule[day.dayOfWeek]
            // One missed day, as in real life.
            val missed = day == today.minusWeeks(2).with(DayOfWeek.WEDNESDAY)
            if (routineId != null && !missed) {
                val id = c.workouts.ensureWorkout(day, routineId)
                for (exercise in c.workouts.observeWorkout(id).first()!!.exercises) {
                    val lifted = exercise.exercise.kind == ExerciseKind.WEIGHTS
                    for (set in exercise.sets) {
                        if (lifted) c.workouts.adjustSet(id, exercise.exercise.id, set.position, SetField.WEIGHT, +1)
                        c.workouts.setDone(id, exercise.exercise.id, set.position, true)
                    }
                }
                c.workouts.finish(id)
            }
            day = day.plusDays(1)
        }
    }

    @Test
    fun screens() {
        launchApp(clock) { seedHistory(it, clock.now.toLocalDate()) }.use {
            compose.awaitText("Welcome to Overload")
            compose.saveScreenshot("01-welcome")
            compose.onNodeWithText("Skip").performClick()

            compose.awaitText("Chest + Triceps")
            compose.saveScreenshot("02-today-preview")
            repeat(5) { compose.onNodeWithContentDescription("Increase weight, set 1").performClick() }
            compose.onNodeWithContentDescription("Set 1 done").performClick()
            compose.onNodeWithContentDescription("Set 2 done").performClick()
            compose.awaitText("New record")
            compose.saveScreenshot("03-today-in-progress")

            compose.onNodeWithTag("tab-routines").performClick()
            compose.awaitText("My routines")
            compose.saveScreenshot("04-routines")
            compose.onAllNodesWithText("Chest + Triceps")[1].performClick()
            compose.awaitTag("routine-name-field")
            compose.onNodeWithText("Bench Press").performClick()
            compose.saveScreenshot("05-routine-editor")
            compose.onNodeWithText("Add exercise").performScrollTo().performClick()
            compose.awaitTag("exercise-search-field")
            compose.awaitText("Ab Crunch Machine")
            compose.saveScreenshot("06-exercise-picker")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Done").performClick()

            compose.onNodeWithTag("tab-profile").performClick()
            compose.awaitText("Export backup file")
            compose.saveScreenshot("07-profile")

            compose.onNodeWithTag("tab-progress").performClick()
            compose.awaitText("Bench Press")
            compose.saveScreenshot("08-progress-exercises")
            compose.onNodeWithText("Bench Press").performClick()
            compose.awaitText("Max weight")
            compose.saveScreenshot("09-exercise-detail")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.awaitTag("progress-search-field")
            compose.onAllNodesWithText("Routines")[0].performClick() // the switch; the tab comes after it
            compose.awaitText("Last session")
            compose.saveScreenshot("10-progress-routines")
            compose.onNodeWithText("Weekly").performClick()
            compose.awaitText("Best week")
            compose.saveScreenshot("11-progress-weekly")
            compose.onNodeWithText("Body").performClick()
            compose.awaitText("Body weight (kg)")
            compose.saveScreenshot("12-progress-body")

            compose.onNodeWithTag("tab-history").performClick()
            compose.awaitText("November 2026")
            compose.saveScreenshot("13-history")
            compose.onAllNodesWithText("Full Body")[0].performClick()
            compose.awaitText("Delete workout")
            compose.saveScreenshot("14-saved-workout")
            compose.onNodeWithContentDescription("Back").performClick()

            compose.onNodeWithTag("tab-today").performClick()
            compose.awaitText("Finish workout")
            compose.onNodeWithText("Finish workout").performClick()
            compose.awaitText("Go back")
            compose.onNodeWithText("Finish").performClick()
            compose.awaitText("Workout finished")
            compose.saveScreenshot("15-today-finished")
        }
    }
}

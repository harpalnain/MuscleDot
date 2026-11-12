package com.example.fit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.Workout
import com.example.fit.domain.WorkoutExercise
import com.example.fit.domain.WorkoutSet
import com.example.fit.domain.WorkoutStatus
import com.example.fit.ui.history.HistoryContent
import com.example.fit.ui.history.HistoryMonth
import com.example.fit.ui.history.HistoryRow
import com.example.fit.ui.history.WorkoutDetailActions
import com.example.fit.ui.history.WorkoutDetailContent
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : WorkoutDetailActions {
        val calls = mutableListOf<String>()
        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) {
            calls += "step $exerciseId $position $field $steps"
        }

        override fun removeSet(exerciseId: String, position: Int) {
            calls += "removeSet $exerciseId $position"
        }

        override fun addSet(exerciseId: String) {
            calls += "addSet $exerciseId"
        }

        override fun deleteWorkout() {
            calls += "delete"
        }

        override fun back() {
            calls += "back"
        }
    }

    private val actions = Recorder()
    private val wednesday = LocalDate.of(2026, 11, 11)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)

    @Test
    fun listShowsRowsAndOpensOne() {
        val opened = mutableListOf<Long>()
        val months = listOf(
            HistoryMonth(
                YearMonth.of(2026, 11),
                listOf(
                    HistoryRow(5, wednesday, "Leg + Shoulder", 6, 18, "6,240 kg", records = 0),
                    HistoryRow(4, wednesday.minusDays(1), "Back + Biceps", 5, 15, "5,480 kg", records = 1),
                ),
            ),
        )
        rule.setContent { OverloadTheme { HistoryContent(months, onOpen = { opened += it }) } }
        rule.onNodeWithText("November 2026").assertIsDisplayed()
        rule.onNodeWithText("6 exercises · 18 sets").assertIsDisplayed()
        rule.onNodeWithText("6,240 kg").assertIsDisplayed()
        rule.onNodeWithText("1 record").assertIsDisplayed()
        rule.onNodeWithText("Wed").assertIsDisplayed()
        rule.onNodeWithText("Back + Biceps").performClick()
        assertEquals(listOf(4L), opened)
    }

    @Test
    fun rowCountsAreSingularForOne() {
        val months = listOf(
            HistoryMonth(YearMonth.of(2026, 11), listOf(HistoryRow(5, wednesday, "Chest", 1, 1, "400 kg", records = 2))),
        )
        rule.setContent { OverloadTheme { HistoryContent(months, onOpen = {}) } }
        rule.onNodeWithText("1 exercise · 1 set").assertIsDisplayed()
        rule.onNodeWithText("2 records").assertIsDisplayed()
    }

    @Test
    fun emptyHistory_showsAMessage() {
        rule.setContent { OverloadTheme { HistoryContent(emptyList(), onOpen = {}) } }
        rule.onNodeWithText("No workouts yet").assertIsDisplayed()
    }

    @Test
    fun detailEditsSetsByTheirOwnPosition_andDeleteAsksFirst() {
        val workout = Workout(
            5, wednesday, 1, "Chest", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(bench, listOf(WorkoutSet(0, SetValues(400, 10), true), WorkoutSet(2, SetValues(450, 6), true)))),
        )
        rule.setContent { OverloadTheme { WorkoutDetailContent(workout, actions) } }
        rule.onNodeWithText("Wednesday, 11 November").assertIsDisplayed()
        rule.onNodeWithText("670 kg").assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase weight, set 2").performClick() // second row shown, stored position 2
        rule.onNodeWithContentDescription("Remove set 1").performClick()
        rule.onNodeWithText("Add set").performClick()
        rule.onNodeWithText("Delete workout").performScrollTo().performClick()
        rule.onNodeWithText("Delete this workout? This cannot be undone.").assertIsDisplayed()
        rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("step bench-press 2 WEIGHT 1", "removeSet bench-press 0", "addSet bench-press", "delete"), actions.calls)
    }

    @Test
    fun removingTheOnlySetAsksBeforeDeletingTheWorkout() {
        val workout = Workout(
            7, wednesday, 1, "Chest", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(bench, listOf(WorkoutSet(1, SetValues(400, 10), true)))),
        )
        rule.setContent { OverloadTheme { WorkoutDetailContent(workout, actions) } }
        rule.onNodeWithContentDescription("Remove set 1").performClick()
        rule.onNodeWithText("Delete this workout? This cannot be undone.").assertIsDisplayed()
        assertEquals(emptyList<String>(), actions.calls)
        rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("delete"), actions.calls)
    }

    @Test
    fun cardioHasNoAddSet_andBackIsReported() {
        val workout = Workout(
            6, wednesday, 1, "Cardio", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(run, listOf(WorkoutSet(0, SetValues(minutes = 35), true)))),
        )
        rule.setContent { OverloadTheme { WorkoutDetailContent(workout, actions) } }
        rule.onNodeWithText("Treadmill").assertIsDisplayed()
        rule.onAllNodesWithText("Add set").assertCountEquals(0)
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), actions.calls)
    }
}

package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.RoutineExercise
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.ui.routines.RoutineEditorActions
import com.example.fit.ui.routines.RoutineEditorContent
import com.example.fit.ui.routines.RoutineEditorUiState
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoutineEditorContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : RoutineEditorActions {
        val calls = mutableListOf<String>()
        override fun rename(name: String) {
            calls += "rename $name"
        }

        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) {
            calls += "step $exerciseId $position $field $steps"
        }

        override fun addSet(exerciseId: String) {
            calls += "addSet $exerciseId"
        }

        override fun removeSet(exerciseId: String, position: Int) {
            calls += "removeSet $exerciseId $position"
        }

        override fun removeExercise(exerciseId: String) {
            calls += "removeExercise $exerciseId"
        }

        override fun move(fromIndex: Int, toIndex: Int) {
            calls += "move $fromIndex $toIndex"
        }

        override fun addExercise() {
            calls += "addExercise"
        }

        override fun done() {
            calls += "done"
        }
    }

    private val actions = Recorder()
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val state = RoutineEditorUiState(
        1,
        "Chest",
        listOf(RoutineExercise(bench, List(3) { SetValues(400, 10) }), RoutineExercise(run, listOf(SetValues(minutes = 10)))),
    )

    @Test
    fun showsNameExercisesAndSummaries() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithTag("routine-name-field").assertTextContains("Chest")
        rule.onNodeWithText("3 sets · 40 kg × 10").assertIsDisplayed()
        rule.onNodeWithText("10 min").assertIsDisplayed()
        rule.onNodeWithText("cardio").assertIsDisplayed()
    }

    @Test
    fun openingAnExerciseEditsItsStartingSets() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithText("Bench Press").performClick()
        rule.onNodeWithContentDescription("Increase weight, set 2").performClick()
        rule.onNodeWithContentDescription("Decrease reps, set 1").performClick()
        rule.onNodeWithContentDescription("Remove set 3").performClick()
        rule.onNodeWithText("Add set").performClick()
        assertEquals(
            listOf("step bench-press 1 WEIGHT 1", "step bench-press 0 REPS -1", "removeSet bench-press 2", "addSet bench-press"),
            actions.calls,
        )
    }

    @Test
    fun exercisesCanBeRemovedAndAdded_andDoneLeaves() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithContentDescription("Remove Treadmill").performClick()
        rule.onNodeWithText("Add exercise").performScrollTo().performClick()
        rule.onNodeWithText("Done").performClick()
        assertEquals(listOf("removeExercise treadmill", "addExercise", "done"), actions.calls)
    }
}

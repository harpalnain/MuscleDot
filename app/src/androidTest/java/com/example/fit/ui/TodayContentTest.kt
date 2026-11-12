package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.FinishSummary
import com.example.fit.domain.MuscleGroup
import com.example.fit.domain.RecordBroken
import com.example.fit.domain.RecordType
import com.example.fit.domain.SetField
import com.example.fit.domain.SetValues
import com.example.fit.domain.TodayExerciseUi
import com.example.fit.domain.TodaySetUi
import com.example.fit.domain.TodayState
import com.example.fit.domain.WeekCount
import com.example.fit.ui.theme.OverloadTheme
import com.example.fit.ui.today.FinishWarningDialog
import com.example.fit.ui.today.ScopeChoiceDialog
import com.example.fit.ui.today.TodayActions
import com.example.fit.ui.today.TodayContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TodayContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : TodayActions {
        val calls = mutableListOf<String>()
        override fun open(exerciseId: String) {
            calls += "open $exerciseId"
        }

        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) {
            calls += "step $exerciseId $position $field $steps"
        }

        override fun toggleDone(exerciseId: String, position: Int) {
            calls += "toggle $exerciseId $position"
        }

        override fun addSet(exerciseId: String) {
            calls += "addSet $exerciseId"
        }

        override fun removeLastSet(exerciseId: String) {
            calls += "removeLastSet $exerciseId"
        }

        override fun chooseRoutine() {
            calls += "chooseRoutine"
        }

        override fun addExercise() {
            calls += "addExercise"
        }

        override fun swapExercise(exerciseId: String) {
            calls += "swap $exerciseId"
        }

        override fun removeExercise(exerciseId: String) {
            calls += "remove $exerciseId"
        }

        override fun finish() {
            calls += "finish"
        }

        override fun continueWorkout() {
            calls += "continue"
        }

        override fun showExercise(exerciseId: String) {
            calls += "show $exerciseId"
        }
    }

    private val actions = Recorder()
    private val monday = LocalDate.of(2026, 11, 9)
    private val bench = Exercise(
        "bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS,
        imageBase = "exercises/bench-press",
    )
    private val run = Exercise(
        "treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO,
        imageBase = "exercises/treadmill",
    )
    private val active = TodayState.Active(
        monday, WeekCount(3, 5), "Chest + Triceps",
        isPreview = false,
        exercises = listOf(
            TodayExerciseUi(
                bench,
                listOf(
                    TodaySetUi(0, SetValues(400, 10), true, false),
                    TodaySetUi(1, SetValues(425, 8), true, true),
                    TodaySetUi(2, SetValues(450, 6), false, false),
                ),
                doneCount = 2, loadHg = 7400, totalReps = 18, minutes = 0, bestWeightHg = 450,
            ),
            TodayExerciseUi(run, listOf(TodaySetUi(0, SetValues(minutes = 10), false, false)), 0, 0, 0, 0, null),
        ),
        openExerciseId = "bench-press", totalLoadHg = 7400, cardioMinutes = 0, tickedCount = 2, untickedCount = 2,
    )

    private fun show(state: TodayState) = rule.setContent { OverloadTheme { TodayContent(state, actions) } }

    @Test
    fun showsHeaderOpenExerciseAndTotals() {
        show(active)
        rule.onNodeWithText("Monday, 9 November").assertIsDisplayed()
        rule.onNodeWithText("Chest + Triceps").assertIsDisplayed()
        rule.onNodeWithText("This week: 3 of 5 done").assertIsDisplayed()
        rule.onNodeWithText("Best 45 kg").assertIsDisplayed()
        rule.onNodeWithText("42.5").assertIsDisplayed()
        rule.onNodeWithText("New record").assertIsDisplayed()
        rule.onNodeWithText("Load 740 kg").assertIsDisplayed()
        rule.onNodeWithText("740 kg").assertIsDisplayed()
        rule.onNodeWithText("0 of 1 sets").assertIsDisplayed()
        rule.onNodeWithText("Change routine").assertIsNotEnabled()
    }

    @Test
    fun setControlsReportWhatWasTouched() {
        show(active)
        rule.onNodeWithContentDescription("Increase weight, set 3").performClick()
        rule.onNodeWithContentDescription("Set 3 done").performClick()
        rule.onNodeWithText("Add set").performClick()
        rule.onNodeWithText("Treadmill").performClick()
        rule.onNodeWithTag("exercise-image").performClick()
        assertEquals(
            listOf("step bench-press 2 WEIGHT 1", "toggle bench-press 2", "addSet bench-press", "open treadmill", "show bench-press"),
            actions.calls,
        )
    }

    @Test
    fun exerciseMenuAndFinish() {
        show(active)
        rule.onNodeWithContentDescription("More options for Bench Press").performClick()
        rule.onNodeWithText("Swap exercise").performClick()
        rule.onNodeWithContentDescription("More options for Bench Press").performClick()
        rule.onNodeWithText("Remove last set").performClick()
        rule.onNodeWithText("Add exercise").performScrollTo().performClick()
        rule.onNodeWithText("Finish workout").performClick()
        assertEquals(listOf("swap bench-press", "removeLastSet bench-press", "addExercise", "finish"), actions.calls)
    }

    @Test
    fun finishIsOffUntilSomethingIsTicked() {
        show(active.copy(tickedCount = 0, untickedCount = 4, isPreview = true))
        rule.onNodeWithText("Finish workout").assertIsNotEnabled()
        rule.onNodeWithText("Change routine").assertIsEnabled()
    }

    @Test
    fun restDayOffersToStartARoutine() {
        show(TodayState.RestDay(monday, WeekCount(0, 5), emptyList()))
        rule.onNodeWithText("Rest day").assertIsDisplayed()
        rule.onNodeWithText("Start a routine").performClick()
        assertEquals(listOf("chooseRoutine"), actions.calls)
    }

    @Test
    fun finishedShowsSummaryAndContinue() {
        show(
            TodayState.Finished(
                monday, WeekCount(4, 5), "Chest + Triceps",
                FinishSummary(10100, 3, 0, listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT))),
                recordLines = listOf("Bench Press" to RecordType.MAX_WEIGHT),
            ),
        )
        rule.onNodeWithText("Workout finished").assertIsDisplayed()
        rule.onNodeWithText("1,010 kg").assertIsDisplayed()
        rule.onNodeWithText("New record: Bench Press, max weight").assertIsDisplayed()
        rule.onNodeWithText("Continue workout").performClick()
        assertEquals(listOf("continue"), actions.calls)
    }

    @Test
    fun finishWarningCountsTheUntickedSets() {
        var finished = false
        rule.setContent { OverloadTheme { FinishWarningDialog(4, onFinish = { finished = true }, onGoBack = {}) } }
        rule.onNodeWithText("4 sets are not ticked and will not be saved. Finish anyway?").assertIsDisplayed()
        rule.onNodeWithText("Finish").performClick()
        assertTrue(finished)
    }

    @Test
    fun finishWarningForOneSet() {
        rule.setContent { OverloadTheme { FinishWarningDialog(1, onFinish = {}, onGoBack = {}) } }
        rule.onNodeWithText("1 set is not ticked and will not be saved. Finish anyway?").assertIsDisplayed()
        rule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun scopeChoiceOffersTodayOrRoutine() {
        var scope = ""
        rule.setContent {
            OverloadTheme { ScopeChoiceDialog(onOnlyToday = { scope = "today" }, onAlsoRoutine = { scope = "routine" }, onDismiss = {}) }
        }
        rule.onNodeWithText("Change only today's workout, or the routine as well?").assertIsDisplayed()
        rule.onNodeWithText("Only today").assertIsDisplayed()
        rule.onNodeWithText("Also update the routine").performClick()
        assertEquals("routine", scope)
    }
}

package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import com.example.fit.ui.picker.ExercisePickerContent
import com.example.fit.ui.picker.PickerActions
import com.example.fit.ui.picker.PickerUiState
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExercisePickerContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : PickerActions {
        val calls = mutableListOf<String>()
        override fun setQuery(query: String) {
            calls += "query $query"
        }

        override fun setGroup(group: MuscleGroup?) {
            calls += "group $group"
        }

        override fun pick(exerciseId: String) {
            calls += "pick $exerciseId"
        }

        override fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind) {
            calls += "create $name $group $kind"
        }

        override fun renameCustom(id: String, name: String) {
            calls += "rename $id $name"
        }

        override fun deleteCustom(id: String) {
            calls += "delete $id"
        }

        override fun back() {
            calls += "back"
        }
    }

    private val actions = Recorder()
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val pushUp = Exercise("push-up", "Push-up", MuscleGroup.CHEST, "Chest", "body only", ExerciseKind.BODY_WEIGHT)

    private fun show(state: PickerUiState) = rule.setContent { OverloadTheme { ExercisePickerContent(state, actions) } }

    @Test
    fun listsResultsAndPicksOne() {
        show(PickerUiState(results = listOf(bench, pushUp), alreadyAdded = setOf("push-up")))
        rule.onNodeWithText("Chest · barbell").assertIsDisplayed()
        rule.onNodeWithText("body weight").assertIsDisplayed()
        rule.onNodeWithText("Added").assertIsDisplayed()
        rule.onNodeWithText("Push-up").performClick()
        rule.onNodeWithText("Bench Press").performClick()
        assertEquals(listOf("pick bench-press"), actions.calls)
    }

    @Test
    fun searchAndChipsArePassedOn() {
        show(PickerUiState(results = listOf(bench)))
        rule.onNodeWithTag("exercise-search-field").performTextInput("pre")
        rule.onNodeWithText("Legs").performClick()
        rule.onNodeWithText("All").performClick()
        assertEquals(listOf("query pre", "group LEGS", "group null"), actions.calls)
    }

    @Test
    fun aMissingExerciseCanBeCreated() {
        show(PickerUiState(query = "Y Raise", results = emptyList()))
        rule.onNodeWithText("Create \"Y Raise\" as my own exercise").performClick()
        rule.onNode(hasText("Shoulders") and hasAnyAncestor(isDialog())).performClick()
        rule.onNode(hasText("Reps only") and hasAnyAncestor(isDialog())).performClick()
        rule.onNode(hasText("Create") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("create Y Raise SHOULDERS REPS_ONLY"), actions.calls)
    }
}

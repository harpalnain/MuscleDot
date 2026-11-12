package com.example.fit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.fit.ui.routines.RoutineRow
import com.example.fit.ui.routines.RoutinesActions
import com.example.fit.ui.routines.RoutinesContent
import com.example.fit.ui.routines.RoutinesUiState
import com.example.fit.ui.routines.WeekDayRow
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

class RoutinesContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : RoutinesActions {
        val calls = mutableListOf<String>()
        override fun setDay(day: DayOfWeek, routineId: Long?) {
            calls += "setDay $day $routineId"
        }

        override fun create(name: String) {
            calls += "create $name"
        }

        override fun edit(id: Long) {
            calls += "edit $id"
        }

        override fun duplicate(id: Long) {
            calls += "duplicate $id"
        }

        override fun delete(id: Long) {
            calls += "delete $id"
        }
    }

    private val actions = Recorder()
    private val state = RoutinesUiState(
        week = DayOfWeek.entries.map {
            val monday = it == DayOfWeek.MONDAY
            WeekDayRow(it, if (monday) 1 else null, if (monday) "Chest" else null, it == DayOfWeek.THURSDAY)
        },
        routines = listOf(RoutineRow(1, "Chest", 5), RoutineRow(2, "Legs", 1)),
    )

    private fun show() = rule.setContent { OverloadTheme { RoutinesContent(state, actions) } }

    @Test
    fun showsTheWeekAndTheRoutines() {
        show()
        rule.onNodeWithText("Mon").assertIsDisplayed()
        rule.onAllNodesWithText("Rest day").assertCountEquals(6)
        rule.onNodeWithText("5 exercises").assertIsDisplayed()
        rule.onNodeWithText("1 exercise").assertIsDisplayed()
    }

    @Test
    fun aDayCanBeSetToARoutineOrRest() {
        show()
        rule.onNodeWithText("Tue").performClick()
        rule.onNode(hasText("Legs") and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithText("Mon").performClick()
        rule.onNode(hasText("Rest day") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("setDay TUESDAY 2", "setDay MONDAY null"), actions.calls)
    }

    @Test
    fun menuOffersEditDuplicateDelete_andDeleteAsksFirst() {
        show()
        rule.onNodeWithContentDescription("More options for Chest").performClick()
        rule.onNodeWithText("Duplicate").performClick()
        rule.onNodeWithContentDescription("More options for Chest").performClick()
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete Chest? Past workouts stay in your history.").assertIsDisplayed()
        assertEquals(listOf("duplicate 1"), actions.calls)
        rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("duplicate 1", "delete 1"), actions.calls)
    }

    @Test
    fun newRoutineNeedsAName() {
        show()
        rule.onNodeWithText("New routine").performScrollTo().performClick()
        rule.onNodeWithText("Create").assertIsNotEnabled()
        rule.onNodeWithTag("routine-name-field").performTextInput("Arms")
        rule.onNodeWithText("Create").performClick()
        assertEquals(listOf("create Arms"), actions.calls)
    }
}

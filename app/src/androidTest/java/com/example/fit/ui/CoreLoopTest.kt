package com.example.fit.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * The whole loop on the real app with an in-memory database: train, finish, see it in
 * History and Progress, and find next week's workout starting from what was done.
 */
@RunWith(AndroidJUnit4::class)
class CoreLoopTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0)) // a Monday: the starter schedule says Chest + Triceps

    @Test
    fun trainFinishSeeHistory_thenNextWeekStartsFromWhatWasDone() {
        launchApp(clock).use { scenario ->
            compose.awaitText("Skip")
            compose.onNodeWithText("Skip").performClick()
            compose.awaitText("Chest + Triceps")
            compose.onNodeWithText("Chest + Triceps").assertIsDisplayed()
            compose.onNodeWithText("Monday, 9 November").assertIsDisplayed()

            compose.onNodeWithContentDescription("Increase weight, set 1").performClick() // 20.0 -> 20.5 kg
            compose.awaitText("20.5")
            compose.onNodeWithContentDescription("Set 1 done").performClick()
            compose.onNodeWithContentDescription("Set 2 done").performClick()
            compose.awaitText("Load 405 kg")
            compose.onNodeWithText("Finish workout").performClick()
            compose.awaitText("13 sets are not ticked and will not be saved. Finish anyway?")
            compose.onNodeWithText("Finish").performClick()
            compose.awaitText("Workout finished")
            compose.onNodeWithText("405 kg").assertIsDisplayed()

            compose.onNodeWithTag("tab-history").performClick()
            compose.awaitText("Chest + Triceps")
            compose.onNodeWithText("405 kg").assertIsDisplayed()
            compose.onNodeWithText("1 exercise · 2 sets").assertIsDisplayed()
            compose.onNodeWithTag("tab-progress").performClick()
            compose.awaitText("Bench Press")
            compose.onNodeWithText("Max 20.5 kg").assertIsDisplayed()

            clock.now = LocalDateTime.of(2026, 11, 16, 10, 0) // the next Monday
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("tab-today").performClick()
            compose.awaitText("Monday, 16 November")
            compose.onNodeWithText("This week: 0 of 5 done").assertIsDisplayed()
            compose.onNodeWithText("20.5").assertIsDisplayed() // set 1 carried forward
            compose.onAllNodesWithText("20.0").assertCountEquals(2) // sets 2 and 3
        }
    }
}

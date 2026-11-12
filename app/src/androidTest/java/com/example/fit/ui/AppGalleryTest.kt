package com.example.fit.ui

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    @Test
    fun screens() {
        launchApp(clock).use {
            compose.awaitText("Welcome to Overload")
            compose.saveScreenshot("01-welcome")
            compose.onNodeWithText("Skip").performClick()

            compose.awaitText("Chest + Triceps")
            compose.saveScreenshot("02-today-preview")
            repeat(5) { compose.onNodeWithContentDescription("Increase weight, set 1").performClick() }
            compose.onNodeWithContentDescription("Set 1 done").performClick()
            compose.onNodeWithContentDescription("Set 2 done").performClick()
            compose.awaitText("Load 425 kg")
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
        }
    }
}

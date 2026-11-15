package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.fit.domain.Profile
import com.example.fit.ui.profile.ProfileActions
import com.example.fit.ui.profile.ProfileContent
import com.example.fit.ui.profile.ProfileUiState
import com.example.fit.ui.profile.WelcomeContent
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ProfileContentTest {
    @get:Rule
    val rule = createComposeRule()

    private class Recorder : ProfileActions {
        val texts = mutableListOf<String>()
        val nudges = mutableListOf<Int>()
        var exports = 0
        var imports = 0
        var typedName = ""

        override fun setName(name: String) {
            typedName = name
        }

        override fun setDateOfBirth(date: LocalDate?) {}
        override fun setHeight(feet: Int?, inches: Int?) {}
        override fun setBodyWeightText(text: String) {
            texts += text
        }

        override fun nudgeBodyWeight(steps: Int) {
            nudges += steps
        }

        override fun exportBackup() {
            exports++
        }

        override fun importBackup() {
            imports++
        }
    }

    private val actions = Recorder()
    private val sam = ProfileUiState(Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, true), age = 30, bodyWeightText = "72.5")

    @Test
    fun showsTheProfileAndPassesChangesOn() {
        rule.setContent { OverloadTheme { ProfileContent(sam, actions) } }
        rule.onNodeWithText("Sam").assertIsDisplayed()
        rule.onNodeWithText("30 years").assertIsDisplayed()
        rule.onNodeWithText("5 ft").assertIsDisplayed()
        rule.onNodeWithText("9 in").assertIsDisplayed()
        rule.onNodeWithTag("body-weight-field").performTextReplacement("73.1")
        assertEquals("73.1", actions.texts.last())
        rule.onNodeWithContentDescription("Increase body weight").performClick()
        assertEquals(listOf(1), actions.nudges)
    }

    @Test
    fun invalidBodyWeightShowsAHint() {
        rule.setContent { OverloadTheme { ProfileContent(sam.copy(bodyWeightText = "7", bodyWeightValid = false), actions) } }
        rule.onNodeWithText("Enter a weight from 20 to 400 kg").assertIsDisplayed()
    }

    @Test
    fun backupButtonsAndCredits() {
        rule.setContent { OverloadTheme { ProfileContent(sam, actions) } }
        rule.onNodeWithText("Export backup file").performScrollTo().performClick()
        rule.onNodeWithText("Import backup file").performScrollTo().performClick()
        assertEquals(1, actions.exports)
        assertEquals(1, actions.imports)
        rule.onNodeWithText("Exercise pictures: free-exercise-db (public domain)").assertExists()
    }

    @Test
    fun welcomeOffersSkipAndDone() {
        var skipped = false
        var done = false
        rule.setContent {
            OverloadTheme { WelcomeContent(ProfileUiState(), actions, onSkip = { skipped = true }, onDone = { done = true }) }
        }
        rule.onNodeWithText("Welcome to MuscleDot").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick()
        rule.onNodeWithText("Done").performClick()
        assertTrue(skipped && done)
    }
}

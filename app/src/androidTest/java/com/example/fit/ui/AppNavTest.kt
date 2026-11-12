package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fit.domain.Profile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class AppNavTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0))

    @Test
    fun firstLaunchOpensWelcome() {
        launchApp(clock).use { compose.onNodeWithTag("screen-welcome").assertIsDisplayed() }
    }

    @Test
    fun showsFiveTabsAndSwitchesBetweenThem() {
        launchApp(clock) { it.profile.saveProfile(Profile(welcomeDone = true)) }.use {
            compose.onNodeWithTag("screen-today").assertIsDisplayed()
            mapOf(
                "tab-today" to "Today",
                "tab-routines" to "Routines",
                "tab-progress" to "Progress",
                "tab-history" to "History",
                "tab-profile" to "Profile",
            ).forEach { (tag, label) -> compose.onNodeWithTag(tag).assertTextEquals(label) }
            compose.onNodeWithTag("tab-history").performClick()
            compose.onNodeWithTag("screen-history").assertIsDisplayed()
            compose.onNodeWithTag("tab-profile").performClick()
            compose.onNodeWithTag("screen-profile").assertIsDisplayed()
        }
    }
}

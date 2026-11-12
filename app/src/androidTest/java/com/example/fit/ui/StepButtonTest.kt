package com.example.fit.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.fit.ui.components.NumberStepper
import com.example.fit.ui.components.StepButton
import com.example.fit.ui.theme.OverloadTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StepButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tapStepsOnce() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(1, steps)
    }

    @Test
    fun holdingRepeatsThenSpeedsUp_andStopsOnRelease() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.mainClock.autoAdvance = false
        val button = rule.onNodeWithContentDescription("Increase weight")
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(350)
        assertEquals(1, steps)
        rule.mainClock.advanceTimeBy(1150)
        assertTrue("$steps", steps in 6..10) // slow repeats until 1.5 s
        rule.mainClock.advanceTimeBy(1000)
        assertTrue("$steps", steps in 22..32) // fast repeats after
        button.performTouchInput { up() }
        val atRelease = steps
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(atRelease, steps)
    }

    @Test
    fun numberStepperReportsDirection() {
        val seen = mutableListOf<Int>()
        rule.setContent {
            OverloadTheme {
                NumberStepper("40.0", "Decrease weight", "Increase weight", onStep = { seen += it }, unit = "kg")
            }
        }
        rule.onNodeWithText("40.0").assertIsDisplayed()
        rule.onNodeWithContentDescription("Decrease weight").performClick()
        rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(listOf(-1, +1), seen)
    }
}
